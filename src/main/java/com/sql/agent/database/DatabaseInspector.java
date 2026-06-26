package com.sql.agent.database;

import com.sql.agent.domain.ExecutionPlan;
import com.sql.agent.domain.TableMetadata;
import com.sql.agent.domain.TableRef;

public interface DatabaseInspector {

    boolean supports(String databaseType);

    TableMetadata inspectTable(TableRef tableRef);

    ExecutionPlan explain(String sqlText, int timeoutSeconds);
}
