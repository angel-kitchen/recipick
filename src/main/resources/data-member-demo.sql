-- 공용 시연 회원. 로컬과 운영 시연 서버에서만 실행하고 자동 테스트의 초기데이터에는 넣지 않는다.
-- 최초 생성만 수행한다. 재실행으로 비밀번호·탈퇴 여부·선택 배송지·기존 이용권 상태를 초기화하지 않는다.
-- 이메일/전화번호가 이미 사용 중이면 다른 회원 정보를 덮어쓰지 않는다.
SET @demo_member_now = DATE_ADD(UTC_TIMESTAMP(), INTERVAL 9 HOUR);

INSERT INTO member (email, name, phone, password_hash, refresh_token_version, mart_id,
                    location_agreed_at, terms_agreed_at, privacy_agreed_at, created_at, updated_at)
SELECT demo.email, demo.name, demo.phone, '$2a$10$gWCBJ3WR2vA5teXDOZ0iOeMrr1PuNIkXf7t6C/g9T34hoX7vep7aC', 0, m.id,
       @demo_member_now, @demo_member_now, @demo_member_now, @demo_member_now, @demo_member_now
FROM (
    SELECT 'test01@reci.pick' AS email, '테스트 일반 회원' AS name, '01000001001' AS phone
    UNION ALL
    SELECT 'test02@reci.pick', '테스트 멤버십 회원', '01000001002'
) demo
JOIN region r ON r.legal_dong_code = '1111014000'
JOIN mart m ON m.region_id = r.id
WHERE NOT EXISTS (SELECT 1 FROM member existing WHERE existing.email = demo.email OR existing.phone = demo.phone);

-- 공용 시연 계정만 배송지를 준비한다. 사용자가 바꾼 기존 배송지는 유지한다.
INSERT INTO member_address (member_id, label, address, detail_address, legal_dong_code, created_at, updated_at)
SELECT m.id, '공용 테스트 배송지', '서울특별시 종로구 삼청로 98', '시연용 101호', '1111014000',
       @demo_member_now, @demo_member_now
FROM member m
WHERE ((m.email = 'test01@reci.pick' AND m.phone = '01000001001')
    OR (m.email = 'test02@reci.pick' AND m.phone = '01000001002'))
  AND m.withdrawn_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM member_address a WHERE a.member_id = m.id);

UPDATE member m
JOIN member_address a ON a.member_id = m.id AND a.label = '공용 테스트 배송지'
JOIN region r ON r.legal_dong_code = a.legal_dong_code
JOIN mart mart ON mart.region_id = r.id
SET m.selected_address_id = a.id, m.mart_id = mart.id, m.updated_at = @demo_member_now
WHERE ((m.email = 'test01@reci.pick' AND m.phone = '01000001001')
    OR (m.email = 'test02@reci.pick' AND m.phone = '01000001002'))
  AND m.withdrawn_at IS NULL
  AND m.selected_address_id IS NULL;

-- 시드 지급은 PG 결제·카드·자동갱신 없이 30일 혜택만 제공한다. 만료·종료 이력도 재실행 시 덮어쓰지 않는다.
INSERT INTO membership (member_id, granted_by_admin_id, type, source, start_at, end_at, activated_at,
                        grace_end_at, auto_renew, billing_anchor_date, created_at, updated_at)
SELECT m.id, NULL, 'PAID', 'ADMIN_GRANT', @demo_member_now, DATE_ADD(@demo_member_now, INTERVAL 30 DAY),
       @demo_member_now, NULL, FALSE, NULL, @demo_member_now, @demo_member_now
FROM member m
WHERE m.email = 'test02@reci.pick' AND m.phone = '01000001002' AND m.withdrawn_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM membership ms WHERE ms.member_id = m.id);
