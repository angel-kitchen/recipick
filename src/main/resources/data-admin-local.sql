-- 로컬·시연용 관리자 계정. 운영 배포에서는 이 테스트 초기화 SQL을 실행하지 않는다.
-- 기존 계정의 비밀번호·권한·활성 상태는 재실행해도 변경하지 않는다.
INSERT INTO admin (login_id, password_hash, name, role, mart_id, active, created_at, updated_at)
SELECT 'super01', '$2a$10$jxuhhdAkkBCHu1LkjzHriu1sc9/rZqRSmZoSNQGJm9b5MaaQUdACm', '서비스 운영자', 'SUPER_ADMIN', NULL, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM admin WHERE login_id = 'super01');

INSERT INTO admin (login_id, password_hash, name, role, mart_id, active, created_at, updated_at)
SELECT 'samcheong01', '$2a$10$jxuhhdAkkBCHu1LkjzHriu1sc9/rZqRSmZoSNQGJm9b5MaaQUdACm', '삼청 마트 관리자', 'MART_ADMIN', 1, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM admin WHERE login_id = 'samcheong01');

INSERT INTO admin (login_id, password_hash, name, role, mart_id, active, created_at, updated_at)
SELECT 'haan01', '$2a$10$jxuhhdAkkBCHu1LkjzHriu1sc9/rZqRSmZoSNQGJm9b5MaaQUdACm', '하안 마트 관리자', 'MART_ADMIN', 2, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM admin WHERE login_id = 'haan01');
