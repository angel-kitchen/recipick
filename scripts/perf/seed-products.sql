SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 100000;

DELETE FROM product WHERE description = 'PERF_SEED';

-- 카테고리 20개
INSERT IGNORE INTO category (name, deleted, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT CONCAT('성능카테고리', n), 0, NOW(), NOW() FROM seq;

-- 마트당 5만 개, 총 10만 개
INSERT INTO product (
    mart_id, category_id, name, brand, description,
    sale_unit_amount, sale_unit, price, status, total_quantity, deleted,
    created_at, updated_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000
),
cat AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS idx
    FROM category
    WHERE name LIKE '성능카테고리%'
)
SELECT
    m.id,
    c.id,
    CONCAT(
        ELT(s.n % 6 + 1, '국내산 ', '유기농 ', '냉동 ', '신선 ', '무항생제 ', '프리미엄 '),
        ELT(s.n % 12 + 1, '양파', '대파', '감자', '당근', '두부', '계란', '우유', '닭가슴살', '돼지고기', '소고기', '쌀', '김치'),
        ' ', s.n
    ),
    CONCAT('브랜드', s.n % 30),
    'PERF_SEED',
    ELT(s.n % 5 + 1, 100, 200, 500, 1000, 1500),
    ELT(s.n % 3 + 1, 'G', 'ML', 'EA'),
    500 + (s.n * 37 % 500) * 100,
    IF(s.n % 20 = 0, 'STOPPED', 'SELLING'),
    s.n * 13 % 501,
    s.n % 50 = 0,
    NOW() - INTERVAL (s.n % 365) DAY - INTERVAL (s.n % 1440) MINUTE,
    NOW()
FROM seq s
JOIN mart m ON m.id IN (1, 2)
JOIN cat c ON c.idx = s.n % 20;
