package com.example.slowsqlagent.database.mysql;

import com.example.slowsqlagent.config.AgentProperties;
import com.example.slowsqlagent.config.TargetJdbcTemplateProvider;
import com.example.slowsqlagent.database.DatabaseInspector;
import com.example.slowsqlagent.domain.ExecutionPlan;
import com.example.slowsqlagent.domain.IndexMetadata;
import com.example.slowsqlagent.domain.TableMetadata;
import com.example.slowsqlagent.domain.TableRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Component
public class MySqlDatabaseInspector implements DatabaseInspector {

    private static final Logger log = LoggerFactory.getLogger(MySqlDatabaseInspector.class);

    private final AgentProperties properties;
    private final TargetJdbcTemplateProvider jdbcTemplateProvider;

    public MySqlDatabaseInspector(AgentProperties properties, TargetJdbcTemplateProvider jdbcTemplateProvider) {
        this.properties = properties;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean supports(String databaseType) {
        return "mysql".equalsIgnoreCase(databaseType);
    }

    @Override
    public TableMetadata inspectTable(TableRef tableRef) {
        TableMetadata metadata = new TableMetadata();
        metadata.setTableRef(resolveSchema(tableRef));
        try {
            JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getJdbcTemplate()
                    .orElseThrow(() -> new IllegalStateException("AGENT_DB_URL is not configured."));
            metadata.setCreateTableSql(readCreateTable(jdbcTemplate, metadata.getTableRef()));
            readTableStats(jdbcTemplate, metadata);
            metadata.getIndexes().addAll(readIndexes(jdbcTemplate, metadata.getTableRef()));
        } catch (Exception ex) {
            metadata.setError(ex.getMessage());
        }
        return metadata;
    }

    @Override
    public ExecutionPlan explain(String sqlText, int timeoutSeconds) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setFormat("JSON");
        if (!isExplainSafe(sqlText)) {
            plan.setError("Only SELECT statements are explained in this MVP.");
            return plan;
        }
        String explainTargetSql = normalizeDigestSqlForExplain(sqlText);
        String explainSql = "EXPLAIN FORMAT=JSON " + explainTargetSql;
        int parameterCount = countParameterMarkers(explainTargetSql);
        try {
            JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getJdbcTemplate()
                    .orElseThrow(() -> new IllegalStateException("AGENT_DB_URL is not configured."));
            jdbcTemplate.setQueryTimeout(timeoutSeconds);
            String content = jdbcTemplate.execute((Connection connection) -> {
                log.info("Running MySQL EXPLAIN. parameterCount={}, sql={}", parameterCount, explainSql);
                try (PreparedStatement statement = connection.prepareStatement(explainSql)) {
                    statement.setQueryTimeout(timeoutSeconds);
                    bindExplainPlaceholders(statement, parameterCount);
                    try (var rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString(1);
                        }
                    }
                    return "";
                }
            });
            plan.setContent(content);
        } catch (Exception ex) {
            Throwable rootCause = rootCause(ex);
            log.warn("MySQL EXPLAIN failed. parameterCount={}, sql={}, error={}",
                    parameterCount, explainSql, rootCause.getMessage(), ex);
            plan.setError("EXPLAIN failed: " + rootCause.getMessage());
        }
        return plan;
    }

    private String normalizeDigestSqlForExplain(String sqlText) {
        String normalized = sqlText.strip();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // MySQL performance_schema digest may render built-in functions as "COUNT (?)".
        // MySQL can then resolve it as a schema function instead of the native function.
        normalized = normalized.replaceAll("(?i)\\b(COUNT|SUM|AVG|MIN|MAX)\\s+\\(", "$1(");
        normalized = normalized.replaceAll("(?i)\\b(COALESCE|IFNULL|DATE_FORMAT|CONCAT|SUBSTRING|LOWER|UPPER)\\s+\\(", "$1(");

        // COUNT(?) in a digest often represents COUNT(*) or COUNT(constant). Use COUNT(*)
        // for EXPLAIN because it avoids an artificial parameter in the select list.
        normalized = normalized.replaceAll("(?i)\\bCOUNT\\(\\s*\\?\\s*\\)", "COUNT(*)");
        return normalized;
    }

    private void bindExplainPlaceholders(PreparedStatement statement, int parameterCount) throws SQLException {
        for (int i = 1; i <= parameterCount; i++) {
            statement.setObject(i, 0);
        }
    }

    private int countParameterMarkers(String sqlText) {
        int count = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean backtickQuoted = false;
        boolean escaped = false;
        for (int i = 0; i < sqlText.length(); i++) {
            char current = sqlText.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (!doubleQuoted && !backtickQuoted && current == '\'') {
                singleQuoted = !singleQuoted;
                continue;
            }
            if (!singleQuoted && !backtickQuoted && current == '"') {
                doubleQuoted = !doubleQuoted;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && current == '`') {
                backtickQuoted = !backtickQuoted;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && !backtickQuoted && current == '?') {
                count++;
            }
        }
        return count;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private TableRef resolveSchema(TableRef tableRef) {
        if (StringUtils.hasText(tableRef.getSchemaName())) {
            return tableRef;
        }
        if (StringUtils.hasText(properties.getDatabase().getDefaultSchema())) {
            return new TableRef(properties.getDatabase().getDefaultSchema(), tableRef.getTableName());
        }
        return tableRef;
    }

    private String readCreateTable(JdbcTemplate jdbcTemplate, TableRef tableRef) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SHOW CREATE TABLE " + qualifiedName(tableRef));
        Object createTable = row.get("Create Table");
        if (createTable == null) {
            createTable = row.values().stream().reduce((first, second) -> second).orElse("");
        }
        return String.valueOf(createTable);
    }

    private void readTableStats(JdbcTemplate jdbcTemplate, TableMetadata metadata) {
        TableRef tableRef = metadata.getTableRef();
        if (!StringUtils.hasText(tableRef.getSchemaName())) {
            return;
        }
        jdbcTemplate.query("""
                SELECT TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                """, rs -> {
            if (rs.next()) {
                metadata.setEstimatedRows(rs.getLong("TABLE_ROWS"));
                metadata.setDataLengthBytes(rs.getLong("DATA_LENGTH"));
                metadata.setIndexLengthBytes(rs.getLong("INDEX_LENGTH"));
            }
            return null;
        }, tableRef.getSchemaName(), tableRef.getTableName());
    }

    private List<IndexMetadata> readIndexes(JdbcTemplate jdbcTemplate, TableRef tableRef) {
        return jdbcTemplate.query("SHOW INDEX FROM " + qualifiedName(tableRef), (rs, rowNum) -> {
            IndexMetadata index = new IndexMetadata();
            index.setIndexName(rs.getString("Key_name"));
            index.setUnique(rs.getInt("Non_unique") == 0);
            index.setSequence(rs.getInt("Seq_in_index"));
            index.setColumnName(rs.getString("Column_name"));
            long cardinality = rs.getLong("Cardinality");
            index.setCardinality(rs.wasNull() ? null : cardinality);
            index.setIndexType(rs.getString("Index_type"));
            return index;
        });
    }

    private String qualifiedName(TableRef tableRef) {
        if (StringUtils.hasText(tableRef.getSchemaName())) {
            return quoteIdentifier(tableRef.getSchemaName()) + "." + quoteIdentifier(tableRef.getTableName());
        }
        return quoteIdentifier(tableRef.getTableName());
    }

    private String quoteIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier) || !identifier.matches("[A-Za-z0-9_$]+")) {
            throw new IllegalArgumentException("Unsafe identifier: " + identifier);
        }
        return "`" + identifier + "`";
    }

    private boolean isExplainSafe(String sqlText) {
        if (!StringUtils.hasText(sqlText)) {
            return false;
        }
        String trimmed = sqlText.stripLeading().toLowerCase();
        return trimmed.startsWith("select") || trimmed.startsWith("with");
    }
}
