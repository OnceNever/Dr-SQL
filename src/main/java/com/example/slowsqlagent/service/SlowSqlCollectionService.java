package com.example.slowsqlagent.service;

import com.example.slowsqlagent.config.AgentProperties;
import com.example.slowsqlagent.database.SlowSqlCollector;
import com.example.slowsqlagent.domain.SlowSqlRecord;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<SlowSqlRecord> collect() {
        SlowSqlCollector collector = collectors.stream()
                .filter(candidate -> candidate.supports(
                        properties.getDatabase().getType(),
                        properties.getCollection().getSource()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No slow SQL collector for database="
                        + properties.getDatabase().getType()
                        + ", source=" + properties.getCollection().getSource()));
        List<SlowSqlRecord> records = collector.collect(properties.getCollection().getLimit());
        repository.saveAll(records);
        return records;
    }
}
