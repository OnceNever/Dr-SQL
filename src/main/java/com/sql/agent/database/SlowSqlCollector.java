package com.sql.agent.database;

import com.sql.agent.domain.SlowSqlRecord;

import java.util.List;

public interface SlowSqlCollector {

    boolean supports(String databaseType, String source);

    List<SlowSqlRecord> collect(int limit);
}
