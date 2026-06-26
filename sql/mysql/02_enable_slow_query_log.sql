-- Optional MySQL slow query log setup.
-- Requires SYSTEM_VARIABLES_ADMIN or SUPER privileges.
--
-- Table output is convenient for this project when using mysql.slow_log collection:
--   agent.collection.sources: [slow_log, performance_schema]
--
-- File output also works for manual inspection, but this agent reads MySQL tables
-- or performance_schema depending on configuration.

SET GLOBAL log_output = 'TABLE';
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0.5;
SET GLOBAL min_examined_row_limit = 0;

-- Optional during focused testing. This logs queries that do not use an index.
-- It can be noisy, so enable only when needed.
-- SET GLOBAL log_queries_not_using_indexes = 'ON';

SHOW VARIABLES
WHERE Variable_name IN (
    'log_output',
    'slow_query_log',
    'long_query_time',
    'min_examined_row_limit',
    'log_queries_not_using_indexes'
);
