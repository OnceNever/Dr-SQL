package com.sql.agent.service;

import com.sql.agent.domain.SlowSqlRecord;
import com.sql.agent.util.HashIds;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SampleDataService {

    private final SlowSqlRepository repository;

    public SampleDataService(SlowSqlRepository repository) {
        this.repository = repository;
    }

    public SlowSqlRecord loadSample() {
        SlowSqlRecord record = new SlowSqlRecord();
        record.setSource("sample");
        record.setSchemaName("shop");
        record.setSqlText("""
                SELECT o.id, o.order_no, o.pay_time, u.nickname
                FROM orders o
                JOIN users u ON u.id = o.user_id
                WHERE o.status = 'PAID'
                  AND o.pay_time >= '2026-01-01 00:00:00'
                ORDER BY o.pay_time DESC
                LIMIT 20
                """);
        record.setExecutionCount(320);
        record.setAvgLatencyMs(1840);
        record.setMaxLatencyMs(6210);
        record.setRowsExamined(18300000);
        record.setRowsSent(6400);
        record.setFirstSeen(Instant.now().minusSeconds(86400));
        record.setLastSeen(Instant.now());
        record.setCollectedAt(Instant.now());
        record.setId(HashIds.sha256Hex(record.getSource() + "|" + record.getSchemaName() + "|" + record.getSqlText()));
        repository.save(record);
        return record;
    }
}
