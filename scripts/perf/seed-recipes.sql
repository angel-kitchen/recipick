-- 레시피 부하 테스트용 데이터. 레시피 5만 건과 레시피마다 재료 5~10개를 만든다. 성능 전용 DB에서만 실행한다.
-- 전제: 앱을 local 프로필로 한 번 띄워 테이블과 기존 재료(ingredient)가 만들어져 있어야 한다.
-- 이름이 'PERF-'로 시작하는 레시피만 지우고 다시 만들므로 여러 번 실행해도 같은 데이터가 된다.
-- 값은 번호의 MD5로 정해 회차마다 같은 분포를 쓴다(Before/After 비교 조건 고정).
-- name_choseong은 앱의 초성 backfill(500건씩)이 기동 때 오래 돌지 않도록 이름과 같은 규칙으로 함께 넣는다.
-- 실행(레포 루트):
--   docker exec -i recipick-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 recipick_perf' < scripts/perf/seed-recipes.sql
SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 100000;

START TRANSACTION;

DELETE ri FROM recipe_ingredient ri JOIN recipe r ON r.id = ri.recipe_id WHERE r.name LIKE 'PERF-%';
DELETE rb FROM recipe_bookmark rb JOIN recipe r ON r.id = rb.recipe_id WHERE r.name LIKE 'PERF-%';
DELETE FROM recipe WHERE name LIKE 'PERF-%';

-- 이름: PERF-{수식어} {주재료} {요리} {번호}. 검색어(김치·된장·닭·두부·파스타 등)가 고르게 걸리도록 조합한다.
-- 초성 목록은 이름 목록과 같은 순서로 맞춘다(Choseong.of와 같은 결과).
INSERT INTO recipe (
    name, name_choseong, cuisine, dish_type, base_servings, cook_time_min, difficulty, spicy_level,
    steps, youtube_video_id, status, auto_cart_enabled, view_count, created_at, updated_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000
),
pick AS (
    SELECT
        n,
        CONV(LEFT(MD5(CONCAT('a', n)), 8), 16, 10) % 6 + 1 AS adj,
        CONV(LEFT(MD5(CONCAT('m', n)), 8), 16, 10) % 12 + 1 AS main,
        CONV(LEFT(MD5(CONCAT('d', n)), 8), 16, 10) % 8 + 1 AS dish
    FROM seq
)
SELECT
    CONCAT('PERF-',
        ELT(adj, '얼큰한', '간단한', '매콤한', '담백한', '든든한', '달콤한'), ' ',
        ELT(main, '김치', '된장', '돼지고기', '소고기', '닭', '두부', '감자', '계란', '버섯', '어묵', '참치', '새우'), ' ',
        ELT(dish, '찌개', '국', '볶음', '덮밥', '볶음밥', '국수', '조림', '파스타'), ' ', n),
    CONCAT('PERF-',
        ELT(adj, 'ㅇㅋㅎ', 'ㄱㄷㅎ', 'ㅁㅋㅎ', 'ㄷㅂㅎ', 'ㄷㄷㅎ', 'ㄷㅋㅎ'), ' ',
        ELT(main, 'ㄱㅊ', 'ㄷㅈ', 'ㄷㅈㄱㄱ', 'ㅅㄱㄱ', 'ㄷ', 'ㄷㅂ', 'ㄱㅈ', 'ㄱㄹ', 'ㅂㅅ', 'ㅇㅁ', 'ㅊㅊ', 'ㅅㅇ'), ' ',
        ELT(dish, 'ㅉㄱ', 'ㄱ', 'ㅂㅇ', 'ㄷㅂ', 'ㅂㅇㅂ', 'ㄱㅅ', 'ㅈㄹ', 'ㅍㅅㅌ'), ' ', n),
    IF(dish = 8, 'WESTERN', 'KOREAN'),
    ELT(dish, 'STEW', 'SOUP', 'STIR_FRY', 'RICE', 'RICE', 'NOODLE', 'SIDE', 'PASTA'),
    n % 4 + 1,
    10 + n % 50,
    ELT(n % 3 + 1, 'EASY', 'NORMAL', 'HARD'),
    n % 4,
    -- 실제 레시피처럼 조리 단계 6개를 넣는다. 목록 조회가 steps까지 읽는 비용을 재기 위해 크기를 맞춘다.
    JSON_ARRAY(
        JSON_OBJECT('order', 1, 'description', '재료를 깨끗이 씻고 물기를 뺀 뒤 먹기 좋은 크기로 썬다'),
        JSON_OBJECT('order', 2, 'description', '냄비나 팬을 중불로 달구고 식용유를 두른 뒤 향신 채소를 먼저 볶는다'),
        JSON_OBJECT('order', 3, 'description', '주재료를 넣고 겉면이 익을 때까지 2~3분 정도 저어 가며 볶는다'),
        JSON_OBJECT('order', 4, 'description', '양념과 물을 넣고 한소끔 끓어오르면 불을 줄여 10분 정도 더 익힌다'),
        JSON_OBJECT('order', 5, 'description', '간을 보고 싱거우면 소금이나 간장으로 맞추고 남은 채소를 넣는다'),
        JSON_OBJECT('order', 6, 'description', '불을 끄고 그릇에 담은 뒤 깨와 송송 썬 파를 올려 마무리한다')
    ),
    NULL,
    IF(n % 20 = 0, 'INACTIVE', 'ACTIVE'),
    0,
    CONV(LEFT(MD5(CONCAT('v', n)), 8), 16, 10) % 1000,
    NOW() - INTERVAL (n % 365) DAY - INTERVAL (n % 1440) MINUTE,
    NOW()
FROM pick;

-- 레시피마다 재료 5~10개. 기존 재료 목록에서 연속한 번호로 골라 (recipe_id, ingredient_id) 중복이 생기지 않게 한다.
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
WITH RECURSIVE k(i) AS (
    SELECT 1 UNION ALL SELECT i + 1 FROM k WHERE i < 10
),
ing AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS idx FROM ingredient
),
ing_count AS (
    SELECT COUNT(*) AS cnt FROM ingredient
),
target AS (
    SELECT id,
           CONV(LEFT(MD5(CONCAT('n', id)), 8), 16, 10) % 6 + 5 AS ingredient_cnt,
           CONV(LEFT(MD5(CONCAT('s', id)), 8), 16, 10) AS start_no
    FROM recipe
    WHERE name LIKE 'PERF-%'
)
SELECT t.id,
       g.id,
       ELT(k.i % 5 + 1, 1.00, 50.00, 100.00, 150.50, 200.00),
       k.i <= 3
FROM target t
JOIN k ON k.i <= t.ingredient_cnt
JOIN ing_count c
JOIN ing g ON g.idx = (t.start_no + k.i) % c.cnt;

COMMIT;
