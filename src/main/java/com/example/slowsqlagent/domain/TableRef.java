package com.example.slowsqlagent.domain;

import java.util.Objects;

public class TableRef {

    private final String schemaName;
    private final String tableName;

    public TableRef(String schemaName, String tableName) {
        this.schemaName = schemaName;
        this.tableName = tableName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public String displayName() {
        if (schemaName == null || schemaName.isBlank()) {
            return tableName;
        }
        return schemaName + "." + tableName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TableRef tableRef)) {
            return false;
        }
        return Objects.equals(schemaName, tableRef.schemaName)
                && Objects.equals(tableName, tableRef.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaName, tableName);
    }
}
