-- MySQL slow SQL cases for validating Dr.SQL.
-- Run after 00_setup_mysql_slow_sql_lab.sql.
--
-- Recommended usage:
--   mysql -h 127.0.0.1 -P 3306 -u root -p dr_sql_slow_test < sql/mysql/01_slow_sql_cases.sql
--
-- You can also copy one statement at a time to make slow log entries easier to inspect.

USE dr_sql_slow_test;

-- 01. Full table scan: province intentionally has no index.
SELECT COUNT(*)
FROM user_info
WHERE province = 'Zhejiang';

-- 02. Index invalidation: phone is indexed, but LEFT(phone, 3) prevents efficient index usage.
SELECT id, username, phone, province
FROM user_info
WHERE LEFT(phone, 3) = '138'
LIMIT 200;

-- 03. OR query: mixes low-selectivity status with unindexed province.
SELECT id, user_id, amount, status, province, created_at
FROM orders
WHERE status = 'REFUNDED'
   OR province = 'Zhejiang'
LIMIT 500;

-- 04. LIKE '%xx%': leading wildcard on a text column.
SELECT id, username, bio
FROM user_info
WHERE bio LIKE '%vip_keyword%'
LIMIT 200;

-- 05. Filesort: amount is intentionally not indexed.
SELECT id, user_id, amount, status, created_at
FROM orders
WHERE status = 'PAID'
ORDER BY amount DESC
LIMIT 200;

-- 06. Subquery: scans unindexed amount and aggregates a large order set.
SELECT id, username, province, created_at
FROM user_info
WHERE id IN (
    SELECT user_id
    FROM orders
    WHERE amount > 1800
    GROUP BY user_id
    HAVING COUNT(*) >= 3
)
LIMIT 200;

-- 07. LIMIT deep pagination: even with an index, the engine must skip many rows.
SELECT id, user_id, order_no, created_at
FROM orders
ORDER BY created_at
LIMIT 5000000, 100;

-- 08. Returning too much data: excessive result set size.
SELECT id, user_id, order_no, amount, status, note
FROM orders
WHERE status = 'PAID'
LIMIT 500000;

-- 09. GROUP BY on unindexed province.
SELECT province, COUNT(*) AS order_count, SUM(amount) AS total_amount
FROM orders
GROUP BY province
ORDER BY total_amount DESC;

-- 10. Join with unindexed filter: user_info.province has no index.
SELECT u.province, COUNT(*) AS order_count, SUM(o.amount) AS total_amount
FROM user_info u
JOIN orders o ON o.user_id = u.id
WHERE u.province = 'Zhejiang'
GROUP BY u.province
ORDER BY total_amount DESC;

-- 11. Non-sargable date expression: created_at is indexed, but DATE(created_at) blocks range access.
SELECT id, user_id, amount, created_at
FROM orders
WHERE DATE(created_at) = '2023-06-18'
LIMIT 500;

-- 12. Range plus filesort on another column.
SELECT id, user_id, amount, status, created_at
FROM orders
WHERE created_at >= '2023-01-01'
  AND created_at < '2023-07-01'
ORDER BY amount DESC
LIMIT 300;
