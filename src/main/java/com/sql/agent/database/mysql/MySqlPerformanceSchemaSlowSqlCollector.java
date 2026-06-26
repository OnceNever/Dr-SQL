package com.sql.agent.database.mysql;

import com.sql.agent.config.AgentProperties;
import com.sql.agent.config.TargetJdbcTemplateProvider;
import com.sql.agent.database.SlowSqlCollector;
import com.sql.agent.domain.SlowSqlRecord;
import com.sql.agent.util.HashIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
public class MySqlPerformanceSchemaSlowSqlCollector implements SlowSqlCollector {

    private static final String SQL = """
            SELECT
              SCHEMA_NAME,
              DIGEST_TEXT,
              COUNT_STAR,
              AVG_TIMER_WAIT / 1000000000 AS AVG_LATENCY_MS,
              MAX_TIMER_WAIT / 1000000000 AS MAX_LATENCY_MS,
              SUM_ROWS_EXAMINED,
              SUM_ROWS_SENT,
              FIRST_SEEN,
              LAST_SEEN
            FROM performance_schema.events_statements_summary_by_digest
            WHERE DIGEST_TEXT IS NOT NULL
              AND SCHEMA_NAME IS NOT NULL
              AND AVG_TIMER_WAIT >= ?
              AND DIGEST_TEXT NOT LIKE '%performance_schema.events_statements_summary_by_digest%'
              AND DIGEST_TEXT NOT LIKE '%mysql.slow_log%'
            ORDER BY AVG_TIMER_WAIT DESC
            LIMIT ?
            """;

    private final AgentProperties properties;
    private final TargetJdbcTemplateProvider jdbcTemplateProvider;

    public MySqlPerformanceSchemaSlowSqlCollector(AgentProperties properties,
                                                 TargetJdbcTemplateProvider jdbcTemplateProvider) {
        this.properties = properties;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean supports(String databaseType, String source) {
        return "mysql".equalsIgnoreCase(databaseType)
                && "performance_schema".equalsIgnoreCase(source);
    }

    @Override
    public List<SlowSqlRecord> collect(int limit) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getJdbcTemplate()
                .orElseThrow(() -> new IllegalStateException("AGENT_DB_URL is not configured."));
        long thresholdPicoseconds = properties.getCollection().getSlowQueryThresholdMs() * 1_000_000_000L;

        return jdbcTemplate.query(SQL, (rs, rowNum) -> {
            SlowSqlRecord record = new SlowSqlRecord();
            record.setSource("performance_schema");
            record.setSchemaName(rs.getString("SCHEMA_NAME"));
            record.setSqlText(rs.getString("DIGEST_TEXT"));
            record.setExecutionCount(rs.getLong("COUNT_STAR"));
            record.setAvgLatencyMs(rs.getDouble("AVG_LATENCY_MS"));
            record.setMaxLatencyMs(rs.getDouble("MAX_LATENCY_MS"));
            record.setRowsExamined(rs.getLong("SUM_ROWS_EXAMINED"));
            record.setRowsSent(rs.getLong("SUM_ROWS_SENT"));
            record.setFirstSeen(toInstant(rs.getTimestamp("FIRST_SEEN")));
            record.setLastSeen(toInstant(rs.getTimestamp("LAST_SEEN")));
            record.setCollectedAt(record.getLastSeen() == null ? record.getFirstSeen() : record.getLastSeen());
            record.setId(HashIds.sha256Hex(String.join("|",
                    record.getSource(),
                    normalize(record.getSchemaName()),
                    normalize(record.getSqlText()))));
            return record;
        }, thresholdPicoseconds, limit);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
