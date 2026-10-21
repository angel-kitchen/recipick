-- 시연 지역 ID는 팀 공통으로 고정한다. 기존 ID·코드가 충돌하면 덮어쓰지 않고 시작을 중단한다.
INSERT INTO region (id, legal_dong_code, name)
SELECT 1, '1111014000', '서울특별시 종로구 삼청동'
WHERE NOT EXISTS (SELECT 1 FROM region WHERE id = 1 AND legal_dong_code = '1111014000');

INSERT INTO region (id, legal_dong_code, name)
SELECT 2, '4121010300', '경기도 광명시 하안동'
WHERE NOT EXISTS (SELECT 1 FROM region WHERE id = 2 AND legal_dong_code = '4121010300');

-- 시연 마트는 지역(ID1, 2)과 같은 ID로 고정한다. 기존 ID가 충돌하면 덮어쓰지 않고 시작을 중단한다.
INSERT INTO mart (id, region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT 1, 1, '삼청 마트', 37.5860000, 126.9820000, 2000, 'OPEN', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM mart WHERE region_id = 1)
OR EXISTS (SELECT 1 FROM mart WHERE id = 1 AND region_id <> 1);

INSERT INTO mart(id, region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT 2, 2 , '하안 마트', 37.4710000, 126.8760000, 2000, 'OPEN', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM mart WHERE region_id = 2)
OR EXISTS (SELECT 1 FROM mart WHERE id = 2 AND region_id <> 2);

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
