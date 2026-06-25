# Dr.SQL

Dr.SQL（SQL医生）是一个基于 Spring Boot + Spring AI 的慢 SQL 诊断工具。当前阶段专注于采集、分析和建议，不会自动执行任何数据库优化变更。

English documentation is available below.

## 中文说明

### 功能

- 从 MySQL `performance_schema` 或 `mysql.slow_log` 表采集慢 SQL。
- 解析 SQL 涉及的表。
- 获取 `SHOW CREATE TABLE`、索引、表行数和空间大小。
- 对 `SELECT` / `WITH` 查询执行 `EXPLAIN FORMAT=JSON`。
- 通过 Spring AI 生成中文优化建议，启动前必须配置 OpenAI 参数。
- 使用本地 JSON 轻量保存慢 SQL 和分析结果。
- 提供 Web 页面和 REST API。

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 5.7+/8.0+

请在 IDE 或命令行中使用 JDK 17 运行项目。

### 启动

推荐直接运行主程序：

```text
com.example.slowsqlagent.SlowSqlAgentApplication
```

IDE 配置：

```text
JDK: D:\java
Working directory: E:\slow-sql-agent
Main class: com.example.slowsqlagent.SlowSqlAgentApplication
```

也可以用 Maven 启动：

```powershell
cd E:\slow-sql-agent
mvn spring-boot:run
```

打开：

```text
http://localhost:8080
```

启动前需要先配置 `config/application.yml`，否则应用会在启动阶段提示缺失配置。

### 统一配置

运行配置集中在项目根目录：

```text
config/application.yml
```

OpenAI、MySQL 连接、采集策略都在这个文件里维护。Spring Boot 从项目根目录启动时会自动读取这个外部配置文件，并覆盖 `src/main/resources/application.yml` 里的默认值。

慢 SQL 和分析结果会轻量保存到：

```text
data/slow-sql-records.json
```

这个文件用于本地恢复页面数据，重启项目后仍可查看历史分析结果，也可以对同一条 SQL 重新分析。

### 配置目标 MySQL

```yaml
agent:
  database:
    url: "jdbc:mysql://127.0.0.1:3306/shop?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    username: "readonly_user"
    password: "password"
    default-schema: "shop"
  collection:
    source: performance_schema
```

也可以改为读取慢日志表：

```yaml
agent:
  collection:
    source: slow_log_table
```

如果要定时自动采集：

```yaml
agent:
  collection:
    enabled: true
    fixed-delay-ms: 300000
```

### 配置 Spring AI

应用启动要求配置 OpenAI 参数：

```yaml
spring:
  ai:
    openai:
      api-key: "你的 API Key"
      base-url: "https://api.openai.com"
      chat:
        options:
          model: "gpt-4o-mini"
```

如果使用兼容 OpenAI 协议的服务：

```yaml
spring:
  ai:
    openai:
      base-url: "https://your-compatible-endpoint"
```

### MySQL 权限建议

第一阶段建议使用只读诊断账号，至少需要：

- 读取目标业务库表结构和索引。
- 读取 `information_schema.TABLES`。
- 使用 `EXPLAIN`。
- 如果使用 `performance_schema` 采集，需要读取 `performance_schema.events_statements_summary_by_digest`。
- 如果使用 `slow_log_table`，需要读取 `mysql.slow_log`。

### API

```text
GET  /api/slow-sql
POST /api/collect
POST /api/sample
GET  /api/slow-sql/{id}
POST /api/slow-sql/{id}/analyze
```

### 后续接入点

新增数据库类型时实现：

- `SlowSqlCollector`
- `DatabaseInspector`

然后通过 `supports(databaseType, source)` 暴露给编排服务即可。

## English

Dr.SQL is a Spring Boot + Spring AI powered slow SQL diagnostic tool. The current stage focuses on collection, diagnosis, and recommendations. It does not automatically execute any database optimization changes.

### Features

- Collect slow SQL from MySQL `performance_schema` or the `mysql.slow_log` table.
- Parse related tables from SQL statements.
- Inspect `SHOW CREATE TABLE`, indexes, estimated rows, and table size.
- Run `EXPLAIN FORMAT=JSON` for `SELECT` / `WITH` queries.
- Generate optimization advice with Spring AI. OpenAI configuration is required before startup.
- Persist collected SQL and analysis reports in a lightweight local JSON file.
- Provide both a Web console and REST APIs.

### Requirements

- JDK 17+
- Maven 3.6+
- MySQL 5.7+/8.0+

Run the project with JDK 17 in your IDE or terminal.

### Run

Recommended main class:

```text
com.example.slowsqlagent.SlowSqlAgentApplication
```

IDE settings:

```text
JDK: D:\java
Working directory: E:\slow-sql-agent
Main class: com.example.slowsqlagent.SlowSqlAgentApplication
```

Or start with Maven:

```powershell
cd E:\slow-sql-agent
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

Update `config/application.yml` before startup. The application validates required OpenAI and database settings during startup.

### Configuration

Runtime configuration is managed in:

```text
config/application.yml
```

OpenAI settings, MySQL connection details, and collection options are maintained in this file. Spring Boot automatically reads it when the working directory is the project root.

Collected SQL records and analysis reports are saved to:

```text
data/slow-sql-records.json
```

This file restores page data after application restarts and allows the same SQL to be analyzed again.

### MySQL Configuration

```yaml
agent:
  database:
    url: "jdbc:mysql://127.0.0.1:3306/shop?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    username: "readonly_user"
    password: "password"
    default-schema: "shop"
  collection:
    source: performance_schema
```

To collect from the slow log table:

```yaml
agent:
  collection:
    source: slow_log_table
```

To enable scheduled collection:

```yaml
agent:
  collection:
    enabled: true
    fixed-delay-ms: 300000
```

### Spring AI Configuration

OpenAI settings are required:

```yaml
spring:
  ai:
    openai:
      api-key: "your API key"
      base-url: "https://api.openai.com"
      chat:
        options:
          model: "gpt-4o-mini"
```

For OpenAI-compatible endpoints:

```yaml
spring:
  ai:
    openai:
      base-url: "https://your-compatible-endpoint"
```

### Recommended MySQL Privileges

Use a read-only diagnostic account in the first stage. It should be able to:

- Read target table structures and indexes.
- Read `information_schema.TABLES`.
- Run `EXPLAIN`.
- Read `performance_schema.events_statements_summary_by_digest` when using `performance_schema`.
- Read `mysql.slow_log` when using `slow_log_table`.

### API

```text
GET  /api/slow-sql
POST /api/collect
POST /api/sample
GET  /api/slow-sql/{id}
POST /api/slow-sql/{id}/analyze
```

### Extension Points

To support another database type, implement:

- `SlowSqlCollector`
- `DatabaseInspector`

Then expose the implementation through `supports(databaseType, source)`.

## License

Dr.SQL is released under the MIT License. See [LICENSE](LICENSE).
