package com.sql.agent.database.mysql;

import com.sql.agent.config.AgentProperties;
import com.sql.agent.config.TargetJdbcTemplateProvider;
import com.sql.agent.database.SlowSqlCollector;
import com.sql.agent.domain.SlowSqlRecord;
import com.sql.agent.util.HashIds;
import com.sql.agent.util.SlowSqlKeys;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
public class MySqlSlowLogTableCollector implements SlowSqlCollector {

    private static final String SQL = """
            SELECT
              db,
              sql_text,
              COUNT(*) AS execution_count,
              AVG(TIME_TO_SEC(query_time)) * 1000 AS avg_latency_ms,
              MAX(TIME_TO_SEC(query_time)) * 1000 AS max_latency_ms,
              SUM(rows_sent) AS rows_sent,
              SUM(rows_examined) AS rows_examined,
              MIN(start_time) AS first_seen,
              MAX(start_time) AS last_seen
            FROM mysql.slow_log
            WHERE query_time >= SEC_TO_TIME(? / 1000)
              AND sql_text NOT LIKE '%performance_schema.events_statements_summary_by_digest%'
              AND sql_text NOT LIKE '%mysql.slow_log%'
            GROUP BY db, sql_text
            ORDER BY last_seen DESC
            LIMIT ?
            """;

    private final AgentProperties properties;
    private final TargetJdbcTemplateProvider jdbcTemplateProvider;

    public MySqlSlowLogTableCollector(AgentProperties properties, TargetJdbcTemplateProvider jdbcTemplateProvider) {
        this.properties = properties;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean supports(String databaseType, String source) {
        return "mysql".equalsIgnoreCase(databaseType)
                && ("slow_log".equalsIgnoreCase(source)
                || "slow_log_table".equalsIgnoreCase(source));
    }

    @Override
    public List<SlowSqlRecord> collect(int limit) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getJdbcTemplate()
                .orElseThrow(() -> new IllegalStateException("AGENT_DB_URL is not configured."));
        return jdbcTemplate.query(SQL, (rs, rowNum) -> {
            SlowSqlRecord record = new SlowSqlRecord();
            record.setSource("slow_log");
            record.setSchemaName(rs.getString("db"));
            record.setSqlText(SlowSqlKeys.stripLeadingComments(rs.getString("sql_text")));
            record.setExecutionCount(rs.getLong("execution_count"));
            record.setAvgLatencyMs(rs.getDouble("avg_latency_ms"));
            record.setMaxLatencyMs(rs.getDouble("max_latency_ms"));
            record.setRowsExamined(rs.getLong("rows_examined"));
            record.setRowsSent(rs.getLong("rows_sent"));
            record.setFirstSeen(toInstant(rs.getTimestamp("first_seen")));
            record.setLastSeen(toInstant(rs.getTimestamp("last_seen")));
            record.setCollectedAt(record.getLastSeen() == null ? record.getFirstSeen() : record.getLastSeen());
            record.setId(HashIds.sha256Hex(String.join("|",
                    record.getSource(),
                    normalize(record.getSchemaName()),
                    normalize(record.getSqlText()))));
            return record;
        }, properties.getCollection().getSlowQueryThresholdMs(), limit);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

}
