package com.example.slowsqlagent.database;

import com.example.slowsqlagent.domain.ExecutionPlan;
import com.example.slowsqlagent.domain.TableMetadata;
import com.example.slowsqlagent.domain.TableRef;

public interface DatabaseInspector {

    boolean supports(String databaseType);

    TableMetadata inspectTable(TableRef tableRef);

    ExecutionPlan explain(String sqlText, int timeoutSeconds);
}
