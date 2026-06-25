package com.example.slowsqlagent.database;

import com.example.slowsqlagent.domain.SlowSqlRecord;

import java.util.List;

public interface SlowSqlCollector {

    boolean supports(String databaseType, String source);

    List<SlowSqlRecord> collect(int limit);
}
