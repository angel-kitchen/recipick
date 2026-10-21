-- 시연 지역 ID는 팀 공통으로 고정한다. 기존 ID·코드가 충돌하면 덮어쓰지 않고 시작을 중단한다.
INSERT INTO region (id, legal_dong_code, name)
SELECT 1, '1111014000', '서울특별시 종로구 삼청동'
WHERE NOT EXISTS (SELECT 1 FROM region WHERE id = 1 AND legal_dong_code = '1111014000');

INSERT INTO region (id, legal_dong_code, name)
SELECT 2, '4121010300', '경기도 광명시 하안동'
WHERE NOT EXISTS (SELECT 1 FROM region WHERE id = 2 AND legal_dong_code = '4121010300');
