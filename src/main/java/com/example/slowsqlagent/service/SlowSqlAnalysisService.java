package com.example.slowsqlagent.service;

import com.example.slowsqlagent.config.AgentProperties;
import com.example.slowsqlagent.database.DatabaseInspector;
import com.example.slowsqlagent.domain.AnalysisReport;
import com.example.slowsqlagent.domain.AnalysisStatus;
import com.example.slowsqlagent.domain.ExecutionPlan;
import com.example.slowsqlagent.domain.SlowSqlRecord;
import com.example.slowsqlagent.domain.SqlAnalysisContext;
import com.example.slowsqlagent.domain.TableRef;
import com.example.slowsqlagent.parser.SqlTableExtractor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class SlowSqlAnalysisService {

    private final AgentProperties properties;
    private final List<DatabaseInspector> inspectors;
    private final SqlTableExtractor tableExtractor;
    private final OptimizationAdvisorService advisorService;
    private final SlowSqlRepository repository;
    private final Executor analysisExecutor;

    public SlowSqlAnalysisService(AgentProperties properties,
                                  List<DatabaseInspector> inspectors,
                                  SqlTableExtractor tableExtractor,
                                  OptimizationAdvisorService advisorService,
                                  SlowSqlRepository repository,
                                  @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.properties = properties;
        this.inspectors = inspectors;
        this.tableExtractor = tableExtractor;
        this.advisorService = advisorService;
        this.repository = repository;
        this.analysisExecutor = analysisExecutor;
    }

    public boolean startAnalysis(String id) {
        SlowSqlRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Slow SQL not found: " + id));
        synchronized (record) {
            if (record.isAnalysisInProgress()) {
                return false;
            }
            record.setAnalysisStatus(AnalysisStatus.ANALYZING);
            record.setAnalysisError(null);
            record.setAnalysisReport(null);
            record.setAnalysisStartedAt(Instant.now());
            record.setAnalysisCompletedAt(null);
            repository.save(record);
        }
        analysisExecutor.execute(() -> analyzeInBackground(id));
        return true;
    }

    public AnalysisReport analyze(String id) {
        SlowSqlRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Slow SQL not found: " + id));
        return analyzeRecord(record);
    }

    private void analyzeInBackground(String id) {
        SlowSqlRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Slow SQL not found: " + id));
        try {
            AnalysisReport report = analyzeRecord(record);
            synchronized (record) {
                record.setAnalysisReport(report);
                record.setAnalysisStatus(AnalysisStatus.COMPLETED);
                record.setAnalysisError(null);
                record.setAnalysisCompletedAt(Instant.now());
                repository.save(record);
            }
        } catch (Exception ex) {
            synchronized (record) {
                record.setAnalysisStatus(AnalysisStatus.FAILED);
                record.setAnalysisError(rootCauseMessage(ex));
                record.setAnalysisCompletedAt(Instant.now());
                repository.save(record);
            }
        }
    }

    private AnalysisReport analyzeRecord(SlowSqlRecord record) {
        SqlAnalysisContext context = buildContext(record);
        return advisorService.advise(context);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private SqlAnalysisContext buildContext(SlowSqlRecord record) {
        DatabaseInspector inspector = inspectors.stream()
                .filter(candidate -> candidate.supports(properties.getDatabase().getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No inspector for database="
                        + properties.getDatabase().getType()));

        SqlAnalysisContext context = new SqlAnalysisContext();
        context.setSlowSqlRecord(record);
        List<TableRef> refs = tableExtractor.extract(record.getSqlText(), record.getSchemaName());
        context.getTableRefs().addAll(refs);
        if (refs.isEmpty()) {
            context.getWarnings().add("未能从 SQL 中解析出表名，可能是语法不完整或采集到的是归一化摘要。");
        }
        for (TableRef ref : refs) {
            context.getTableMetadata().add(inspector.inspectTable(ref));
        }
        ExecutionPlan plan = new ExecutionPlan();
        if (properties.getAnalysis().isRunExplain()) {
            plan = inspector.explain(record.getSqlText(), properties.getAnalysis().getExplainTimeoutSeconds());
        } else {
            plan.setError("EXPLAIN is disabled by configuration.");
        }
        context.setExecutionPlan(plan);
        return context;
    }
}
