-- 상품 검색 쿼리의 실행 계획 재현용 스크립트. 설명은 docs/performance/product/bottleneck.md 참고.
-- 전제: 성능 DB(recipick_perf)에 scripts/perf/seed-products.sql 데이터가 있고 앱이 한 번 떠서 인덱스가 만들어져 있다.
-- 실행(레포 루트, Git Bash):
--   docker exec -i recipick-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 recipick_perf' < scripts/perf/explain-product-search.sql
-- 각 쿼리는 ProductRepositoryImpl.searchMemberProducts가 만드는 조건을 손으로 옮긴 것이다. 코드가 바뀌면 다시 맞춘다.
-- 같은 쿼리를 두 번 실행하면 두 번째가 버퍼가 데워진 값이다. 시간은 첫 실행과 비교하지 않는다.

SET @now = NOW();

-- 1) 키워드 검색 목록: 기본 정렬(최신순). idx_product_mart_created를 거꾸로 읽다가 20건에서 멈추는지 본다.
EXPLAIN ANALYZE
SELECT p.id FROM product p
WHERE p.deleted = 0 AND p.mart_id = 1 AND p.name LIKE '%대파%'
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20;

-- 2) 키워드 검색 개수: LIKE '%…%'는 인덱스를 못 써서 마트 상품 전체를 훑는다(개선되지 않은 부분).
EXPLAIN ANALYZE
SELECT COUNT(*) FROM product p
WHERE p.deleted = 0 AND p.mart_id = 1 AND p.name LIKE '%대파%';

-- 3) 필터 검색 목록(현재 앱 코드 모양): 가격 조건과 정렬이 판매가 CASE 식(#494)이라 price 컬럼 인덱스의 범위·정렬을 못 쓴다.
EXPLAIN ANALYZE
SELECT p.id FROM product p
WHERE p.deleted = 0 AND p.mart_id = 1 AND p.category_id = 5
  AND (CASE WHEN p.discount_rate > 0 AND p.discount_end_at > @now
            THEN FLOOR(p.price * (100 - p.discount_rate) / 100.0) ELSE p.price END) >= 1000
  AND (CASE WHEN p.discount_rate > 0 AND p.discount_end_at > @now
            THEN FLOOR(p.price * (100 - p.discount_rate) / 100.0) ELSE p.price END) <= 20000
ORDER BY (CASE WHEN p.discount_rate > 0 AND p.discount_end_at > @now
               THEN FLOOR(p.price * (100 - p.discount_rate) / 100.0) ELSE p.price END) ASC, p.id DESC
LIMIT 20;

-- 4) 필터 검색 목록(판매가 반영 전 모양, 비교용): price 컬럼을 직접 쓰면 인덱스 범위 스캔(covering)이 된다.
EXPLAIN ANALYZE
SELECT p.id FROM product p
WHERE p.deleted = 0 AND p.mart_id = 1 AND p.category_id = 5 AND p.price >= 1000 AND p.price <= 20000
ORDER BY p.price ASC, p.id DESC
LIMIT 20;

-- 5) 필터 검색 개수(현재 앱 코드 모양)
EXPLAIN ANALYZE
SELECT COUNT(*) FROM product p
WHERE p.deleted = 0 AND p.mart_id = 1 AND p.category_id = 5
  AND (CASE WHEN p.discount_rate > 0 AND p.discount_end_at > @now
            THEN FLOOR(p.price * (100 - p.discount_rate) / 100.0) ELSE p.price END) >= 1000
  AND (CASE WHEN p.discount_rate > 0 AND p.discount_end_at > @now
            THEN FLOOR(p.price * (100 - p.discount_rate) / 100.0) ELSE p.price END) <= 20000;
