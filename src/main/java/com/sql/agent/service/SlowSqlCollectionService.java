package com.sql.agent.service;

import com.sql.agent.config.AgentProperties;
import com.sql.agent.database.SlowSqlCollector;
import com.sql.agent.domain.SlowSqlRecord;
import com.sql.agent.util.SlowSqlKeys;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlowSqlCollectionService {

    private final AgentProperties properties;
    private final List<SlowSqlCollector> collectors;
    private final SlowSqlRepository repository;

    public SlowSqlCollectionService(AgentProperties properties,
                                    List<SlowSqlCollector> collectors,
                                    SlowSqlRepository repository) {
        this.properties = properties;
        this.collectors = collectors;
        this.repository = repository;
    }

    public SlowSqlCollectionResult collect() {
        Map<String, SlowSqlRecord> deduplicated = new LinkedHashMap<>();
        for (String source : properties.getCollection().getSources()) {
            SlowSqlCollector collector = collectors.stream()
                    .filter(candidate -> candidate.supports(properties.getDatabase().getType(), source))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No slow SQL collector for database="
                            + properties.getDatabase().getType()
                            + ", source=" + source));
            for (SlowSqlRecord record : collector.collect(properties.getCollection().getLimit())) {
                deduplicated.merge(deduplicateKey(record), record, this::preferRecord);
            }
        }
        List<SlowSqlRecord> records = List.copyOf(deduplicated.values());
        SlowSqlRepository.SaveStats saveStats = repository.saveAll(records);
        return new SlowSqlCollectionResult(records, saveStats.insertedCount(), saveStats.updatedCount());
    }

    private SlowSqlRecord preferRecord(SlowSqlRecord existing, SlowSqlRecord incoming) {
        if (isSlowLog(incoming) && !isSlowLog(existing)) {
            return incoming;
        }
        return existing;
    }

    private boolean isSlowLog(SlowSqlRecord record) {
        return record.getSource() != null
                && ("slow_log".equalsIgnoreCase(record.getSource())
                || "slow_log_table".equalsIgnoreCase(record.getSource()));
    }

    private String deduplicateKey(SlowSqlRecord record) {
        return SlowSqlKeys.deduplicateKey(record);
    }
}
