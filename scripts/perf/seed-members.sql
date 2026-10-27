-- 부하 테스트용 회원 100명을 만든다. 모두 같은 로컬 테스트용 비밀번호(k6 스크립트의 PERF_PASSWORD)를 쓰고 삼청 마트(1)에 배정된다.
-- 재실행해도 회원 번호와 연결된 데이터가 유지되도록 이메일 또는 전화번호가 같으면 갱신하고 없으면 추가한다. 성능 전용 DB에서만 실행한다.
-- 실행(Git Bash, 레포 루트):
--   docker exec -i recipick-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 recipick_perf' < scripts/perf/seed-members.sql
SET SESSION cte_max_recursion_depth = 1000;

INSERT INTO member (email, name, phone, password_hash, refresh_token_version, mart_id,
                    location_agreed_at, terms_agreed_at, privacy_agreed_at, created_at, updated_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT CONCAT('perf-user-', LPAD(n, 3, '0'), '@recipick.test'),
       CONCAT('성능회원', n),
       CONCAT('0109', LPAD(n, 7, '0')),
       '$2y$10$44V3tHY/4uaN9sPEPEsI6Ojur1C4w5rYloKgfV1hK.PGPRT09PH8G',
       0,
       1,
       NOW(), NOW(), NOW(), NOW(), NOW()
FROM seq
    ON DUPLICATE KEY UPDATE
                         name = VALUES(name),
                         password_hash = VALUES(password_hash),
                         mart_id = VALUES(mart_id),
                         withdrawn_at = NULL,
                         updated_at = NOW();
