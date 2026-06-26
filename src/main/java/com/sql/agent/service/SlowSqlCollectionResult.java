package com.sql.agent.service;

import com.sql.agent.domain.SlowSqlRecord;

import java.util.List;

public record SlowSqlCollectionResult(List<SlowSqlRecord> records, int insertedCount, int updatedCount) {
}
