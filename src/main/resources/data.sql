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

-- 하안 마트 좌표가 철산동 쪽에 있어 하안동 안에서 현재 위치를 찾아도 이용 가능 지역과 어긋났다. 하안동 안쪽으로 옮긴다.
-- 위 INSERT는 이미 있는 행을 건너뛰므로 기존 DB에는 UPDATE로 반영한다. 예전 좌표일 때만 바꿔 관리자가 고친 값은 덮어쓰지 않는다.
UPDATE mart SET latitude = 37.4640000, longitude = 126.8760000, updated_at = NOW()
WHERE id = 2 AND region_id = 2 AND latitude = 37.4710000 AND longitude = 126.8760000;
