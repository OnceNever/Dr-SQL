-- MySQL 8.0+ slow SQL lab bootstrap.
-- One-shot usage:
--   mysql -h 127.0.0.1 -P 3306 -u root -p < sql/mysql/00_setup_mysql_slow_sql_lab.sql
--
-- Default data volume:
--   user_info: 1,000,000 rows
--   orders:   10,000,000 rows
--
-- To run a smaller local smoke test, change these two variables before execution.

SET NAMES utf8mb4;
SET @user_rows := 1000000;
SET @order_rows := 10000000;

CREATE DATABASE IF NOT EXISTS dr_sql_slow_test
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE dr_sql_slow_test;

DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS user_info;

CREATE TABLE user_info (
    id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(128) NOT NULL,
    province VARCHAR(32) NOT NULL,
    city VARCHAR(64) NOT NULL,
    age INT NOT NULL,
    status TINYINT NOT NULL,
    created_at DATETIME NOT NULL,
    last_login_at DATETIME NULL,
    bio VARCHAR(512) NULL,
    remark VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_user_info_username (username),
    KEY idx_user_info_phone (phone),
    KEY idx_user_info_created_at (created_at),
    KEY idx_user_info_status_created (status, created_at),
    KEY idx_user_info_age (age)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE orders (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pay_type VARCHAR(20) NOT NULL,
    province VARCHAR(32) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    note VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_created_at (created_at),
    KEY idx_orders_status_created (status, created_at),
    KEY idx_orders_pay_type (pay_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Intentionally missing indexes:
--   user_info.province, user_info.city, user_info.email, user_info.bio
--   orders.amount, orders.province, orders.product_name, orders.note
-- These gaps are useful for validating a slow SQL optimization agent.

DROP TABLE IF EXISTS tmp_digit;
CREATE TABLE tmp_digit (
    n TINYINT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_digit (n) VALUES
    (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

SET unique_checks = 0;
SET foreign_key_checks = 0;

INSERT INTO user_info (
    id, username, phone, email, province, city, age, status,
    created_at, last_login_at, bio, remark
)
SELECT
    seq + 1 AS id,
    CONCAT('user_', LPAD(seq + 1, 7, '0')) AS username,
    CONCAT('13', LPAD((seq * 37) % 1000000000, 9, '0')) AS phone,
    CONCAT('user_', seq + 1, '@example.test') AS email,
    ELT((seq % 12) + 1,
        'Zhejiang', 'Jiangsu', 'Guangdong', 'Beijing',
        'Shanghai', 'Sichuan', 'Hubei', 'Hunan',
        'Fujian', 'Shandong', 'Henan', 'Shaanxi') AS province,
    CONCAT('city_', LPAD(seq % 200, 3, '0')) AS city,
    18 + (seq % 50) AS age,
    seq % 4 AS status,
    DATE_ADD(DATE_ADD('2021-01-01 00:00:00', INTERVAL (seq % 1200) DAY), INTERVAL (seq % 86400) SECOND) AS created_at,
    CASE
        WHEN seq % 11 = 0 THEN NULL
        ELSE DATE_ADD(DATE_ADD('2024-01-01 00:00:00', INTERVAL (seq % 500) DAY), INTERVAL (seq % 86400) SECOND)
    END AS last_login_at,
    CASE
        WHEN seq % 1000 = 0 THEN CONCAT('profile text with vip_keyword and tuning_marker ', seq)
        WHEN seq % 777 = 0 THEN CONCAT('profile text with refund_keyword and support_marker ', seq)
        ELSE CONCAT('ordinary profile text ', seq)
    END AS bio,
    CONCAT('synthetic user row ', seq) AS remark
FROM (
    SELECT
        d0.n
        + d1.n * 10
        + d2.n * 100
        + d3.n * 1000
        + d4.n * 10000
        + d5.n * 100000 AS seq
    FROM tmp_digit d0
    CROSS JOIN tmp_digit d1
    CROSS JOIN tmp_digit d2
    CROSS JOIN tmp_digit d3
    CROSS JOIN tmp_digit d4
    CROSS JOIN tmp_digit d5
) numbers
WHERE seq < @user_rows;

INSERT INTO orders (
    id, user_id, order_no, amount, status, pay_type, province,
    product_name, created_at, paid_at, note
)
SELECT
    seq + 1 AS id,
    (seq % @user_rows) + 1 AS user_id,
    CONCAT('ORD', LPAD(seq + 1, 12, '0')) AS order_no,
    ROUND(((seq * 17) % 200000) / 100 + 1, 2) AS amount,
    ELT((seq % 6) + 1, 'PAID', 'PAID', 'PAID', 'UNPAID', 'CANCELLED', 'REFUNDED') AS status,
    ELT((seq % 4) + 1, 'CARD', 'WECHAT', 'ALIPAY', 'BALANCE') AS pay_type,
    ELT((seq % 12) + 1,
        'Zhejiang', 'Jiangsu', 'Guangdong', 'Beijing',
        'Shanghai', 'Sichuan', 'Hubei', 'Hunan',
        'Fujian', 'Shandong', 'Henan', 'Shaanxi') AS province,
    CONCAT('product_', LPAD(seq % 5000, 4, '0')) AS product_name,
    DATE_ADD(DATE_ADD('2022-01-01 00:00:00', INTERVAL (seq % 900) DAY), INTERVAL (seq % 86400) SECOND) AS created_at,
    CASE
        WHEN seq % 6 IN (3, 4) THEN NULL
        ELSE DATE_ADD(DATE_ADD('2022-01-01 00:00:00', INTERVAL (seq % 900) DAY), INTERVAL ((seq % 86400) + 300) SECOND)
    END AS paid_at,
    CASE
        WHEN seq % 1200 = 0 THEN CONCAT('manual review note contains refund_keyword ', seq)
        WHEN seq % 1800 = 0 THEN CONCAT('customer note contains vip_keyword ', seq)
        ELSE CONCAT('normal order note ', seq)
    END AS note
FROM (
    SELECT
        d0.n
        + d1.n * 10
        + d2.n * 100
        + d3.n * 1000
        + d4.n * 10000
        + d5.n * 100000
        + d6.n * 1000000 AS seq
    FROM tmp_digit d0
    CROSS JOIN tmp_digit d1
    CROSS JOIN tmp_digit d2
    CROSS JOIN tmp_digit d3
    CROSS JOIN tmp_digit d4
    CROSS JOIN tmp_digit d5
    CROSS JOIN tmp_digit d6
) numbers
WHERE seq < @order_rows;

SET unique_checks = 1;
SET foreign_key_checks = 1;

ANALYZE TABLE user_info;
ANALYZE TABLE orders;

DROP TABLE IF EXISTS tmp_digit;

SELECT
    'dr_sql_slow_test bootstrap complete' AS message,
    @user_rows AS expected_user_info_rows,
    @order_rows AS expected_orders_rows;
