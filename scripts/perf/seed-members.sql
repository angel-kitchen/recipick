-- 부하 테스트용 회원 100명을 만든다. 모두 같은 로컬 테스트용 비밀번호(k6 스크립트의 PERF_PASSWORD)를 쓰고 삼청 마트(1)에 배정된다.
-- 재실행해도 같은 결과가 나오도록 기존 성능 회원(perf-user-*@recipick.test)을 지우고 다시 만든다.
-- 실행(Git Bash, 레포 루트):
--   docker exec -i recipick-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 recipick_perf' < scripts/perf/seed-members.sql
SET SESSION cte_max_recursion_depth = 1000;

START TRANSACTION;

DELETE FROM member WHERE email LIKE 'perf-user-%@recipick.test';

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
FROM seq;

COMMIT;
