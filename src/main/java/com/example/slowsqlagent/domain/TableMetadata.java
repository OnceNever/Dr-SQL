package com.example.slowsqlagent.domain;

import java.util.ArrayList;
import java.util.List;

public class TableMetadata {

    private TableRef tableRef;
    private String createTableSql;
    private Long estimatedRows;
    private Long dataLengthBytes;
    private Long indexLengthBytes;
    private final List<IndexMetadata> indexes = new ArrayList<>();
    private String error;

    public TableRef getTableRef() {
        return tableRef;
    }

    public void setTableRef(TableRef tableRef) {
        this.tableRef = tableRef;
    }

    public String getCreateTableSql() {
        return createTableSql;
    }

    public void setCreateTableSql(String createTableSql) {
        this.createTableSql = createTableSql;
    }

    public Long getEstimatedRows() {
        return estimatedRows;
    }

    public void setEstimatedRows(Long estimatedRows) {
        this.estimatedRows = estimatedRows;
    }

    public Long getDataLengthBytes() {
        return dataLengthBytes;
    }

    public void setDataLengthBytes(Long dataLengthBytes) {
        this.dataLengthBytes = dataLengthBytes;
    }

    public Long getIndexLengthBytes() {
        return indexLengthBytes;
    }

    public void setIndexLengthBytes(Long indexLengthBytes) {
        this.indexLengthBytes = indexLengthBytes;
    }

    public List<IndexMetadata> getIndexes() {
        return indexes;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
