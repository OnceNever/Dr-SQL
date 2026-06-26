package com.sql.agent.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private final Database database = new Database();
    private final Collection collection = new Collection();
    private final Analysis analysis = new Analysis();
    private final Storage storage = new Storage();

    public Database getDatabase() {
        return database;
    }

    public Collection getCollection() {
        return collection;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public Storage getStorage() {
        return storage;
    }

    public static class Database {
        private String type = "mysql";
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private String defaultSchema;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getDefaultSchema() {
            return defaultSchema;
        }

        public void setDefaultSchema(String defaultSchema) {
            this.defaultSchema = defaultSchema;
        }
    }

    public static class Collection {
        private boolean enabled;
        private List<String> sources = new ArrayList<>(List.of("performance_schema"));

        @Min(1)
        private long slowQueryThresholdMs = 1000;

        @Min(1)
        private int limit = 50;

        @Min(1000)
        private long fixedDelayMs = 300000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getSources() {
            if (sources == null || sources.isEmpty()) {
                return List.of("performance_schema");
            }
            return sources;
        }

        public void setSources(List<String> sources) {
            this.sources = sources == null || sources.isEmpty()
                    ? new ArrayList<>(List.of("performance_schema"))
                    : sources;
        }

        public long getSlowQueryThresholdMs() {
            return slowQueryThresholdMs;
        }

        public void setSlowQueryThresholdMs(long slowQueryThresholdMs) {
            this.slowQueryThresholdMs = slowQueryThresholdMs;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }
    }

    public static class Analysis {
        private boolean runExplain = true;

        @Min(1)
        private int explainTimeoutSeconds = 10;

        public boolean isRunExplain() {
            return runExplain;
        }

        public void setRunExplain(boolean runExplain) {
            this.runExplain = runExplain;
        }

        public int getExplainTimeoutSeconds() {
            return explainTimeoutSeconds;
        }

        public void setExplainTimeoutSeconds(int explainTimeoutSeconds) {
            this.explainTimeoutSeconds = explainTimeoutSeconds;
        }
    }

    public static class Storage {
        private String file = "data/slow-sql-records.json";

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }
}
