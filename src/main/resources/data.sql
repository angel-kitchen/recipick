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
SELECT 2, 2 , '하안 마트', 37.4640000, 126.8760000, 2000, 'OPEN', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM mart WHERE region_id = 2)
OR EXISTS (SELECT 1 FROM mart WHERE id = 2 AND region_id <> 2);

-- 지도 시연용 준비 중 점포. 배송 지역을 연결하지 않고 CLOSED로 저장해 주문 대상에서 제외한다.
INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '녹번마트 (준비중)', 37.603, 126.936, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '녹번마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '평창마트 (준비중)', 37.612, 126.964, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '평창마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '정릉마트 (준비중)', 37.611, 127.002, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '정릉마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '종암마트 (준비중)', 37.594, 127.035, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '종암마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '답십리마트 (준비중)', 37.576, 127.065, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '답십리마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '능동마트 (준비중)', 37.556, 127.08, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '능동마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '자양마트 (준비중)', 37.532, 127.065, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '자양마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '삼성마트 (준비중)', 37.508, 127.052, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '삼성마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '서초마트 (준비중)', 37.482, 127.031, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '서초마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '과천마트 (준비중)', 37.465, 126.998, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '과천마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '봉천마트 (준비중)', 37.477, 126.964, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '봉천마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '상도마트 (준비중)', 37.493, 126.935, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '상도마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '도림마트 (준비중)', 37.511, 126.906, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '도림마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '목동마트 (준비중)', 37.535, 126.886, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '목동마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '염창마트 (준비중)', 37.558, 126.865, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '염창마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '상암마트 (준비중)', 37.581, 126.884, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '상암마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '은평신사마트 (준비중)', 37.596, 126.909, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '은평신사마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '연희마트 (준비중)', 37.576, 126.942, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '연희마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '남대문마트 (준비중)', 37.558, 126.974, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '남대문마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '한남마트 (준비중)', 37.539, 127.009, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '한남마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '이촌마트 (준비중)', 37.517, 126.979, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '이촌마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '구로마트 (준비중)', 37.49, 126.882, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '구로마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '신림마트 1호점 (준비중)', 37.469, 126.914, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '신림마트 1호점 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '신림마트 2호점 (준비중)', 37.45, 126.943, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '신림마트 2호점 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '소하마트 (준비중)', 37.442, 126.895, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '소하마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '노온사마트 (준비중)', 37.454, 126.849, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '노온사마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '항동마트 (준비중)', 37.478, 126.828, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '항동마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '고척마트 (준비중)', 37.504, 126.849, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '고척마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '신월마트 (준비중)', 37.53, 126.837, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '신월마트 (준비중)');

INSERT INTO mart (region_id, name, latitude, longitude, service_radius_m, status, created_at, updated_at)
SELECT NULL, '공항마트 (준비중)', 37.555, 126.817, 2000, 'CLOSED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mart WHERE name = '공항마트 (준비중)');
