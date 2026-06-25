package com.example.slowsqlagent.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Optional;

@Component
public class TargetJdbcTemplateProvider {

    private final AgentProperties properties;
    private volatile JdbcTemplate jdbcTemplate;

    public TargetJdbcTemplateProvider(AgentProperties properties) {
        this.properties = properties;
    }

    public Optional<JdbcTemplate> getJdbcTemplate() {
        if (!StringUtils.hasText(properties.getDatabase().getUrl())) {
            return Optional.empty();
        }
        JdbcTemplate existing = jdbcTemplate;
        if (existing != null) {
            return Optional.of(existing);
        }
        synchronized (this) {
            if (jdbcTemplate == null) {
                jdbcTemplate = new JdbcTemplate(createDataSource());
            }
            return Optional.of(jdbcTemplate);
        }
    }

    private DataSource createDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.getDatabase().getDriverClassName());
        dataSource.setUrl(properties.getDatabase().getUrl());
        dataSource.setUsername(properties.getDatabase().getUsername());
        dataSource.setPassword(properties.getDatabase().getPassword());
        return dataSource;
    }
}
