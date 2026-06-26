# MySQL Slow SQL Lab

This folder contains MySQL scripts for building a repeatable slow SQL test database.

## Files

- `00_setup_mysql_slow_sql_lab.sql`: creates `dr_sql_slow_test`, creates tables, and inserts synthetic data.
- `01_slow_sql_cases.sql`: slow SQL cases covering full scans, index invalidation, OR, leading-wildcard LIKE, filesort, subqueries, deep pagination, large result sets, grouping, joins, and non-sargable date expressions.
- `02_enable_slow_query_log.sql`: optional slow query log configuration. It requires admin privileges.

## Quick Start

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p < sql/mysql/02_enable_slow_query_log.sql
mysql -h 127.0.0.1 -P 3306 -u root -p < sql/mysql/00_setup_mysql_slow_sql_lab.sql
mysql -h 127.0.0.1 -P 3306 -u root -p dr_sql_slow_test < sql/mysql/01_slow_sql_cases.sql
```

The default data volume is:

- `user_info`: 1,000,000 rows
- `orders`: 10,000,000 rows

For a smaller local smoke test, edit the top of `00_setup_mysql_slow_sql_lab.sql`:

```sql
SET @user_rows := 1000000;
SET @order_rows := 10000000;
```

The schema intentionally omits indexes on fields such as `province`, `amount`, `bio`, `note`, and `product_name`, so the slow SQL agent has realistic optimization opportunities to discover.
