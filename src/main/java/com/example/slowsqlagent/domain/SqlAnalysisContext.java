package com.example.slowsqlagent.domain;

import java.util.ArrayList;
import java.util.List;

public class SqlAnalysisContext {

    private SlowSqlRecord slowSqlRecord;
    private ExecutionPlan executionPlan;
    private final List<TableRef> tableRefs = new ArrayList<>();
    private final List<TableMetadata> tableMetadata = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public SlowSqlRecord getSlowSqlRecord() {
        return slowSqlRecord;
    }

    public void setSlowSqlRecord(SlowSqlRecord slowSqlRecord) {
        this.slowSqlRecord = slowSqlRecord;
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

    public List<String> getWarnings() {
        return warnings;
    }
}
