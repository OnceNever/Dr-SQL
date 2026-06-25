package com.example.slowsqlagent.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class SlowSqlRecord {

    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private String id;
    private String source;
    private String schemaName;
    private String sqlText;
    private long executionCount;
    private double avgLatencyMs;
    private double maxLatencyMs;
    private long rowsExamined;
    private long rowsSent;
    private Instant firstSeen;
    private Instant lastSeen;
    private Instant collectedAt;
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;
    private String analysisError;
    private Instant analysisStartedAt;
    private Instant analysisCompletedAt;
    private AnalysisReport analysisReport;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public long getExecutionCount() {
        return executionCount;
    }

    public void setExecutionCount(long executionCount) {
        this.executionCount = executionCount;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public double getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(double maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public long getRowsExamined() {
        return rowsExamined;
    }

    public void setRowsExamined(long rowsExamined) {
        this.rowsExamined = rowsExamined;
    }

    public long getRowsSent() {
        return rowsSent;
    }

    public void setRowsSent(long rowsSent) {
        this.rowsSent = rowsSent;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    @JsonIgnore
    public String getCollectedAtDisplay() {
        return collectedAt == null ? "-" : DISPLAY_TIME_FORMATTER.format(collectedAt);
    }

    public AnalysisReport getAnalysisReport() {
        return analysisReport;
    }

    public void setAnalysisReport(AnalysisReport analysisReport) {
        this.analysisReport = analysisReport;
    }

    public AnalysisStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(AnalysisStatus analysisStatus) {
        this.analysisStatus = analysisStatus == null ? AnalysisStatus.PENDING : analysisStatus;
    }

    public String getAnalysisError() {
        return analysisError;
    }

    public void setAnalysisError(String analysisError) {
        this.analysisError = analysisError;
    }

    public Instant getAnalysisStartedAt() {
        return analysisStartedAt;
    }

    public void setAnalysisStartedAt(Instant analysisStartedAt) {
        this.analysisStartedAt = analysisStartedAt;
    }

    public Instant getAnalysisCompletedAt() {
        return analysisCompletedAt;
    }

    public void setAnalysisCompletedAt(Instant analysisCompletedAt) {
        this.analysisCompletedAt = analysisCompletedAt;
    }

    public boolean hasAnalysis() {
        return analysisStatus == AnalysisStatus.COMPLETED && analysisReport != null;
    }

    public boolean isAnalysisInProgress() {
        return analysisStatus == AnalysisStatus.ANALYZING;
    }

    public boolean isAnalysisFailed() {
        return analysisStatus == AnalysisStatus.FAILED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SlowSqlRecord that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
