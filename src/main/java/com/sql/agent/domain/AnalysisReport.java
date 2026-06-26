package com.sql.agent.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AnalysisReport {

    private Instant analyzedAt = Instant.now();
    private String summary;
    private String rootCause;
    private String confidence;
    private ExecutionPlan executionPlan;
    private final List<TableRef> tableRefs = new ArrayList<>();
    private final List<TableMetadata> tableMetadata = new ArrayList<>();
    private final List<OptimizationAdvice> recommendations = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(Instant analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public ExecutionPlan getExecutionPlan() {
        return executionPlan;
    }

    public void setExecutionPlan(ExecutionPlan executionPlan) {
        this.executionPlan = executionPlan;
    }

    public List<TableRef> getTableRefs() {
        return tableRefs;
    }

    public List<TableMetadata> getTableMetadata() {
        return tableMetadata;
    }

    public List<OptimizationAdvice> getRecommendations() {
        return recommendations;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
