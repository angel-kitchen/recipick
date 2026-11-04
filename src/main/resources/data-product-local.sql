-- 로컬·시연용 상품·재료·레시피 재료 초기 데이터. scripts/recipe-seed/GenerateProductSeed.java로 생성하며 직접 고치지 않는다.
-- 이미 있는 행은 건너뛰므로 서버를 다시 띄워도 중복으로 쌓이지 않는다.
-- 반드시 data.sql(마트)과 data-recipe-local.sql(레시피) 다음에 읽는다.
-- 이 파일을 다시 읽을 때 아래 레시피의 자동 담기가 다시 켜진다(관리자가 끈 설정도 되돌려진다).

UPDATE category SET name = '채소·과일', updated_at = NOW()
WHERE name = '채소' AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM category WHERE name = '채소·과일') c);
UPDATE category SET name = '육류·가공육', updated_at = NOW()
WHERE name = '육류' AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM category WHERE name = '육류·가공육') c);

INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '채소·과일', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '채소·과일');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '육류·가공육', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '육류·가공육');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '수산·건어물', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '수산·건어물');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '두부·계란·유제품', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '두부·계란·유제품');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '쌀·곡물·가루', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '쌀·곡물·가루');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '면·파스타·떡', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '면·파스타·떡');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '빵·가공식품', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '빵·가공식품');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '장류·소스', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '장류·소스');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '기름·양념·향신료', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '기름·양념·향신료');

INSERT INTO ingredient (name, unit)
SELECT '김치', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '김치');
INSERT INTO ingredient (name, unit)
SELECT '돼지고기 앞다리살', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '돼지고기 앞다리살');
INSERT INTO ingredient (name, unit)
SELECT '두부', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '두부');
INSERT INTO ingredient (name, unit)
SELECT '대파', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '대파');
INSERT INTO ingredient (name, unit)
SELECT '양파', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '양파');
INSERT INTO ingredient (name, unit)
SELECT '다진마늘', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '다진마늘');
INSERT INTO ingredient (name, unit)
SELECT '고춧가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '고춧가루');
INSERT INTO ingredient (name, unit)
SELECT '된장', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '된장');
INSERT INTO ingredient (name, unit)
SELECT '애호박', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '애호박');
INSERT INTO ingredient (name, unit)
SELECT '청양고추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '청양고추');
INSERT INTO ingredient (name, unit)
SELECT '국물용 멸치', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '국물용 멸치');
INSERT INTO ingredient (name, unit)
SELECT '건미역', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '건미역');
INSERT INTO ingredient (name, unit)
SELECT '소고기 국거리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '소고기 국거리');
INSERT INTO ingredient (name, unit)
SELECT '국간장', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '국간장');
INSERT INTO ingredient (name, unit)
SELECT '참기름', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '참기름');
INSERT INTO ingredient (name, unit)
SELECT '고추장', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '고추장');
INSERT INTO ingredient (name, unit)
SELECT '간장', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '간장');
INSERT INTO ingredient (name, unit)
SELECT '설탕', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '설탕');
INSERT INTO ingredient (name, unit)
SELECT '계란', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '계란');
INSERT INTO ingredient (name, unit)
SELECT '당근', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '당근');
INSERT INTO ingredient (name, unit)
SELECT '소금', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '소금');
INSERT INTO ingredient (name, unit)
SELECT '식용유', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '식용유');
INSERT INTO ingredient (name, unit)
SELECT '새우젓', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '새우젓');
INSERT INTO ingredient (name, unit)
SELECT '스파게티면', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '스파게티면');
INSERT INTO ingredient (name, unit)
SELECT '베이컨', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '베이컨');
INSERT INTO ingredient (name, unit)
SELECT '파마산치즈 가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '파마산치즈 가루');
INSERT INTO ingredient (name, unit)
SELECT '생크림', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '생크림');
INSERT INTO ingredient (name, unit)
SELECT '후추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '후추');
INSERT INTO ingredient (name, unit)
SELECT '올리브유', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '올리브유');
INSERT INTO ingredient (name, unit)
SELECT '토마토소스', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '토마토소스');
INSERT INTO ingredient (name, unit)
SELECT '통마늘', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '통마늘');
INSERT INTO ingredient (name, unit)
SELECT '생강', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '생강');
INSERT INTO ingredient (name, unit)
SELECT '홍고추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '홍고추');
INSERT INTO ingredient (name, unit)
SELECT '건고추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '건고추');
INSERT INTO ingredient (name, unit)
SELECT '꽈리고추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '꽈리고추');
INSERT INTO ingredient (name, unit)
SELECT '감자', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '감자');
INSERT INTO ingredient (name, unit)
SELECT '무', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '무');
INSERT INTO ingredient (name, unit)
SELECT '콩나물', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '콩나물');
INSERT INTO ingredient (name, unit)
SELECT '시금치', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '시금치');
INSERT INTO ingredient (name, unit)
SELECT '오이', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '오이');
INSERT INTO ingredient (name, unit)
SELECT '양배추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '양배추');
INSERT INTO ingredient (name, unit)
SELECT '고구마', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '고구마');
INSERT INTO ingredient (name, unit)
SELECT '깻잎', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '깻잎');
INSERT INTO ingredient (name, unit)
SELECT '표고버섯', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '표고버섯');
INSERT INTO ingredient (name, unit)
SELECT '양송이버섯', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '양송이버섯');
INSERT INTO ingredient (name, unit)
SELECT '파프리카', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '파프리카');
INSERT INTO ingredient (name, unit)
SELECT '피망', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '피망');
INSERT INTO ingredient (name, unit)
SELECT '토마토', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '토마토');
INSERT INTO ingredient (name, unit)
SELECT '방울토마토', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '방울토마토');
INSERT INTO ingredient (name, unit)
SELECT '단호박', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '단호박');
INSERT INTO ingredient (name, unit)
SELECT '고사리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '고사리');
INSERT INTO ingredient (name, unit)
SELECT '숙주', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '숙주');
INSERT INTO ingredient (name, unit)
SELECT '우거지', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '우거지');
INSERT INTO ingredient (name, unit)
SELECT '곤드레', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '곤드레');
INSERT INTO ingredient (name, unit)
SELECT '쑥갓', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '쑥갓');
INSERT INTO ingredient (name, unit)
SELECT '미나리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '미나리');
INSERT INTO ingredient (name, unit)
SELECT '우엉', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '우엉');
INSERT INTO ingredient (name, unit)
SELECT '밤', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '밤');
INSERT INTO ingredient (name, unit)
SELECT '대추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '대추');
INSERT INTO ingredient (name, unit)
SELECT '인삼', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '인삼');
INSERT INTO ingredient (name, unit)
SELECT '셀러리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '셀러리');
INSERT INTO ingredient (name, unit)
SELECT '로메인', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '로메인');
INSERT INTO ingredient (name, unit)
SELECT '양상추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '양상추');
INSERT INTO ingredient (name, unit)
SELECT '아보카도', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '아보카도');
INSERT INTO ingredient (name, unit)
SELECT '옥수수', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '옥수수');
INSERT INTO ingredient (name, unit)
SELECT '레몬', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '레몬');
INSERT INTO ingredient (name, unit)
SELECT '바질', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '바질');
INSERT INTO ingredient (name, unit)
SELECT '파슬리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '파슬리');
INSERT INTO ingredient (name, unit)
SELECT '로즈마리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '로즈마리');
INSERT INTO ingredient (name, unit)
SELECT '피클', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '피클');
INSERT INTO ingredient (name, unit)
SELECT '견과류', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '견과류');
INSERT INTO ingredient (name, unit)
SELECT '쪽파', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '쪽파');
INSERT INTO ingredient (name, unit)
SELECT '다진 돼지고기', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '다진 돼지고기');
INSERT INTO ingredient (name, unit)
SELECT '다진 소고기', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '다진 소고기');
INSERT INTO ingredient (name, unit)
SELECT '소고기 불고기용', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '소고기 불고기용');
INSERT INTO ingredient (name, unit)
SELECT '소갈비', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '소갈비');
INSERT INTO ingredient (name, unit)
SELECT '소고기 홍두깨살', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '소고기 홍두깨살');
INSERT INTO ingredient (name, unit)
SELECT '소고기 스테이크용', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '소고기 스테이크용');
INSERT INTO ingredient (name, unit)
SELECT '돼지 등뼈', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '돼지 등뼈');
INSERT INTO ingredient (name, unit)
SELECT '닭볶음탕용 닭', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '닭볶음탕용 닭');
INSERT INTO ingredient (name, unit)
SELECT '닭다리살', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '닭다리살');
INSERT INTO ingredient (name, unit)
SELECT '닭가슴살', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '닭가슴살');
INSERT INTO ingredient (name, unit)
SELECT '영계', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '영계');
INSERT INTO ingredient (name, unit)
SELECT '햄', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '햄');
INSERT INTO ingredient (name, unit)
SELECT '소시지', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '소시지');
INSERT INTO ingredient (name, unit)
SELECT '동태', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '동태');
INSERT INTO ingredient (name, unit)
SELECT '오징어', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '오징어');
INSERT INTO ingredient (name, unit)
SELECT '낙지', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '낙지');
INSERT INTO ingredient (name, unit)
SELECT '쭈꾸미', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '쭈꾸미');
INSERT INTO ingredient (name, unit)
SELECT '북어채', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '북어채');
INSERT INTO ingredient (name, unit)
SELECT '고등어', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '고등어');
INSERT INTO ingredient (name, unit)
SELECT '새우', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '새우');
INSERT INTO ingredient (name, unit)
SELECT '바지락', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '바지락');
INSERT INTO ingredient (name, unit)
SELECT '홍합', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '홍합');
INSERT INTO ingredient (name, unit)
SELECT '어묵', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '어묵');
INSERT INTO ingredient (name, unit)
SELECT '맛살', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '맛살');
INSERT INTO ingredient (name, unit)
SELECT '잔멸치', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '잔멸치');
INSERT INTO ingredient (name, unit)
SELECT '다시마', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '다시마');
INSERT INTO ingredient (name, unit)
SELECT '김가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '김가루');
INSERT INTO ingredient (name, unit)
SELECT '김밥김', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '김밥김');
INSERT INTO ingredient (name, unit)
SELECT '안초비', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '안초비');
INSERT INTO ingredient (name, unit)
SELECT '순두부', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '순두부');
INSERT INTO ingredient (name, unit)
SELECT '우유', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '우유');
INSERT INTO ingredient (name, unit)
SELECT '버터', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '버터');
INSERT INTO ingredient (name, unit)
SELECT '모차렐라 치즈', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '모차렐라 치즈');
INSERT INTO ingredient (name, unit)
SELECT '체다 치즈', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '체다 치즈');
INSERT INTO ingredient (name, unit)
SELECT '요거트', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '요거트');
INSERT INTO ingredient (name, unit)
SELECT '쌀', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '쌀');
INSERT INTO ingredient (name, unit)
SELECT '찹쌀', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '찹쌀');
INSERT INTO ingredient (name, unit)
SELECT '백태', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '백태');
INSERT INTO ingredient (name, unit)
SELECT '밀가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '밀가루');
INSERT INTO ingredient (name, unit)
SELECT '부침가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '부침가루');
INSERT INTO ingredient (name, unit)
SELECT '빵가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '빵가루');
INSERT INTO ingredient (name, unit)
SELECT '전분', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '전분');
INSERT INTO ingredient (name, unit)
SELECT '소면', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '소면');
INSERT INTO ingredient (name, unit)
SELECT '칼국수면', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '칼국수면');
INSERT INTO ingredient (name, unit)
SELECT '냉면사리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '냉면사리');
INSERT INTO ingredient (name, unit)
SELECT '쫄면사리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '쫄면사리');
INSERT INTO ingredient (name, unit)
SELECT '메밀면', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '메밀면');
INSERT INTO ingredient (name, unit)
SELECT '당면', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '당면');
INSERT INTO ingredient (name, unit)
SELECT '라면사리', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '라면사리');
INSERT INTO ingredient (name, unit)
SELECT '펜네', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '펜네');
INSERT INTO ingredient (name, unit)
SELECT '마카로니', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '마카로니');
INSERT INTO ingredient (name, unit)
SELECT '라자냐면', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '라자냐면');
INSERT INTO ingredient (name, unit)
SELECT '떡국떡', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '떡국떡');
INSERT INTO ingredient (name, unit)
SELECT '떡볶이떡', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '떡볶이떡');
INSERT INTO ingredient (name, unit)
SELECT '식빵', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '식빵');
INSERT INTO ingredient (name, unit)
SELECT '바게트', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '바게트');
INSERT INTO ingredient (name, unit)
SELECT '번', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '번');
INSERT INTO ingredient (name, unit)
SELECT '잉글리시 머핀', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '잉글리시 머핀');
INSERT INTO ingredient (name, unit)
SELECT '또띠아', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '또띠아');
INSERT INTO ingredient (name, unit)
SELECT '유부', 'EA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '유부');
INSERT INTO ingredient (name, unit)
SELECT '후레이크', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '후레이크');
INSERT INTO ingredient (name, unit)
SELECT '단무지', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '단무지');
INSERT INTO ingredient (name, unit)
SELECT '통조림 콩', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '통조림 콩');
INSERT INTO ingredient (name, unit)
SELECT '참치캔', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '참치캔');
INSERT INTO ingredient (name, unit)
SELECT '청국장', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '청국장');
INSERT INTO ingredient (name, unit)
SELECT '액젓', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '액젓');
INSERT INTO ingredient (name, unit)
SELECT '케첩', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '케첩');
INSERT INTO ingredient (name, unit)
SELECT '마요네즈', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '마요네즈');
INSERT INTO ingredient (name, unit)
SELECT '머스터드', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '머스터드');
INSERT INTO ingredient (name, unit)
SELECT '겨자', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '겨자');
INSERT INTO ingredient (name, unit)
SELECT '굴소스', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '굴소스');
INSERT INTO ingredient (name, unit)
SELECT '우스터소스', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '우스터소스');
INSERT INTO ingredient (name, unit)
SELECT '냉면육수', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '냉면육수');
INSERT INTO ingredient (name, unit)
SELECT '들기름', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '들기름');
INSERT INTO ingredient (name, unit)
SELECT '고추기름', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '고추기름');
INSERT INTO ingredient (name, unit)
SELECT '식초', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '식초');
INSERT INTO ingredient (name, unit)
SELECT '맛술', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '맛술');
INSERT INTO ingredient (name, unit)
SELECT '물엿', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '물엿');
INSERT INTO ingredient (name, unit)
SELECT '통깨', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '통깨');
INSERT INTO ingredient (name, unit)
SELECT '들깻가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '들깻가루');
INSERT INTO ingredient (name, unit)
SELECT '페페론치노', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '페페론치노');
INSERT INTO ingredient (name, unit)
SELECT '월계수잎', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '월계수잎');
INSERT INTO ingredient (name, unit)
SELECT '통후추', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '통후추');
INSERT INTO ingredient (name, unit)
SELECT '시나몬', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '시나몬');
INSERT INTO ingredient (name, unit)
SELECT '바닐라익스트랙', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '바닐라익스트랙');
INSERT INTO ingredient (name, unit)
SELECT '사프란', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '사프란');
INSERT INTO ingredient (name, unit)
SELECT '파프리카 가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '파프리카 가루');
INSERT INTO ingredient (name, unit)
SELECT '슈가파우더', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '슈가파우더');
INSERT INTO ingredient (name, unit)
SELECT '화이트와인', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '화이트와인');
INSERT INTO ingredient (name, unit)
SELECT '카레가루', 'G'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '카레가루');
INSERT INTO ingredient (name, unit)
SELECT '매실청', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '매실청');
INSERT INTO ingredient (name, unit)
SELECT '메이플시럽', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '메이플시럽');
INSERT INTO ingredient (name, unit)
SELECT '배즙', 'ML'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = '배즙');

INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '된장'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '애호박'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '된장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진 돼지고기'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '바지락'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '순두부'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추기름'
WHERE r.name = '순두부찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진 소고기'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '햄'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소시지'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 90, TRUE
FROM recipe r JOIN ingredient i ON i.name = '라면사리'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '체다 치즈'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통조림 콩'
WHERE r.name = '부대찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 90, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '애호박'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '청국장'
WHERE r.name = '청국장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 90, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '애호박'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '고추장찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '동태'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '생강'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, FALSE
FROM recipe r JOIN ingredient i ON i.name = '쑥갓'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, FALSE
FROM recipe r JOIN ingredient i ON i.name = '미나리'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '동태찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '참치캔'
WHERE r.name = '참치김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '참치김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '참치김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '참치김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '참치김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '참치김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '참치김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '참치김치찌개'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 250, TRUE
FROM recipe r JOIN ingredient i ON i.name = '닭볶음탕용 닭'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '닭볶음탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '건미역'
WHERE r.name = '미역국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소고기 국거리'
WHERE r.name = '미역국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '미역국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '미역국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, TRUE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '미역국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '미역국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소고기 국거리'
WHERE r.name = '소고기무국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '소고기무국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '소고기무국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '소고기무국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '소고기무국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '소고기무국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '소고기무국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '콩나물국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우젓'
WHERE r.name = '콩나물국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '콩나물국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '콩나물국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '콩나물국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '콩나물'
WHERE r.name = '콩나물국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '콩나물국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '북어채'
WHERE r.name = '북엇국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우젓'
WHERE r.name = '북엇국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '북엇국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '북엇국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '북엇국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '북엇국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '북엇국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '북엇국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '시금치된장국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '시금치된장국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '시금치된장국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '시금치'
WHERE r.name = '시금치된장국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '된장'
WHERE r.name = '시금치된장국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '시금치된장국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소고기 국거리'
WHERE r.name = '떡국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '김가루'
WHERE r.name = '떡국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '떡국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '떡국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '떡국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '떡국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '떡국떡'
WHERE r.name = '떡국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '떡볶이떡'
WHERE r.name = '떡국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소고기 국거리'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고사리'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '숙주'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추기름'
WHERE r.name = '육개장'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 500, TRUE
FROM recipe r JOIN ingredient i ON i.name = '영계'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, FALSE
FROM recipe r JOIN ingredient i ON i.name = '밤'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '대추'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '인삼'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '찹쌀'
WHERE r.name = '삼계탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소갈비'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당면'
WHERE r.name = '갈비탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 250, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지 등뼈'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '생강'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '깻잎'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우거지'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '들깻가루'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '된장'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '감자탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '어묵'
WHERE r.name = '어묵탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '어묵탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다시마'
WHERE r.name = '어묵탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '어묵탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '어묵탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '어묵탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '어묵탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '어묵탕'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '건미역'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '홍고추'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '오이'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, FALSE
FROM recipe r JOIN ingredient i ON i.name = '매실청'
WHERE r.name = '오이냉국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '계란국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '계란국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '계란국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '계란국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '계란국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '계란국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '계란국'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '생강'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '제육볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '오징어'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양배추'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '오징어볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '낙지'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '낙지볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쭈꾸미'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '생강'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양배추'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '쭈꾸미볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소고기 불고기용'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '표고버섯'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '배즙'
WHERE r.name = '소불고기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '닭다리살'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양배추'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고구마'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '깻잎'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '카레가루'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '떡볶이떡'
WHERE r.name = '닭갈비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 90, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '돼지고기두루치기'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '김가루'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '김치볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '소고기 국거리'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '애호박'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '콩나물'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '시금치'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고사리'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '비빔밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 90, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '콩나물'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '콩나물밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '햄'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '어묵'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '시금치'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우엉'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '단무지'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '맛살'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김밥김'
WHERE r.name = '김밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '햄'
WHERE r.name = '유부초밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '유부초밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '유부초밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, TRUE
FROM recipe r JOIN ingredient i ON i.name = '유부'
WHERE r.name = '유부초밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후레이크'
WHERE r.name = '유부초밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 90, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '제육덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '참치캔'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '김가루'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '마요네즈'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '참치마요덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '굴소스'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '새우볶음밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 75, TRUE
FROM recipe r JOIN ingredient i ON i.name = '참치캔'
WHERE r.name = '주먹밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '김가루'
WHERE r.name = '주먹밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '주먹밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '주먹밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '주먹밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '마요네즈'
WHERE r.name = '주먹밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '주먹밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '곤드레밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '곤드레'
WHERE r.name = '곤드레밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '곤드레밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '곤드레밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '곤드레밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '곤드레밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '들기름'
WHERE r.name = '곤드레밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '곤드레밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '낙지'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '전분'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '낙지덮밥'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다시마'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '김가루'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '애호박'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소면'
WHERE r.name = '잔치국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '오이'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소면'
WHERE r.name = '비빔국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다시마'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '김가루'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '애호박'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '칼국수면'
WHERE r.name = '칼국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다시마'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '애호박'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '수제비'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소고기 국거리'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '오이'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '겨자'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 160, TRUE
FROM recipe r JOIN ingredient i ON i.name = '냉면사리'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 300, TRUE
FROM recipe r JOIN ingredient i ON i.name = '냉면육수'
WHERE r.name = '물냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '오이'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '배즙'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 160, TRUE
FROM recipe r JOIN ingredient i ON i.name = '냉면사리'
WHERE r.name = '비빔냉면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '오이'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, FALSE
FROM recipe r JOIN ingredient i ON i.name = '견과류'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소면'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통조림 콩'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '백태'
WHERE r.name = '콩국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '어묵'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양배추'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 90, TRUE
FROM recipe r JOIN ingredient i ON i.name = '라면사리'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '떡볶이떡'
WHERE r.name = '라볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, FALSE
FROM recipe r JOIN ingredient i ON i.name = '콩나물'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '오이'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양배추'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '물엿'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쫄면사리'
WHERE r.name = '쫄면'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '김가루'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쪽파'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '들기름'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '메밀면'
WHERE r.name = '들기름막국수'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '계란말이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '계란말이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '계란말이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '계란말이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '계란말이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '계란말이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '맛술'
WHERE r.name = '계란말이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '시금치나물'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '시금치나물'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '시금치'
WHERE r.name = '시금치나물'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '시금치나물'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국간장'
WHERE r.name = '시금치나물'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '시금치나물'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '시금치나물'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '콩나물무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '콩나물무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '콩나물'
WHERE r.name = '콩나물무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '콩나물무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '콩나물무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '콩나물무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '콩나물무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '국물용 멸치'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '잔멸치'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, FALSE
FROM recipe r JOIN ingredient i ON i.name = '견과류'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '물엿'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '멸치볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '감자조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '감자조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '감자조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '감자조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '감자조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '물엿'
WHERE r.name = '감자조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '감자조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '감자조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '두부'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '두부조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 125, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소고기 홍두깨살'
WHERE r.name = '장조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '장조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '장조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '꽈리고추'
WHERE r.name = '장조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '장조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '장조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '장조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통후추'
WHERE r.name = '장조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '액젓'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '오이'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '오이무침'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '감자채볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '감자채볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '감자채볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '감자채볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '감자채볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '감자채볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '감자채볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '어묵'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '물엿'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '어묵볶음'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '액젓'
WHERE r.name = '무생채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쪽파'
WHERE r.name = '무생채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '무생채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '무생채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '무생채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '무생채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '무생채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '무생채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고등어'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '생강'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '맛술'
WHERE r.name = '고등어조림'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '소고기 국거리'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '시금치'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '표고버섯'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '파프리카'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당면'
WHERE r.name = '잡채'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '김치전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '김치전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '김치전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '김치전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '부침가루'
WHERE r.name = '김치전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '오징어'
WHERE r.name = '해물파전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우'
WHERE r.name = '해물파전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '바지락'
WHERE r.name = '해물파전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쪽파'
WHERE r.name = '해물파전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '해물파전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '해물파전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '해물파전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '부침가루'
WHERE r.name = '해물파전'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '어묵'
WHERE r.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고추장'
WHERE r.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '떡볶이떡'
WHERE r.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '계란찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우젓'
WHERE r.name = '계란찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '계란찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '계란찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통깨'
WHERE r.name = '계란찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '계란찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 200, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소갈비'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '무'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '표고버섯'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, FALSE
FROM recipe r JOIN ingredient i ON i.name = '밤'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '대추'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 4, FALSE
FROM recipe r JOIN ingredient i ON i.name = '참기름'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '배즙'
WHERE r.name = '갈비찜'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 250, TRUE
FROM recipe r JOIN ingredient i ON i.name = '닭볶음탕용 닭'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '생강'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '청양고추'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '건고추'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '간장'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '물엿'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당면'
WHERE r.name = '찜닭'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 200, TRUE
FROM recipe r JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우젓'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '김치'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '대파'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '된장'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '월계수잎'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '통후추'
WHERE r.name = '수육'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '스파게티면'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '베이컨'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, FALSE
FROM recipe r JOIN ingredient i ON i.name = '생크림'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '까르보나라'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '알리오올리오'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파슬리'
WHERE r.name = '알리오올리오'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '알리오올리오'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '알리오올리오'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '페페론치노'
WHERE r.name = '알리오올리오'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '스파게티면'
WHERE r.name = '알리오올리오'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '스파게티면'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토소스'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진마늘'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '바질'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '토마토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '바지락'
WHERE r.name = '봉골레파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '봉골레파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파슬리'
WHERE r.name = '봉골레파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '봉골레파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '봉골레파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '페페론치노'
WHERE r.name = '봉골레파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '화이트와인'
WHERE r.name = '봉골레파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '스파게티면'
WHERE r.name = '봉골레파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '베이컨'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '생크림'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '고춧가루'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토소스'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '스파게티면'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '펜네'
WHERE r.name = '로제파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 70, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진 돼지고기'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 70, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진 소고기'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파슬리'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토소스'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '빵가루'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '스파게티면'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '미트볼스파게티'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진 소고기'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토소스'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '라자냐면'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '모차렐라 치즈'
WHERE r.name = '라자냐'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '바질페스토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, FALSE
FROM recipe r JOIN ingredient i ON i.name = '방울토마토'
WHERE r.name = '바질페스토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '바질'
WHERE r.name = '바질페스토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, FALSE
FROM recipe r JOIN ingredient i ON i.name = '견과류'
WHERE r.name = '바질페스토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '바질페스토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '바질페스토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '바질페스토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '스파게티면'
WHERE r.name = '바질페스토파스타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '아라비아타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파슬리'
WHERE r.name = '아라비아타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '아라비아타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '아라비아타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '아라비아타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토소스'
WHERE r.name = '아라비아타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '페페론치노'
WHERE r.name = '아라비아타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '펜네'
WHERE r.name = '아라비아타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '모차렐라 치즈'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '체다 치즈'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '빵가루'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '마카로니'
WHERE r.name = '맥앤치즈'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 200, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '뇨끼'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '뇨끼'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '생크림'
WHERE r.name = '뇨끼'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '뇨끼'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '뇨끼'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '뇨끼'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '뇨끼'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양송이버섯'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파슬리'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '생크림'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '양송이수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '단호박수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '단호박'
WHERE r.name = '단호박수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, FALSE
FROM recipe r JOIN ingredient i ON i.name = '견과류'
WHERE r.name = '단호박수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '단호박수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '생크림'
WHERE r.name = '단호박수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '단호박수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '단호박수프'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '베이컨'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '바지락'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '셀러리'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파슬리'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '생크림'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '화이트와인'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '밀가루'
WHERE r.name = '클램차우더'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '애호박'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '셀러리'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토소스'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통조림 콩'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '마카로니'
WHERE r.name = '미네스트로네'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '버섯리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '표고버섯'
WHERE r.name = '버섯리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양송이버섯'
WHERE r.name = '버섯리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '버섯리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '버섯리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '화이트와인'
WHERE r.name = '버섯리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '버섯리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, FALSE
FROM recipe r JOIN ingredient i ON i.name = '방울토마토'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 3, FALSE
FROM recipe r JOIN ingredient i ON i.name = '바질'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토소스'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '토마토리조또'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '오징어'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '홍합'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '파프리카'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '레몬'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '사프란'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파프리카 가루'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '빠에야'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '햄'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '당근'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '케첩'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '쌀'
WHERE r.name = '오므라이스'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소고기 스테이크용'
WHERE r.name = '스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '로즈마리'
WHERE r.name = '스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식용유'
WHERE r.name = '스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 70, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진 돼지고기'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 70, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진 소고기'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '케첩'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우스터소스'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '빵가루'
WHERE r.name = '함박스테이크'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, TRUE
FROM recipe r JOIN ingredient i ON i.name = '새우'
WHERE r.name = '감바스알아히요'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '감바스알아히요'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파슬리'
WHERE r.name = '감바스알아히요'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '감바스알아히요'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '감바스알아히요'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '감바스알아히요'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '페페론치노'
WHERE r.name = '감바스알아히요'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 80, FALSE
FROM recipe r JOIN ingredient i ON i.name = '바게트'
WHERE r.name = '감바스알아히요'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 6, TRUE
FROM recipe r JOIN ingredient i ON i.name = '설탕'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, FALSE
FROM recipe r JOIN ingredient i ON i.name = '메이플시럽'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '시나몬'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, FALSE
FROM recipe r JOIN ingredient i ON i.name = '바닐라익스트랙'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '슈가파우더'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식빵'
WHERE r.name = '프렌치토스트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '닭가슴살'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '베이컨'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '안초비'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '통마늘'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, TRUE
FROM recipe r JOIN ingredient i ON i.name = '로메인'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '레몬'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '마요네즈'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '머스터드'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '식빵'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 50, FALSE
FROM recipe r JOIN ingredient i ON i.name = '바게트'
WHERE r.name = '시저샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '닭가슴살'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '베이컨'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양상추'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '아보카도'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '옥수수'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '레몬'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '요거트'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '마요네즈'
WHERE r.name = '콥샐러드'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '햄'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '베이컨'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양상추'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '마요네즈'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '머스터드'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 150, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식빵'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '체다 치즈'
WHERE r.name = '클럽샌드위치'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '햄'
WHERE r.name = '에그베네딕트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '베이컨'
WHERE r.name = '에그베네딕트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '레몬'
WHERE r.name = '에그베네딕트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '에그베네딕트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '에그베네딕트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '식초'
WHERE r.name = '에그베네딕트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파프리카 가루'
WHERE r.name = '에그베네딕트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '잉글리시 머핀'
WHERE r.name = '에그베네딕트'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, FALSE
FROM recipe r JOIN ingredient i ON i.name = '햄'
WHERE r.name = '또띠아피자'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '또띠아피자'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, FALSE
FROM recipe r JOIN ingredient i ON i.name = '피망'
WHERE r.name = '또띠아피자'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '옥수수'
WHERE r.name = '또띠아피자'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '모차렐라 치즈'
WHERE r.name = '또띠아피자'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 100, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토소스'
WHERE r.name = '또띠아피자'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '또띠아'
WHERE r.name = '또띠아피자'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 120, TRUE
FROM recipe r JOIN ingredient i ON i.name = '다진 소고기'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, TRUE
FROM recipe r JOIN ingredient i ON i.name = '토마토'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양상추'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, FALSE
FROM recipe r JOIN ingredient i ON i.name = '피클'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '버터'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '마요네즈'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '케첩'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '머스터드'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '번'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 20, TRUE
FROM recipe r JOIN ingredient i ON i.name = '체다 치즈'
WHERE r.name = '수제버거'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '베이컨'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 40, TRUE
FROM recipe r JOIN ingredient i ON i.name = '양파'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 60, TRUE
FROM recipe r JOIN ingredient i ON i.name = '감자'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 30, TRUE
FROM recipe r JOIN ingredient i ON i.name = '시금치'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.25, TRUE
FROM recipe r JOIN ingredient i ON i.name = '파프리카'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 1, TRUE
FROM recipe r JOIN ingredient i ON i.name = '계란'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 15, TRUE
FROM recipe r JOIN ingredient i ON i.name = '우유'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 8, FALSE
FROM recipe r JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 2, TRUE
FROM recipe r JOIN ingredient i ON i.name = '소금'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 0.5, FALSE
FROM recipe r JOIN ingredient i ON i.name = '후추'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)
SELECT r.id, i.id, 10, TRUE
FROM recipe r JOIN ingredient i ON i.name = '올리브유'
WHERE r.name = '프리타타'
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);

-- 중복 시연 상품 정리: 스파게티면 500g은 2,900원 상품만 유지한다.
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.deleted = TRUE, p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '스파게티면' AND p.name = '스파게티면 500g' AND p.brand = '데체코'
  AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.price = 4500 AND p.deleted = FALSE;

-- 기존 시연 상품의 브랜드를 갱신한다. 상품 ID·상품명·용량·가격·재고는 유지한다.
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김치' AND p.name = '김치 500g' AND p.brand = '종가' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김치' AND p.name = '김치 900g' AND p.brand = '풀무원' AND p.sale_unit_amount = 900 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '돼지고기 앞다리살' AND p.name = '돼지고기 앞다리살 500g' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '돼지고기 앞다리살' AND p.name = '돼지고기 앞다리살 600g' AND p.brand = '한돈' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '두부' AND p.name = '두부 300g' AND p.brand = '풀무원' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '두부' AND p.name = '두부 500g' AND p.brand = 'CJ' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '대파' AND p.name = '대파 300g' AND p.brand = '국내산' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '대파' AND p.name = '대파 500g' AND p.brand = '친환경' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양파' AND p.name = '양파 1kg' AND p.brand = '국내산' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양파' AND p.name = '양파 1.5kg' AND p.brand = '국내산 대' AND p.sale_unit_amount = 1500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진마늘' AND p.name = '다진마늘 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진마늘' AND p.name = '다진마늘 500g' AND p.brand = '국내산 대용량' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고춧가루' AND p.name = '고춧가루 200g' AND p.brand = '청정원' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고춧가루' AND p.name = '고춧가루 500g' AND p.brand = '해찬들' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '된장' AND p.name = '된장 500g' AND p.brand = '해찬들' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '된장' AND p.name = '된장 1kg' AND p.brand = '사조' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '애호박' AND p.name = '애호박 1개' AND p.brand = '국내산' AND p.sale_unit_amount = 1 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '애호박' AND p.name = '애호박 2개' AND p.brand = '친환경' AND p.sale_unit_amount = 2 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '청양고추' AND p.name = '청양고추 100g' AND p.brand = '국내산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '청양고추' AND p.name = '청양고추 200g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '국물용 멸치' AND p.name = '국물용 멸치 200g' AND p.brand = '남해' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '국물용 멸치' AND p.name = '국물용 멸치 400g' AND p.brand = '통영' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '건미역' AND p.name = '건미역 50g' AND p.brand = '완도' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '건미역' AND p.name = '건미역 100g' AND p.brand = '기장' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 국거리' AND p.name = '소고기 국거리 300g' AND p.brand = '국내산 한우' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 국거리' AND p.name = '소고기 국거리 400g' AND p.brand = '호주산' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '국간장' AND p.name = '국간장 500ml' AND p.brand = '청정원' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '국간장' AND p.name = '국간장 900ml' AND p.brand = '샘표' AND p.sale_unit_amount = 900 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '참기름' AND p.name = '참기름 320ml' AND p.brand = '오뚜기' AND p.sale_unit_amount = 320 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '참기름' AND p.name = '참기름 500ml' AND p.brand = 'CJ' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고추장' AND p.name = '고추장 500g' AND p.brand = '해찬들' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고추장' AND p.name = '고추장 1kg' AND p.brand = 'CJ' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '간장' AND p.name = '간장 500ml' AND p.brand = '샘표' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '간장' AND p.name = '간장 900ml' AND p.brand = '몽고' AND p.sale_unit_amount = 900 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '설탕' AND p.name = '설탕 1kg' AND p.brand = 'CJ' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '설탕' AND p.name = '설탕 3kg' AND p.brand = '삼양' AND p.sale_unit_amount = 3000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '계란' AND p.name = '계란 10개' AND p.brand = '특란' AND p.sale_unit_amount = 10 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '계란' AND p.name = '계란 30개' AND p.brand = '대란' AND p.sale_unit_amount = 30 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '당근' AND p.name = '당근 2개' AND p.brand = '국내산' AND p.sale_unit_amount = 2 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '당근' AND p.name = '당근 4개' AND p.brand = '국내산 대' AND p.sale_unit_amount = 4 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소금' AND p.name = '소금 500g' AND p.brand = '꽃소금' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소금' AND p.name = '소금 1kg' AND p.brand = '천일염' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식용유' AND p.name = '식용유 900ml' AND p.brand = 'CJ 해표' AND p.sale_unit_amount = 900 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식용유' AND p.name = '식용유 1.8L' AND p.brand = '오뚜기' AND p.sale_unit_amount = 1800 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '새우젓' AND p.name = '새우젓 200g' AND p.brand = '육젓' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '새우젓' AND p.name = '새우젓 500g' AND p.brand = '추젓' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '스파게티면' AND p.name = '스파게티면 500g' AND p.brand = '폰타나' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '베이컨' AND p.name = '베이컨 200g' AND p.brand = '대림' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '베이컨' AND p.name = '베이컨 400g' AND p.brand = '롯데' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파마산치즈 가루' AND p.name = '파마산치즈 가루 100g' AND p.brand = '서울우유' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파마산치즈 가루' AND p.name = '파마산치즈 가루 200g' AND p.brand = '데이지' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '생크림' AND p.name = '생크림 250ml' AND p.brand = '서울우유' AND p.sale_unit_amount = 250 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '생크림' AND p.name = '생크림 500ml' AND p.brand = '매일' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '후추' AND p.name = '후추 50g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '후추' AND p.name = '후추 100g' AND p.brand = '백설' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '올리브유' AND p.name = '올리브유 500ml' AND p.brand = '사조' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '올리브유' AND p.name = '올리브유 1L' AND p.brand = '오뚜기' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '토마토소스' AND p.name = '토마토소스 400g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '토마토소스' AND p.name = '토마토소스 600g' AND p.brand = '폰타나' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통마늘' AND p.name = '통마늘 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통마늘' AND p.name = '통마늘 500g' AND p.brand = '의성' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '생강' AND p.name = '생강 100g' AND p.brand = '국내산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '생강' AND p.name = '생강 300g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '홍고추' AND p.name = '홍고추 100g' AND p.brand = '국내산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '홍고추' AND p.name = '홍고추 200g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '건고추' AND p.name = '건고추 50g' AND p.brand = '국내산' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '건고추' AND p.name = '건고추 100g' AND p.brand = '영양' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '꽈리고추' AND p.name = '꽈리고추 150g' AND p.brand = '국내산' AND p.sale_unit_amount = 150 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '꽈리고추' AND p.name = '꽈리고추 300g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '감자' AND p.name = '감자 1kg' AND p.brand = '국내산' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '감자' AND p.name = '감자 2kg' AND p.brand = '수미' AND p.sale_unit_amount = 2000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '무' AND p.name = '무 800g' AND p.brand = '국내산' AND p.sale_unit_amount = 800 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '무' AND p.name = '무 1.5kg' AND p.brand = '국내산 대' AND p.sale_unit_amount = 1500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '콩나물' AND p.name = '콩나물 300g' AND p.brand = '풀무원' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '콩나물' AND p.name = '콩나물 500g' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '시금치' AND p.name = '시금치 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '시금치' AND p.name = '시금치 400g' AND p.brand = '포항초' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '오이' AND p.name = '오이 2개' AND p.brand = '국내산' AND p.sale_unit_amount = 2 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '오이' AND p.name = '오이 5개' AND p.brand = '취청' AND p.sale_unit_amount = 5 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양배추' AND p.name = '양배추 800g' AND p.brand = '국내산' AND p.sale_unit_amount = 800 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양배추' AND p.name = '양배추 1.5kg' AND p.brand = '국내산 대' AND p.sale_unit_amount = 1500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고구마' AND p.name = '고구마 1kg' AND p.brand = '국내산' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고구마' AND p.name = '고구마 2kg' AND p.brand = '호박고구마' AND p.sale_unit_amount = 2000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '깻잎' AND p.name = '깻잎 100g' AND p.brand = '국내산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '깻잎' AND p.name = '깻잎 200g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '표고버섯' AND p.name = '표고버섯 150g' AND p.brand = '국내산' AND p.sale_unit_amount = 150 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '표고버섯' AND p.name = '표고버섯 300g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양송이버섯' AND p.name = '양송이버섯 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양송이버섯' AND p.name = '양송이버섯 400g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파프리카' AND p.name = '파프리카 2개' AND p.brand = '국내산' AND p.sale_unit_amount = 2 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파프리카' AND p.name = '파프리카 4개' AND p.brand = '국내산 대' AND p.sale_unit_amount = 4 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '피망' AND p.name = '피망 3개' AND p.brand = '국내산' AND p.sale_unit_amount = 3 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '피망' AND p.name = '피망 6개' AND p.brand = '국내산 대' AND p.sale_unit_amount = 6 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '토마토' AND p.name = '토마토 3개' AND p.brand = '국내산' AND p.sale_unit_amount = 3 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '토마토' AND p.name = '토마토 6개' AND p.brand = '국내산 대' AND p.sale_unit_amount = 6 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '방울토마토' AND p.name = '방울토마토 500g' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '방울토마토' AND p.name = '방울토마토 1kg' AND p.brand = '대추방울' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '단호박' AND p.name = '단호박 1개' AND p.brand = '국내산' AND p.sale_unit_amount = 1 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '단호박' AND p.name = '단호박 2개' AND p.brand = '국내산 대' AND p.sale_unit_amount = 2 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고사리' AND p.name = '고사리 150g' AND p.brand = '국내산' AND p.sale_unit_amount = 150 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고사리' AND p.name = '고사리 300g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '숙주' AND p.name = '숙주 300g' AND p.brand = '국내산' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '숙주' AND p.name = '숙주 500g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우거지' AND p.name = '우거지 300g' AND p.brand = '국내산' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우거지' AND p.name = '우거지 600g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '곤드레' AND p.name = '곤드레 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '곤드레' AND p.name = '곤드레 400g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쑥갓' AND p.name = '쑥갓 100g' AND p.brand = '국내산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쑥갓' AND p.name = '쑥갓 200g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '미나리' AND p.name = '미나리 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '미나리' AND p.name = '미나리 400g' AND p.brand = '청도' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우엉' AND p.name = '우엉 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우엉' AND p.name = '우엉 400g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '밤' AND p.name = '밤 300g' AND p.brand = '국내산' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '밤' AND p.name = '밤 600g' AND p.brand = '공주' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '대추' AND p.name = '대추 100g' AND p.brand = '국내산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '대추' AND p.name = '대추 200g' AND p.brand = '경산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '인삼' AND p.name = '인삼 100g' AND p.brand = '국내산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '인삼' AND p.name = '인삼 200g' AND p.brand = '금산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '셀러리' AND p.name = '셀러리 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '셀러리' AND p.name = '셀러리 400g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '로메인' AND p.name = '로메인 150g' AND p.brand = '국내산' AND p.sale_unit_amount = 150 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '로메인' AND p.name = '로메인 300g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양상추' AND p.name = '양상추 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양상추' AND p.name = '양상추 400g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '아보카도' AND p.name = '아보카도 2개' AND p.brand = '페루산' AND p.sale_unit_amount = 2 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '아보카도' AND p.name = '아보카도 4개' AND p.brand = '멕시코산' AND p.sale_unit_amount = 4 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '옥수수' AND p.name = '옥수수 340g' AND p.brand = '델몬트' AND p.sale_unit_amount = 340 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '옥수수' AND p.name = '옥수수 680g' AND p.brand = '동원' AND p.sale_unit_amount = 680 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '레몬' AND p.name = '레몬 3개' AND p.brand = '미국산' AND p.sale_unit_amount = 3 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '레몬' AND p.name = '레몬 6개' AND p.brand = '칠레산' AND p.sale_unit_amount = 6 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바질' AND p.name = '바질 20g' AND p.brand = '국내산' AND p.sale_unit_amount = 20 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바질' AND p.name = '바질 50g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파슬리' AND p.name = '파슬리 20g' AND p.brand = '국내산' AND p.sale_unit_amount = 20 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파슬리' AND p.name = '파슬리 50g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '로즈마리' AND p.name = '로즈마리 20g' AND p.brand = '국내산' AND p.sale_unit_amount = 20 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '로즈마리' AND p.name = '로즈마리 50g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '피클' AND p.name = '피클 300g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '피클' AND p.name = '피클 600g' AND p.brand = '청정원' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '견과류' AND p.name = '견과류 150g' AND p.brand = '견과믹스' AND p.sale_unit_amount = 150 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '견과류' AND p.name = '견과류 300g' AND p.brand = '견과믹스 대' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쪽파' AND p.name = '쪽파 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쪽파' AND p.name = '쪽파 400g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진 돼지고기' AND p.name = '다진 돼지고기 300g' AND p.brand = '국내산' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진 돼지고기' AND p.name = '다진 돼지고기 600g' AND p.brand = '한돈' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진 소고기' AND p.name = '다진 소고기 300g' AND p.brand = '호주산' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진 소고기' AND p.name = '다진 소고기 500g' AND p.brand = '한우' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 불고기용' AND p.name = '소고기 불고기용 300g' AND p.brand = '호주산' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 불고기용' AND p.name = '소고기 불고기용 500g' AND p.brand = '한우' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소갈비' AND p.name = '소갈비 1kg' AND p.brand = '호주산' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소갈비' AND p.name = '소갈비 1.5kg' AND p.brand = '한우' AND p.sale_unit_amount = 1500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 홍두깨살' AND p.name = '소고기 홍두깨살 500g' AND p.brand = '호주산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 홍두깨살' AND p.name = '소고기 홍두깨살 1kg' AND p.brand = '한우' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 스테이크용' AND p.name = '소고기 스테이크용 250g' AND p.brand = '호주산 안심' AND p.sale_unit_amount = 250 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 스테이크용' AND p.name = '소고기 스테이크용 400g' AND p.brand = '한우 등심' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '돼지 등뼈' AND p.name = '돼지 등뼈 1kg' AND p.brand = '국내산' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '돼지 등뼈' AND p.name = '돼지 등뼈 2kg' AND p.brand = '국내산 대' AND p.sale_unit_amount = 2000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭볶음탕용 닭' AND p.name = '닭볶음탕용 닭 1kg' AND p.brand = '하림' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭볶음탕용 닭' AND p.name = '닭볶음탕용 닭 1.5kg' AND p.brand = '마니커' AND p.sale_unit_amount = 1500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭다리살' AND p.name = '닭다리살 500g' AND p.brand = '하림' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭다리살' AND p.name = '닭다리살 1kg' AND p.brand = '마니커' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭가슴살' AND p.name = '닭가슴살 500g' AND p.brand = '하림' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭가슴살' AND p.name = '닭가슴살 1kg' AND p.brand = '마니커' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '영계' AND p.name = '영계 500g' AND p.brand = '삼계탕용' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '영계' AND p.name = '영계 1kg' AND p.brand = '삼계탕용 2마리' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '햄' AND p.name = '햄 200g' AND p.brand = '스팸' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '햄' AND p.name = '햄 340g' AND p.brand = '롯데' AND p.sale_unit_amount = 340 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소시지' AND p.name = '소시지 300g' AND p.brand = '롯데' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소시지' AND p.name = '소시지 600g' AND p.brand = 'CJ' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '동태' AND p.name = '동태 500g' AND p.brand = '국내 해동' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '동태' AND p.name = '동태 1kg' AND p.brand = '국내 해동 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '오징어' AND p.name = '오징어 400g' AND p.brand = '국내산' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '오징어' AND p.name = '오징어 800g' AND p.brand = '동해' AND p.sale_unit_amount = 800 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '낙지' AND p.name = '낙지 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '낙지' AND p.name = '낙지 400g' AND p.brand = '서해' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쭈꾸미' AND p.name = '쭈꾸미 300g' AND p.brand = '국내산' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쭈꾸미' AND p.name = '쭈꾸미 600g' AND p.brand = '서해' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '북어채' AND p.name = '북어채 100g' AND p.brand = '국내산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '북어채' AND p.name = '북어채 200g' AND p.brand = '황태' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고등어' AND p.name = '고등어 400g' AND p.brand = '노르웨이산' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고등어' AND p.name = '고등어 800g' AND p.brand = '국내산' AND p.sale_unit_amount = 800 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '새우' AND p.name = '새우 300g' AND p.brand = '칵테일' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '새우' AND p.name = '새우 500g' AND p.brand = '흰다리' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바지락' AND p.name = '바지락 300g' AND p.brand = '해감' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바지락' AND p.name = '바지락 500g' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '홍합' AND p.name = '홍합 500g' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '홍합' AND p.name = '홍합 1kg' AND p.brand = '국내산 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '어묵' AND p.name = '어묵 200g' AND p.brand = '삼진' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '어묵' AND p.name = '어묵 400g' AND p.brand = '사각' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '맛살' AND p.name = '맛살 200g' AND p.brand = '크라운' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '맛살' AND p.name = '맛살 400g' AND p.brand = '사조' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '잔멸치' AND p.name = '잔멸치 100g' AND p.brand = '남해' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '잔멸치' AND p.name = '잔멸치 200g' AND p.brand = '통영' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다시마' AND p.name = '다시마 50g' AND p.brand = '완도' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다시마' AND p.name = '다시마 100g' AND p.brand = '기장' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김가루' AND p.name = '김가루 50g' AND p.brand = '광천' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김가루' AND p.name = '김가루 100g' AND p.brand = '성경' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김밥김' AND p.name = '김밥김 10개' AND p.brand = '광천' AND p.sale_unit_amount = 10 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김밥김' AND p.name = '김밥김 20개' AND p.brand = '성경' AND p.sale_unit_amount = 20 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '안초비' AND p.name = '안초비 50g' AND p.brand = '국내산' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '안초비' AND p.name = '안초비 100g' AND p.brand = '수입산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '순두부' AND p.name = '순두부 350g' AND p.brand = '풀무원' AND p.sale_unit_amount = 350 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '순두부' AND p.name = '순두부 700g' AND p.brand = 'CJ' AND p.sale_unit_amount = 700 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우유' AND p.name = '우유 900ml' AND p.brand = '서울우유' AND p.sale_unit_amount = 900 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우유' AND p.name = '우유 1.8L' AND p.brand = '매일' AND p.sale_unit_amount = 1800 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '버터' AND p.name = '버터 200g' AND p.brand = '서울우유 무염' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '버터' AND p.name = '버터 450g' AND p.brand = '앵커' AND p.sale_unit_amount = 450 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '모차렐라 치즈' AND p.name = '모차렐라 치즈 200g' AND p.brand = '서울우유' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '모차렐라 치즈' AND p.name = '모차렐라 치즈 1kg' AND p.brand = '대용량' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '체다 치즈' AND p.name = '체다 치즈 200g' AND p.brand = '슬라이스' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '체다 치즈' AND p.name = '체다 치즈 400g' AND p.brand = '슬라이스 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '요거트' AND p.name = '요거트 400g' AND p.brand = '플레인' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '요거트' AND p.name = '요거트 800g' AND p.brand = '플레인 대' AND p.sale_unit_amount = 800 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쌀' AND p.name = '쌀 1kg' AND p.brand = '국내산' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쌀' AND p.name = '쌀 4kg' AND p.brand = '신동진' AND p.sale_unit_amount = 4000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '찹쌀' AND p.name = '찹쌀 500g' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '찹쌀' AND p.name = '찹쌀 1kg' AND p.brand = '국내산 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '백태' AND p.name = '백태 500g' AND p.brand = '국산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '백태' AND p.name = '백태 1kg' AND p.brand = '국산 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '밀가루' AND p.name = '밀가루 1kg' AND p.brand = 'CJ' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '밀가루' AND p.name = '밀가루 2.5kg' AND p.brand = '곰표' AND p.sale_unit_amount = 2500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '부침가루' AND p.name = '부침가루 500g' AND p.brand = 'CJ' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '부침가루' AND p.name = '부침가루 1kg' AND p.brand = '백설' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '빵가루' AND p.name = '빵가루 200g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '빵가루' AND p.name = '빵가루 500g' AND p.brand = '큐원' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '전분' AND p.name = '전분 200g' AND p.brand = 'CJ' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '전분' AND p.name = '전분 500g' AND p.brand = '옥수수전분' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소면' AND p.name = '소면 500g' AND p.brand = '사조' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소면' AND p.name = '소면 900g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 900 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '칼국수면' AND p.name = '칼국수면 500g' AND p.brand = '풀무원' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '칼국수면' AND p.name = '칼국수면 1kg' AND p.brand = 'CJ' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '냉면사리' AND p.name = '냉면사리 400g' AND p.brand = '풀무원' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '냉면사리' AND p.name = '냉면사리 800g' AND p.brand = 'CJ' AND p.sale_unit_amount = 800 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쫄면사리' AND p.name = '쫄면사리 400g' AND p.brand = '풀무원' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쫄면사리' AND p.name = '쫄면사리 800g' AND p.brand = 'CJ' AND p.sale_unit_amount = 800 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '메밀면' AND p.name = '메밀면 200g' AND p.brand = '봉평' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '메밀면' AND p.name = '메밀면 400g' AND p.brand = '봉평 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '당면' AND p.name = '당면 300g' AND p.brand = '대상' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '당면' AND p.name = '당면 500g' AND p.brand = 'CJ' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '라면사리' AND p.name = '라면사리 420g' AND p.brand = '농심' AND p.sale_unit_amount = 420 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '라면사리' AND p.name = '라면사리 840g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 840 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '펜네' AND p.name = '펜네 500g' AND p.brand = '데체코' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '펜네' AND p.name = '펜네 1kg' AND p.brand = '폰타나' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '마카로니' AND p.name = '마카로니 500g' AND p.brand = '폰타나' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '마카로니' AND p.name = '마카로니 1kg' AND p.brand = '데체코' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '라자냐면' AND p.name = '라자냐면 250g' AND p.brand = '데체코' AND p.sale_unit_amount = 250 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '라자냐면' AND p.name = '라자냐면 500g' AND p.brand = '바릴라' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '떡국떡' AND p.name = '떡국떡 500g' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '떡국떡' AND p.name = '떡국떡 1kg' AND p.brand = '국내산 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '떡볶이떡' AND p.name = '떡볶이떡 500g' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '떡볶이떡' AND p.name = '떡볶이떡 1kg' AND p.brand = '국내산 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식빵' AND p.name = '식빵 450g' AND p.brand = '삼립' AND p.sale_unit_amount = 450 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식빵' AND p.name = '식빵 900g' AND p.brand = '샤니' AND p.sale_unit_amount = 900 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바게트' AND p.name = '바게트 250g' AND p.brand = '파리바게뜨' AND p.sale_unit_amount = 250 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바게트' AND p.name = '바게트 500g' AND p.brand = '뚜레쥬르' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '번' AND p.name = '번 4개' AND p.brand = '햄버거번' AND p.sale_unit_amount = 4 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '번' AND p.name = '번 8개' AND p.brand = '햄버거번 대' AND p.sale_unit_amount = 8 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '잉글리시 머핀' AND p.name = '잉글리시 머핀 4개' AND p.brand = '국내산' AND p.sale_unit_amount = 4 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '잉글리시 머핀' AND p.name = '잉글리시 머핀 8개' AND p.brand = '국내산 대' AND p.sale_unit_amount = 8 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '또띠아' AND p.name = '또띠아 8개' AND p.brand = '오뚜기' AND p.sale_unit_amount = 8 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '또띠아' AND p.name = '또띠아 16개' AND p.brand = 'CJ' AND p.sale_unit_amount = 16 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '유부' AND p.name = '유부 20개' AND p.brand = '유부초밥용' AND p.sale_unit_amount = 20 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '유부' AND p.name = '유부 40개' AND p.brand = '유부초밥용 대' AND p.sale_unit_amount = 40 AND p.sale_unit = 'EA' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '후레이크' AND p.name = '후레이크 40g' AND p.brand = '유부초밥용' AND p.sale_unit_amount = 40 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '후레이크' AND p.name = '후레이크 100g' AND p.brand = '유부초밥용 대' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '단무지' AND p.name = '단무지 300g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 300 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '단무지' AND p.name = '단무지 600g' AND p.brand = '청정원' AND p.sale_unit_amount = 600 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통조림 콩' AND p.name = '통조림 콩 400g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통조림 콩' AND p.name = '통조림 콩 800g' AND p.brand = '델몬트' AND p.sale_unit_amount = 800 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '참치캔' AND p.name = '참치캔 150g' AND p.brand = '동원' AND p.sale_unit_amount = 150 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '참치캔' AND p.name = '참치캔 450g' AND p.brand = '사조' AND p.sale_unit_amount = 450 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '청국장' AND p.name = '청국장 200g' AND p.brand = '국내산' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '청국장' AND p.name = '청국장 400g' AND p.brand = '국내산 대' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '액젓' AND p.name = '액젓 500ml' AND p.brand = '까나리' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '액젓' AND p.name = '액젓 1L' AND p.brand = '멸치' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '케첩' AND p.name = '케첩 500g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '케첩' AND p.name = '케첩 1kg' AND p.brand = '하인즈' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '마요네즈' AND p.name = '마요네즈 500g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '마요네즈' AND p.name = '마요네즈 1kg' AND p.brand = '청정원' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '머스터드' AND p.name = '머스터드 200g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '머스터드' AND p.name = '머스터드 400g' AND p.brand = '하인즈' AND p.sale_unit_amount = 400 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '겨자' AND p.name = '겨자 50g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '겨자' AND p.name = '겨자 100g' AND p.brand = '청정원' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '굴소스' AND p.name = '굴소스 260ml' AND p.brand = '이금기' AND p.sale_unit_amount = 260 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '굴소스' AND p.name = '굴소스 510ml' AND p.brand = '이금기 대' AND p.sale_unit_amount = 510 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우스터소스' AND p.name = '우스터소스 150ml' AND p.brand = '오뚜기' AND p.sale_unit_amount = 150 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우스터소스' AND p.name = '우스터소스 300ml' AND p.brand = '하인즈' AND p.sale_unit_amount = 300 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '냉면육수' AND p.name = '냉면육수 340ml' AND p.brand = '풀무원' AND p.sale_unit_amount = 340 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '냉면육수' AND p.name = '냉면육수 1.02L' AND p.brand = '풀무원 3개입' AND p.sale_unit_amount = 1020 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '들기름' AND p.name = '들기름 320ml' AND p.brand = '오뚜기' AND p.sale_unit_amount = 320 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '들기름' AND p.name = '들기름 500ml' AND p.brand = '국산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고추기름' AND p.name = '고추기름 150ml' AND p.brand = '오뚜기' AND p.sale_unit_amount = 150 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고추기름' AND p.name = '고추기름 300ml' AND p.brand = '청정원' AND p.sale_unit_amount = 300 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식초' AND p.name = '식초 500ml' AND p.brand = '오뚜기' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식초' AND p.name = '식초 900ml' AND p.brand = '사과식초' AND p.sale_unit_amount = 900 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '맛술' AND p.name = '맛술 500ml' AND p.brand = '미림' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '맛술' AND p.name = '맛술 1L' AND p.brand = '미림 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '물엿' AND p.name = '물엿 700ml' AND p.brand = 'CJ' AND p.sale_unit_amount = 700 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '물엿' AND p.name = '물엿 1.2L' AND p.brand = '백설' AND p.sale_unit_amount = 1200 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통깨' AND p.name = '통깨 100g' AND p.brand = '국산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통깨' AND p.name = '통깨 200g' AND p.brand = '국산 대' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '들깻가루' AND p.name = '들깻가루 100g' AND p.brand = '국산' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '들깻가루' AND p.name = '들깻가루 200g' AND p.brand = '국산 대' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '페페론치노' AND p.name = '페페론치노 30g' AND p.brand = '수입산' AND p.sale_unit_amount = 30 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '페페론치노' AND p.name = '페페론치노 60g' AND p.brand = '수입산 대' AND p.sale_unit_amount = 60 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '월계수잎' AND p.name = '월계수잎 10g' AND p.brand = '수입산' AND p.sale_unit_amount = 10 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '월계수잎' AND p.name = '월계수잎 20g' AND p.brand = '수입산 대' AND p.sale_unit_amount = 20 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통후추' AND p.name = '통후추 50g' AND p.brand = '수입산' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통후추' AND p.name = '통후추 100g' AND p.brand = '수입산 대' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '시나몬' AND p.name = '시나몬 30g' AND p.brand = '수입산' AND p.sale_unit_amount = 30 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '시나몬' AND p.name = '시나몬 60g' AND p.brand = '수입산 대' AND p.sale_unit_amount = 60 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바닐라익스트랙' AND p.name = '바닐라익스트랙 50ml' AND p.brand = '수입산' AND p.sale_unit_amount = 50 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바닐라익스트랙' AND p.name = '바닐라익스트랙 100ml' AND p.brand = '수입산 대' AND p.sale_unit_amount = 100 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '사프란' AND p.name = '사프란 1g' AND p.brand = '스페인산' AND p.sale_unit_amount = 1 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '사프란' AND p.name = '사프란 2g' AND p.brand = '스페인산 대' AND p.sale_unit_amount = 2 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파프리카 가루' AND p.name = '파프리카 가루 50g' AND p.brand = '수입산' AND p.sale_unit_amount = 50 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파프리카 가루' AND p.name = '파프리카 가루 100g' AND p.brand = '수입산 대' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '슈가파우더' AND p.name = '슈가파우더 200g' AND p.brand = 'CJ' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '슈가파우더' AND p.name = '슈가파우더 500g' AND p.brand = '백설' AND p.sale_unit_amount = 500 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '화이트와인' AND p.name = '화이트와인 750ml' AND p.brand = '요리용' AND p.sale_unit_amount = 750 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '화이트와인' AND p.name = '화이트와인 1.5L' AND p.brand = '요리용 대' AND p.sale_unit_amount = 1500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '카레가루' AND p.name = '카레가루 100g' AND p.brand = '오뚜기' AND p.sale_unit_amount = 100 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '카레가루' AND p.name = '카레가루 200g' AND p.brand = '오뚜기 대' AND p.sale_unit_amount = 200 AND p.sale_unit = 'G' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '매실청' AND p.name = '매실청 500ml' AND p.brand = '청정원' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '매실청' AND p.name = '매실청 1L' AND p.brand = '청정원 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '메이플시럽' AND p.name = '메이플시럽 250ml' AND p.brand = '캐나다산' AND p.sale_unit_amount = 250 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '메이플시럽' AND p.name = '메이플시럽 500ml' AND p.brand = '캐나다산 대' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '배즙' AND p.name = '배즙 500ml' AND p.brand = '국내산' AND p.sale_unit_amount = 500 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.brand = '레시픽', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '배즙' AND p.name = '배즙 1L' AND p.brand = '국내산 대' AND p.sale_unit_amount = 1000 AND p.sale_unit = 'ML' AND p.brand <> '레시픽';

INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김치 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-001.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '김치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김치 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김치 900g', '레시픽', NULL, 900, 'G',
       '/images/products/ingredient-001.webp', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '김치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김치 900g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지고기 앞다리살 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-002.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지고기 앞다리살 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지고기 앞다리살 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-002.webp', 13900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지고기 앞다리살 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '두부 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-003.webp', 2300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '두부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '두부 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '두부 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-003.webp', 3100, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '두부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '두부 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대파 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-004.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '대파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대파 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대파 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-004.webp', 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '대파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대파 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양파 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-005.webp', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양파 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양파 1.5kg', '레시픽', NULL, 1500, 'G',
       '/images/products/ingredient-005.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양파 1.5kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진마늘 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-006.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '다진마늘'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진마늘 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진마늘 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-006.webp', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '다진마늘'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진마늘 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고춧가루 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-007.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '고춧가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고춧가루 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고춧가루 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-007.webp', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '고춧가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고춧가루 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '된장 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-008.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '된장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '된장 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '된장 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-008.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '된장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '된장 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '애호박 1개', '레시픽', NULL, 1, 'EA',
       '/images/products/ingredient-009.webp', 1500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '애호박'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '애호박 1개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '애호박 2개', '레시픽', NULL, 2, 'EA',
       '/images/products/ingredient-009.webp', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '애호박'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '애호박 2개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청양고추 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-010.webp', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '청양고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청양고추 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청양고추 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-010.webp', 3300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '청양고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청양고추 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국물용 멸치 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-011.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '국물용 멸치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국물용 멸치 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국물용 멸치 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-011.webp', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '국물용 멸치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국물용 멸치 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건미역 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-012.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '건미역'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건미역 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건미역 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-012.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '건미역'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건미역 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 국거리 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-013.webp', 17900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 국거리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 국거리 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 국거리 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-013.webp', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 국거리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 국거리 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국간장 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-014.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '국간장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국간장 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국간장 900ml', '레시픽', NULL, 900, 'ML',
       '/images/products/ingredient-014.webp', 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '국간장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국간장 900ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참기름 320ml', '레시픽', NULL, 320, 'ML',
       '/images/products/ingredient-015.webp', 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '참기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참기름 320ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참기름 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-015.webp', 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '참기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참기름 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추장 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-016.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '고추장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추장 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추장 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-016.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '고추장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추장 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '간장 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-017.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '간장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '간장 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '간장 900ml', '레시픽', NULL, 900, 'ML',
       '/images/products/ingredient-017.webp', 7500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '간장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '간장 900ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '설탕 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-018.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '설탕'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '설탕 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '설탕 3kg', '레시픽', NULL, 3000, 'G',
       '/images/products/ingredient-018.webp', 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '설탕'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '설탕 3kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '계란 10개', '레시픽', NULL, 10, 'EA',
       '/images/products/ingredient-019.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '계란'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '계란 10개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '계란 30개', '레시픽', NULL, 30, 'EA',
       '/images/products/ingredient-019.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '계란'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '계란 30개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당근 2개', '레시픽', NULL, 2, 'EA',
       '/images/products/ingredient-020.webp', 2200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '당근'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당근 2개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당근 4개', '레시픽', NULL, 4, 'EA',
       '/images/products/ingredient-020.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '당근'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당근 4개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소금 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-021.webp', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '소금'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소금 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소금 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-021.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '소금'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소금 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식용유 900ml', '레시픽', NULL, 900, 'ML',
       '/images/products/ingredient-022.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '식용유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식용유 900ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식용유 1.8L', '레시픽', NULL, 1800, 'ML',
       '/images/products/ingredient-022.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '식용유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식용유 1.8L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우젓 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-023.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우젓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우젓 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우젓 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-023.webp', 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우젓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우젓 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '스파게티면 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-024.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '스파게티면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '스파게티면 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '베이컨 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-025.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '베이컨'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '베이컨 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '베이컨 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-025.webp', 8200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '베이컨'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '베이컨 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파마산치즈 가루 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-026.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파마산치즈 가루 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파마산치즈 가루 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-026.webp', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파마산치즈 가루 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생크림 250ml', '레시픽', NULL, 250, 'ML',
       '/images/products/ingredient-027.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '생크림'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생크림 250ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생크림 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-027.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '생크림'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생크림 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후추 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-028.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '후추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후추 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후추 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-028.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '후추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후추 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '올리브유 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-029.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '올리브유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '올리브유 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '올리브유 1L', '레시픽', NULL, 1000, 'ML',
       '/images/products/ingredient-029.webp', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '올리브유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '올리브유 1L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토소스 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-030.webp', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '토마토소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토소스 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토소스 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-030.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '토마토소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토소스 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통마늘 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-031.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '통마늘'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통마늘 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통마늘 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-031.webp', 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '통마늘'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통마늘 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생강 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-032.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '생강'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생강 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생강 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-032.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '생강'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생강 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '홍고추 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-033.webp', 2000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '홍고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '홍고추 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '홍고추 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-033.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '홍고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '홍고추 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건고추 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-034.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '건고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건고추 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건고추 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-034.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '건고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건고추 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '꽈리고추 150g', '레시픽', NULL, 150, 'G',
       '/images/products/ingredient-035.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '꽈리고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '꽈리고추 150g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '꽈리고추 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-035.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '꽈리고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '꽈리고추 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '감자 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-036.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '감자'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '감자 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '감자 2kg', '레시픽', NULL, 2000, 'G',
       '/images/products/ingredient-036.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '감자'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '감자 2kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '무 800g', '레시픽', NULL, 800, 'G',
       '/images/products/ingredient-037.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '무'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '무 800g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '무 1.5kg', '레시픽', NULL, 1500, 'G',
       '/images/products/ingredient-037.webp', 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '무'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '무 1.5kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '콩나물 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-038.webp', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '콩나물'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '콩나물 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '콩나물 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-038.webp', 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '콩나물'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '콩나물 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '시금치 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-039.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '시금치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '시금치 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '시금치 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-039.webp', 6200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '시금치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '시금치 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '오이 2개', '레시픽', NULL, 2, 'EA',
       '/images/products/ingredient-040.webp', 2200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '오이'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '오이 2개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '오이 5개', '레시픽', NULL, 5, 'EA',
       '/images/products/ingredient-040.webp', 4800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '오이'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '오이 5개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양배추 800g', '레시픽', NULL, 800, 'G',
       '/images/products/ingredient-041.webp', 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양배추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양배추 800g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양배추 1.5kg', '레시픽', NULL, 1500, 'G',
       '/images/products/ingredient-041.webp', 4800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양배추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양배추 1.5kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고구마 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-042.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '고구마'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고구마 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고구마 2kg', '레시픽', NULL, 2000, 'G',
       '/images/products/ingredient-042.webp', 7200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '고구마'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고구마 2kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '깻잎 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-043.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '깻잎'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '깻잎 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '깻잎 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-043.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '깻잎'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '깻잎 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '표고버섯 150g', '레시픽', NULL, 150, 'G',
       '/images/products/ingredient-044.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '표고버섯'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '표고버섯 150g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '표고버섯 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-044.webp', 6200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '표고버섯'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '표고버섯 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양송이버섯 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-045.webp', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양송이버섯'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양송이버섯 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양송이버섯 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-045.webp', 5800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양송이버섯'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양송이버섯 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파프리카 2개', '레시픽', NULL, 2, 'EA',
       '/images/products/ingredient-046.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '파프리카'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파프리카 2개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파프리카 4개', '레시픽', NULL, 4, 'EA',
       '/images/products/ingredient-046.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '파프리카'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파프리카 4개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '피망 3개', '레시픽', NULL, 3, 'EA',
       '/images/products/ingredient-047.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '피망'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '피망 3개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '피망 6개', '레시픽', NULL, 6, 'EA',
       '/images/products/ingredient-047.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '피망'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '피망 6개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토 3개', '레시픽', NULL, 3, 'EA',
       '/images/products/ingredient-048.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '토마토'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토 3개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토 6개', '레시픽', NULL, 6, 'EA',
       '/images/products/ingredient-048.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '토마토'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토 6개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '방울토마토 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-049.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '방울토마토'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '방울토마토 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '방울토마토 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-049.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '방울토마토'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '방울토마토 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '단호박 1개', '레시픽', NULL, 1, 'EA',
       '/images/products/ingredient-050.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '단호박'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '단호박 1개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '단호박 2개', '레시픽', NULL, 2, 'EA',
       '/images/products/ingredient-050.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '단호박'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '단호박 2개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고사리 150g', '레시픽', NULL, 150, 'G',
       '/images/products/ingredient-051.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '고사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고사리 150g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고사리 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-051.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '고사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고사리 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '숙주 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-052.webp', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '숙주'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '숙주 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '숙주 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-052.webp', 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '숙주'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '숙주 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우거지 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-053.webp', 3000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '우거지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우거지 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우거지 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-053.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '우거지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우거지 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '곤드레 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-054.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '곤드레'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '곤드레 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '곤드레 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-054.webp', 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '곤드레'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '곤드레 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쑥갓 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-055.webp', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '쑥갓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쑥갓 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쑥갓 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-055.webp', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '쑥갓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쑥갓 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '미나리 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-056.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '미나리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '미나리 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '미나리 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-056.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '미나리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '미나리 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우엉 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-057.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '우엉'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우엉 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우엉 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-057.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '우엉'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우엉 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '밤 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-058.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '밤'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '밤 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '밤 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-058.webp', 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '밤'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '밤 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대추 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-059.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '대추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대추 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대추 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-059.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '대추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대추 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '인삼 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-060.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '인삼'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '인삼 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '인삼 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-060.webp', 10500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '인삼'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '인삼 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '셀러리 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-061.webp', 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '셀러리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '셀러리 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '셀러리 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-061.webp', 4800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '셀러리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '셀러리 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '로메인 150g', '레시픽', NULL, 150, 'G',
       '/images/products/ingredient-062.webp', 3000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '로메인'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '로메인 150g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '로메인 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-062.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '로메인'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '로메인 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양상추 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-063.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양상추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양상추 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양상추 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-063.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양상추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양상추 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '아보카도 2개', '레시픽', NULL, 2, 'EA',
       '/images/products/ingredient-064.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '아보카도'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '아보카도 2개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '아보카도 4개', '레시픽', NULL, 4, 'EA',
       '/images/products/ingredient-064.webp', 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '아보카도'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '아보카도 4개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '옥수수 340g', '레시픽', NULL, 340, 'G',
       '/images/products/ingredient-065.webp', 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '옥수수'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '옥수수 340g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '옥수수 680g', '레시픽', NULL, 680, 'G',
       '/images/products/ingredient-065.webp', 5000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '옥수수'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '옥수수 680g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '레몬 3개', '레시픽', NULL, 3, 'EA',
       '/images/products/ingredient-066.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '레몬'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '레몬 3개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '레몬 6개', '레시픽', NULL, 6, 'EA',
       '/images/products/ingredient-066.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '레몬'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '레몬 6개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바질 20g', '레시픽', NULL, 20, 'G',
       '/images/products/ingredient-067.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '바질'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바질 20g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바질 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-067.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '바질'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바질 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파슬리 20g', '레시픽', NULL, 20, 'G',
       '/images/products/ingredient-068.webp', 2000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '파슬리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파슬리 20g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파슬리 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-068.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '파슬리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파슬리 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '로즈마리 20g', '레시픽', NULL, 20, 'G',
       '/images/products/ingredient-069.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '로즈마리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '로즈마리 20g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '로즈마리 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-069.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '로즈마리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '로즈마리 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '피클 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-070.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '피클'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '피클 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '피클 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-070.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '피클'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '피클 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '견과류 150g', '레시픽', NULL, 150, 'G',
       '/images/products/ingredient-071.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '견과류'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '견과류 150g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '견과류 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-071.webp', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '견과류'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '견과류 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쪽파 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-072.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '쪽파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쪽파 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쪽파 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-072.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '쪽파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쪽파 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진 돼지고기 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-073.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '다진 돼지고기'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진 돼지고기 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진 돼지고기 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-073.webp', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '다진 돼지고기'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진 돼지고기 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진 소고기 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-074.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '다진 소고기'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진 소고기 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진 소고기 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-074.webp', 24900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '다진 소고기'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진 소고기 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 불고기용 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-075.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 불고기용'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 불고기용 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 불고기용 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-075.webp', 29900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 불고기용'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 불고기용 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소갈비 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-076.webp', 24900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소갈비'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소갈비 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소갈비 1.5kg', '레시픽', NULL, 1500, 'G',
       '/images/products/ingredient-076.webp', 69900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소갈비'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소갈비 1.5kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 홍두깨살 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-077.webp', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 홍두깨살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 홍두깨살 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 홍두깨살 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-077.webp', 49900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 홍두깨살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 홍두깨살 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 스테이크용 250g', '레시픽', NULL, 250, 'G',
       '/images/products/ingredient-078.webp', 14900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 스테이크용'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 스테이크용 250g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 스테이크용 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-078.webp', 39900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 스테이크용'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 스테이크용 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지 등뼈 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-079.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '돼지 등뼈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지 등뼈 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지 등뼈 2kg', '레시픽', NULL, 2000, 'G',
       '/images/products/ingredient-079.webp', 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '돼지 등뼈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지 등뼈 2kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭볶음탕용 닭 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-080.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭볶음탕용 닭'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭볶음탕용 닭 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭볶음탕용 닭 1.5kg', '레시픽', NULL, 1500, 'G',
       '/images/products/ingredient-080.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭볶음탕용 닭'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭볶음탕용 닭 1.5kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭다리살 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-081.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭다리살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭다리살 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭다리살 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-081.webp', 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭다리살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭다리살 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭가슴살 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-082.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭가슴살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭가슴살 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭가슴살 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-082.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭가슴살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭가슴살 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '영계 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-083.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '영계'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '영계 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '영계 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-083.webp', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '영계'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '영계 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '햄 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-084.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '햄'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '햄 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '햄 340g', '레시픽', NULL, 340, 'G',
       '/images/products/ingredient-084.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '햄'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '햄 340g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소시지 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-085.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소시지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소시지 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소시지 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-085.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소시지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소시지 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '동태 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-086.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '동태'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '동태 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '동태 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-086.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '동태'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '동태 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '오징어 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-087.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '오징어'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '오징어 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '오징어 800g', '레시픽', NULL, 800, 'G',
       '/images/products/ingredient-087.webp', 12500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '오징어'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '오징어 800g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '낙지 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-088.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '낙지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '낙지 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '낙지 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-088.webp', 18000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '낙지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '낙지 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쭈꾸미 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-089.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '쭈꾸미'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쭈꾸미 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쭈꾸미 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-089.webp', 17900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '쭈꾸미'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쭈꾸미 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '북어채 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-090.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '북어채'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '북어채 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '북어채 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-090.webp', 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '북어채'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '북어채 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고등어 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-091.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '고등어'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고등어 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고등어 800g', '레시픽', NULL, 800, 'G',
       '/images/products/ingredient-091.webp', 12500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '고등어'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고등어 800g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-092.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-092.webp', 13900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바지락 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-093.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '바지락'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바지락 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바지락 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-093.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '바지락'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바지락 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '홍합 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-094.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '홍합'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '홍합 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '홍합 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-094.webp', 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '홍합'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '홍합 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '어묵 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-095.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '어묵'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '어묵 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '어묵 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-095.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '어묵'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '어묵 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '맛살 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-096.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '맛살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '맛살 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '맛살 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-096.webp', 5000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '맛살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '맛살 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '잔멸치 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-097.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '잔멸치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '잔멸치 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '잔멸치 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-097.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '잔멸치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '잔멸치 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다시마 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-098.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '다시마'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다시마 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다시마 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-098.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '다시마'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다시마 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김가루 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-099.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '김가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김가루 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김가루 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-099.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '김가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김가루 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김밥김 10개', '레시픽', NULL, 10, 'EA',
       '/images/products/ingredient-100.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '김밥김'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김밥김 10개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김밥김 20개', '레시픽', NULL, 20, 'EA',
       '/images/products/ingredient-100.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '김밥김'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김밥김 20개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '안초비 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-101.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '안초비'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '안초비 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '안초비 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-101.webp', 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '안초비'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '안초비 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '순두부 350g', '레시픽', NULL, 350, 'G',
       '/images/products/ingredient-102.webp', 2300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '순두부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '순두부 350g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '순두부 700g', '레시픽', NULL, 700, 'G',
       '/images/products/ingredient-102.webp', 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '순두부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '순두부 700g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우유 900ml', '레시픽', NULL, 900, 'ML',
       '/images/products/ingredient-103.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '우유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우유 900ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우유 1.8L', '레시픽', NULL, 1800, 'ML',
       '/images/products/ingredient-103.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '우유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우유 1.8L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '버터 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-104.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '버터'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '버터 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '버터 450g', '레시픽', NULL, 450, 'G',
       '/images/products/ingredient-104.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '버터'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '버터 450g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '모차렐라 치즈 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-105.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '모차렐라 치즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '모차렐라 치즈 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '모차렐라 치즈 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-105.webp', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '모차렐라 치즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '모차렐라 치즈 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '체다 치즈 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-106.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '체다 치즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '체다 치즈 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '체다 치즈 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-106.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '체다 치즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '체다 치즈 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '요거트 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-107.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '요거트'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '요거트 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '요거트 800g', '레시픽', NULL, 800, 'G',
       '/images/products/ingredient-107.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '요거트'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '요거트 800g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쌀 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-108.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '쌀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쌀 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쌀 4kg', '레시픽', NULL, 4000, 'G',
       '/images/products/ingredient-108.webp', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '쌀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쌀 4kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '찹쌀 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-109.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '찹쌀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '찹쌀 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '찹쌀 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-109.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '찹쌀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '찹쌀 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '백태 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-110.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '백태'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '백태 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '백태 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-110.webp', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '백태'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '백태 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '밀가루 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-111.webp', 2400, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '밀가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '밀가루 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '밀가루 2.5kg', '레시픽', NULL, 2500, 'G',
       '/images/products/ingredient-111.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '밀가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '밀가루 2.5kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '부침가루 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-112.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '부침가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '부침가루 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '부침가루 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-112.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '부침가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '부침가루 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '빵가루 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-113.webp', 1900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '빵가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '빵가루 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '빵가루 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-113.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '빵가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '빵가루 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '전분 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-114.webp', 2000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '전분'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '전분 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '전분 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-114.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '전분'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '전분 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소면 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-115.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '소면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소면 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소면 900g', '레시픽', NULL, 900, 'G',
       '/images/products/ingredient-115.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '소면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소면 900g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '칼국수면 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-116.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '칼국수면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '칼국수면 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '칼국수면 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-116.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '칼국수면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '칼국수면 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '냉면사리 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-117.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '냉면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '냉면사리 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '냉면사리 800g', '레시픽', NULL, 800, 'G',
       '/images/products/ingredient-117.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '냉면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '냉면사리 800g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쫄면사리 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-118.webp', 3000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '쫄면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쫄면사리 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쫄면사리 800g', '레시픽', NULL, 800, 'G',
       '/images/products/ingredient-118.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '쫄면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쫄면사리 800g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '메밀면 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-119.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '메밀면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '메밀면 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '메밀면 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-119.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '메밀면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '메밀면 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당면 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-120.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '당면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당면 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당면 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-120.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '당면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당면 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '라면사리 420g', '레시픽', NULL, 420, 'G',
       '/images/products/ingredient-121.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '라면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '라면사리 420g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '라면사리 840g', '레시픽', NULL, 840, 'G',
       '/images/products/ingredient-121.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '라면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '라면사리 840g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '펜네 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-122.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '펜네'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '펜네 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '펜네 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-122.webp', 7200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '펜네'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '펜네 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '마카로니 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-123.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '마카로니'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '마카로니 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '마카로니 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-123.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '마카로니'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '마카로니 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '라자냐면 250g', '레시픽', NULL, 250, 'G',
       '/images/products/ingredient-124.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '라자냐면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '라자냐면 250g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '라자냐면 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-124.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '라자냐면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '라자냐면 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '떡국떡 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-125.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '떡국떡'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '떡국떡 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '떡국떡 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-125.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '떡국떡'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '떡국떡 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '떡볶이떡 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-126.webp', 3000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '떡볶이떡'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '떡볶이떡 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '떡볶이떡 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-126.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '떡볶이떡'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '떡볶이떡 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식빵 450g', '레시픽', NULL, 450, 'G',
       '/images/products/ingredient-127.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '식빵'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식빵 450g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식빵 900g', '레시픽', NULL, 900, 'G',
       '/images/products/ingredient-127.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '식빵'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식빵 900g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바게트 250g', '레시픽', NULL, 250, 'G',
       '/images/products/ingredient-128.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '바게트'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바게트 250g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바게트 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-128.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '바게트'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바게트 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '번 4개', '레시픽', NULL, 4, 'EA',
       '/images/products/ingredient-129.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '번'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '번 4개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '번 8개', '레시픽', NULL, 8, 'EA',
       '/images/products/ingredient-129.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '번'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '번 8개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '잉글리시 머핀 4개', '레시픽', NULL, 4, 'EA',
       '/images/products/ingredient-130.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '잉글리시 머핀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '잉글리시 머핀 4개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '잉글리시 머핀 8개', '레시픽', NULL, 8, 'EA',
       '/images/products/ingredient-130.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '잉글리시 머핀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '잉글리시 머핀 8개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '또띠아 8개', '레시픽', NULL, 8, 'EA',
       '/images/products/ingredient-131.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '또띠아'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '또띠아 8개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '또띠아 16개', '레시픽', NULL, 16, 'EA',
       '/images/products/ingredient-131.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '또띠아'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '또띠아 16개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '유부 20개', '레시픽', NULL, 20, 'EA',
       '/images/products/ingredient-132.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '유부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '유부 20개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '유부 40개', '레시픽', NULL, 40, 'EA',
       '/images/products/ingredient-132.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '유부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '유부 40개' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후레이크 40g', '레시픽', NULL, 40, 'G',
       '/images/products/ingredient-133.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '후레이크'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후레이크 40g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후레이크 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-133.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '후레이크'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후레이크 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '단무지 300g', '레시픽', NULL, 300, 'G',
       '/images/products/ingredient-134.webp', 1500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '단무지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '단무지 300g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '단무지 600g', '레시픽', NULL, 600, 'G',
       '/images/products/ingredient-134.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '단무지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '단무지 600g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통조림 콩 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-135.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '통조림 콩'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통조림 콩 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통조림 콩 800g', '레시픽', NULL, 800, 'G',
       '/images/products/ingredient-135.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '통조림 콩'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통조림 콩 800g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참치캔 150g', '레시픽', NULL, 150, 'G',
       '/images/products/ingredient-136.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '참치캔'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참치캔 150g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참치캔 450g', '레시픽', NULL, 450, 'G',
       '/images/products/ingredient-136.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '참치캔'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참치캔 450g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청국장 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-137.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '청국장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청국장 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청국장 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-137.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '청국장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청국장 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '액젓 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-138.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '액젓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '액젓 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '액젓 1L', '레시픽', NULL, 1000, 'ML',
       '/images/products/ingredient-138.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '액젓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '액젓 1L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '케첩 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-139.webp', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '케첩'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '케첩 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '케첩 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-139.webp', 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '케첩'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '케첩 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '마요네즈 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-140.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '마요네즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '마요네즈 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '마요네즈 1kg', '레시픽', NULL, 1000, 'G',
       '/images/products/ingredient-140.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '마요네즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '마요네즈 1kg' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '머스터드 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-141.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '머스터드'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '머스터드 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '머스터드 400g', '레시픽', NULL, 400, 'G',
       '/images/products/ingredient-141.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '머스터드'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '머스터드 400g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '겨자 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-142.webp', 1900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '겨자'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '겨자 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '겨자 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-142.webp', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '겨자'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '겨자 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '굴소스 260ml', '레시픽', NULL, 260, 'ML',
       '/images/products/ingredient-143.webp', 3800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '굴소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '굴소스 260ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '굴소스 510ml', '레시픽', NULL, 510, 'ML',
       '/images/products/ingredient-143.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '굴소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '굴소스 510ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우스터소스 150ml', '레시픽', NULL, 150, 'ML',
       '/images/products/ingredient-144.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '우스터소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우스터소스 150ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우스터소스 300ml', '레시픽', NULL, 300, 'ML',
       '/images/products/ingredient-144.webp', 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '우스터소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우스터소스 300ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '냉면육수 340ml', '레시픽', NULL, 340, 'ML',
       '/images/products/ingredient-145.webp', 1500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '냉면육수'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '냉면육수 340ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '냉면육수 1.02L', '레시픽', NULL, 1020, 'ML',
       '/images/products/ingredient-145.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '냉면육수'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '냉면육수 1.02L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '들기름 320ml', '레시픽', NULL, 320, 'ML',
       '/images/products/ingredient-146.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '들기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '들기름 320ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '들기름 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-146.webp', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '들기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '들기름 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추기름 150ml', '레시픽', NULL, 150, 'ML',
       '/images/products/ingredient-147.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '고추기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추기름 150ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추기름 300ml', '레시픽', NULL, 300, 'ML',
       '/images/products/ingredient-147.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '고추기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추기름 300ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식초 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-148.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '식초'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식초 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식초 900ml', '레시픽', NULL, 900, 'ML',
       '/images/products/ingredient-148.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '식초'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식초 900ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '맛술 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-149.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '맛술'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '맛술 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '맛술 1L', '레시픽', NULL, 1000, 'ML',
       '/images/products/ingredient-149.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '맛술'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '맛술 1L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '물엿 700ml', '레시픽', NULL, 700, 'ML',
       '/images/products/ingredient-150.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '물엿'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '물엿 700ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '물엿 1.2L', '레시픽', NULL, 1200, 'ML',
       '/images/products/ingredient-150.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '물엿'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '물엿 1.2L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통깨 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-151.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '통깨'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통깨 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통깨 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-151.webp', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '통깨'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통깨 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '들깻가루 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-152.webp', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '들깻가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '들깻가루 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '들깻가루 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-152.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '들깻가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '들깻가루 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '페페론치노 30g', '레시픽', NULL, 30, 'G',
       '/images/products/ingredient-153.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '페페론치노'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '페페론치노 30g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '페페론치노 60g', '레시픽', NULL, 60, 'G',
       '/images/products/ingredient-153.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '페페론치노'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '페페론치노 60g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '월계수잎 10g', '레시픽', NULL, 10, 'G',
       '/images/products/ingredient-154.webp', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '월계수잎'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '월계수잎 10g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '월계수잎 20g', '레시픽', NULL, 20, 'G',
       '/images/products/ingredient-154.webp', 4000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '월계수잎'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '월계수잎 20g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통후추 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-155.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '통후추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통후추 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통후추 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-155.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '통후추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통후추 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '시나몬 30g', '레시픽', NULL, 30, 'G',
       '/images/products/ingredient-156.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '시나몬'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '시나몬 30g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '시나몬 60g', '레시픽', NULL, 60, 'G',
       '/images/products/ingredient-156.webp', 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '시나몬'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '시나몬 60g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바닐라익스트랙 50ml', '레시픽', NULL, 50, 'ML',
       '/images/products/ingredient-157.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '바닐라익스트랙'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바닐라익스트랙 50ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바닐라익스트랙 100ml', '레시픽', NULL, 100, 'ML',
       '/images/products/ingredient-157.webp', 11000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '바닐라익스트랙'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바닐라익스트랙 100ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '사프란 1g', '레시픽', NULL, 1, 'G',
       '/images/products/ingredient-158.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '사프란'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '사프란 1g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '사프란 2g', '레시픽', NULL, 2, 'G',
       '/images/products/ingredient-158.webp', 17900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '사프란'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '사프란 2g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파프리카 가루 50g', '레시픽', NULL, 50, 'G',
       '/images/products/ingredient-159.webp', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '파프리카 가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파프리카 가루 50g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파프리카 가루 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-159.webp', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '파프리카 가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파프리카 가루 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '슈가파우더 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-160.webp', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '슈가파우더'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '슈가파우더 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '슈가파우더 500g', '레시픽', NULL, 500, 'G',
       '/images/products/ingredient-160.webp', 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '슈가파우더'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '슈가파우더 500g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '화이트와인 750ml', '레시픽', NULL, 750, 'ML',
       '/images/products/ingredient-161.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '화이트와인'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '화이트와인 750ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '화이트와인 1.5L', '레시픽', NULL, 1500, 'ML',
       '/images/products/ingredient-161.webp', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '화이트와인'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '화이트와인 1.5L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '카레가루 100g', '레시픽', NULL, 100, 'G',
       '/images/products/ingredient-162.webp', 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '카레가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '카레가루 100g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '카레가루 200g', '레시픽', NULL, 200, 'G',
       '/images/products/ingredient-162.webp', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '카레가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '카레가루 200g' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '매실청 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-163.webp', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '매실청'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '매실청 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '매실청 1L', '레시픽', NULL, 1000, 'ML',
       '/images/products/ingredient-163.webp', 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '매실청'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '매실청 1L' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '메이플시럽 250ml', '레시픽', NULL, 250, 'ML',
       '/images/products/ingredient-164.webp', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '메이플시럽'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '메이플시럽 250ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '메이플시럽 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-164.webp', 16900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '메이플시럽'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '메이플시럽 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '배즙 500ml', '레시픽', NULL, 500, 'ML',
       '/images/products/ingredient-165.webp', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '배즙'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '배즙 500ml' AND p.brand = '레시픽');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '배즙 1L', '레시픽', NULL, 1000, 'ML',
       '/images/products/ingredient-165.webp', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '배즙'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '배즙 1L' AND p.brand = '레시픽');

-- 기존 시연 상품의 이미지를 갱신한다. 가격·재고·판매 상태는 변경하지 않는다.
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-001.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김치' AND p.name = '김치 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-001.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-001.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김치' AND p.name = '김치 900g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-001.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-002.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '돼지고기 앞다리살' AND p.name = '돼지고기 앞다리살 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-002.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-002.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '돼지고기 앞다리살' AND p.name = '돼지고기 앞다리살 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-002.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-003.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '두부' AND p.name = '두부 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-003.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-003.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '두부' AND p.name = '두부 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-003.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-004.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '대파' AND p.name = '대파 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-004.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-004.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '대파' AND p.name = '대파 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-004.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-005.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양파' AND p.name = '양파 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-005.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-005.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양파' AND p.name = '양파 1.5kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-005.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-006.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진마늘' AND p.name = '다진마늘 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-006.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-006.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진마늘' AND p.name = '다진마늘 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-006.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-007.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고춧가루' AND p.name = '고춧가루 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-007.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-007.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고춧가루' AND p.name = '고춧가루 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-007.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-008.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '된장' AND p.name = '된장 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-008.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-008.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '된장' AND p.name = '된장 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-008.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-009.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '애호박' AND p.name = '애호박 1개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-009.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-009.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '애호박' AND p.name = '애호박 2개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-009.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-010.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '청양고추' AND p.name = '청양고추 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-010.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-010.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '청양고추' AND p.name = '청양고추 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-010.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-011.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '국물용 멸치' AND p.name = '국물용 멸치 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-011.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-011.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '국물용 멸치' AND p.name = '국물용 멸치 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-011.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-012.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '건미역' AND p.name = '건미역 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-012.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-012.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '건미역' AND p.name = '건미역 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-012.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-013.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 국거리' AND p.name = '소고기 국거리 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-013.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-013.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 국거리' AND p.name = '소고기 국거리 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-013.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-014.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '국간장' AND p.name = '국간장 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-014.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-014.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '국간장' AND p.name = '국간장 900ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-014.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-015.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '참기름' AND p.name = '참기름 320ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-015.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-015.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '참기름' AND p.name = '참기름 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-015.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-016.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고추장' AND p.name = '고추장 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-016.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-016.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고추장' AND p.name = '고추장 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-016.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-017.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '간장' AND p.name = '간장 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-017.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-017.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '간장' AND p.name = '간장 900ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-017.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-018.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '설탕' AND p.name = '설탕 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-018.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-018.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '설탕' AND p.name = '설탕 3kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-018.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-019.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '계란' AND p.name = '계란 10개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-019.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-019.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '계란' AND p.name = '계란 30개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-019.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-020.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '당근' AND p.name = '당근 2개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-020.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-020.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '당근' AND p.name = '당근 4개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-020.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-021.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소금' AND p.name = '소금 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-021.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-021.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소금' AND p.name = '소금 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-021.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-022.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식용유' AND p.name = '식용유 900ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-022.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-022.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식용유' AND p.name = '식용유 1.8L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-022.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-023.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '새우젓' AND p.name = '새우젓 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-023.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-023.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '새우젓' AND p.name = '새우젓 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-023.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-024.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '스파게티면' AND p.name = '스파게티면 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-024.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-025.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '베이컨' AND p.name = '베이컨 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-025.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-025.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '베이컨' AND p.name = '베이컨 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-025.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-026.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파마산치즈 가루' AND p.name = '파마산치즈 가루 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-026.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-026.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파마산치즈 가루' AND p.name = '파마산치즈 가루 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-026.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-027.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '생크림' AND p.name = '생크림 250ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-027.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-027.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '생크림' AND p.name = '생크림 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-027.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-028.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '후추' AND p.name = '후추 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-028.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-028.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '후추' AND p.name = '후추 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-028.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-029.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '올리브유' AND p.name = '올리브유 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-029.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-029.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '올리브유' AND p.name = '올리브유 1L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-029.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-030.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '토마토소스' AND p.name = '토마토소스 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-030.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-030.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '토마토소스' AND p.name = '토마토소스 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-030.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-031.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통마늘' AND p.name = '통마늘 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-031.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-031.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통마늘' AND p.name = '통마늘 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-031.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-032.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '생강' AND p.name = '생강 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-032.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-032.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '생강' AND p.name = '생강 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-032.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-033.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '홍고추' AND p.name = '홍고추 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-033.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-033.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '홍고추' AND p.name = '홍고추 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-033.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-034.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '건고추' AND p.name = '건고추 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-034.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-034.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '건고추' AND p.name = '건고추 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-034.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-035.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '꽈리고추' AND p.name = '꽈리고추 150g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-035.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-035.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '꽈리고추' AND p.name = '꽈리고추 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-035.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-036.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '감자' AND p.name = '감자 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-036.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-036.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '감자' AND p.name = '감자 2kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-036.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-037.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '무' AND p.name = '무 800g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-037.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-037.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '무' AND p.name = '무 1.5kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-037.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-038.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '콩나물' AND p.name = '콩나물 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-038.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-038.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '콩나물' AND p.name = '콩나물 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-038.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-039.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '시금치' AND p.name = '시금치 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-039.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-039.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '시금치' AND p.name = '시금치 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-039.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-040.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '오이' AND p.name = '오이 2개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-040.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-040.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '오이' AND p.name = '오이 5개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-040.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-041.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양배추' AND p.name = '양배추 800g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-041.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-041.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양배추' AND p.name = '양배추 1.5kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-041.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-042.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고구마' AND p.name = '고구마 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-042.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-042.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고구마' AND p.name = '고구마 2kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-042.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-043.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '깻잎' AND p.name = '깻잎 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-043.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-043.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '깻잎' AND p.name = '깻잎 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-043.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-044.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '표고버섯' AND p.name = '표고버섯 150g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-044.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-044.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '표고버섯' AND p.name = '표고버섯 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-044.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-045.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양송이버섯' AND p.name = '양송이버섯 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-045.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-045.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양송이버섯' AND p.name = '양송이버섯 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-045.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-046.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파프리카' AND p.name = '파프리카 2개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-046.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-046.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파프리카' AND p.name = '파프리카 4개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-046.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-047.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '피망' AND p.name = '피망 3개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-047.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-047.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '피망' AND p.name = '피망 6개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-047.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-048.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '토마토' AND p.name = '토마토 3개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-048.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-048.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '토마토' AND p.name = '토마토 6개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-048.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-049.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '방울토마토' AND p.name = '방울토마토 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-049.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-049.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '방울토마토' AND p.name = '방울토마토 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-049.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-050.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '단호박' AND p.name = '단호박 1개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-050.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-050.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '단호박' AND p.name = '단호박 2개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-050.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-051.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고사리' AND p.name = '고사리 150g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-051.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-051.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고사리' AND p.name = '고사리 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-051.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-052.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '숙주' AND p.name = '숙주 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-052.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-052.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '숙주' AND p.name = '숙주 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-052.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-053.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우거지' AND p.name = '우거지 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-053.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-053.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우거지' AND p.name = '우거지 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-053.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-054.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '곤드레' AND p.name = '곤드레 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-054.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-054.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '곤드레' AND p.name = '곤드레 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-054.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-055.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쑥갓' AND p.name = '쑥갓 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-055.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-055.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쑥갓' AND p.name = '쑥갓 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-055.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-056.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '미나리' AND p.name = '미나리 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-056.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-056.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '미나리' AND p.name = '미나리 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-056.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-057.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우엉' AND p.name = '우엉 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-057.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-057.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우엉' AND p.name = '우엉 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-057.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-058.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '밤' AND p.name = '밤 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-058.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-058.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '밤' AND p.name = '밤 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-058.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-059.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '대추' AND p.name = '대추 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-059.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-059.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '대추' AND p.name = '대추 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-059.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-060.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '인삼' AND p.name = '인삼 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-060.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-060.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '인삼' AND p.name = '인삼 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-060.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-061.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '셀러리' AND p.name = '셀러리 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-061.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-061.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '셀러리' AND p.name = '셀러리 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-061.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-062.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '로메인' AND p.name = '로메인 150g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-062.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-062.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '로메인' AND p.name = '로메인 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-062.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-063.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양상추' AND p.name = '양상추 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-063.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-063.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '양상추' AND p.name = '양상추 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-063.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-064.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '아보카도' AND p.name = '아보카도 2개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-064.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-064.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '아보카도' AND p.name = '아보카도 4개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-064.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-065.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '옥수수' AND p.name = '옥수수 340g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-065.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-065.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '옥수수' AND p.name = '옥수수 680g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-065.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-066.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '레몬' AND p.name = '레몬 3개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-066.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-066.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '레몬' AND p.name = '레몬 6개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-066.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-067.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바질' AND p.name = '바질 20g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-067.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-067.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바질' AND p.name = '바질 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-067.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-068.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파슬리' AND p.name = '파슬리 20g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-068.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-068.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파슬리' AND p.name = '파슬리 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-068.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-069.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '로즈마리' AND p.name = '로즈마리 20g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-069.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-069.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '로즈마리' AND p.name = '로즈마리 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-069.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-070.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '피클' AND p.name = '피클 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-070.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-070.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '피클' AND p.name = '피클 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-070.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-071.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '견과류' AND p.name = '견과류 150g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-071.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-071.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '견과류' AND p.name = '견과류 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-071.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-072.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쪽파' AND p.name = '쪽파 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-072.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-072.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쪽파' AND p.name = '쪽파 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-072.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-073.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진 돼지고기' AND p.name = '다진 돼지고기 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-073.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-073.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진 돼지고기' AND p.name = '다진 돼지고기 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-073.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-074.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진 소고기' AND p.name = '다진 소고기 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-074.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-074.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다진 소고기' AND p.name = '다진 소고기 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-074.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-075.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 불고기용' AND p.name = '소고기 불고기용 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-075.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-075.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 불고기용' AND p.name = '소고기 불고기용 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-075.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-076.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소갈비' AND p.name = '소갈비 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-076.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-076.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소갈비' AND p.name = '소갈비 1.5kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-076.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-077.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 홍두깨살' AND p.name = '소고기 홍두깨살 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-077.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-077.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 홍두깨살' AND p.name = '소고기 홍두깨살 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-077.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-078.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 스테이크용' AND p.name = '소고기 스테이크용 250g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-078.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-078.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소고기 스테이크용' AND p.name = '소고기 스테이크용 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-078.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-079.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '돼지 등뼈' AND p.name = '돼지 등뼈 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-079.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-079.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '돼지 등뼈' AND p.name = '돼지 등뼈 2kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-079.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-080.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭볶음탕용 닭' AND p.name = '닭볶음탕용 닭 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-080.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-080.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭볶음탕용 닭' AND p.name = '닭볶음탕용 닭 1.5kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-080.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-081.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭다리살' AND p.name = '닭다리살 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-081.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-081.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭다리살' AND p.name = '닭다리살 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-081.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-082.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭가슴살' AND p.name = '닭가슴살 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-082.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-082.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '닭가슴살' AND p.name = '닭가슴살 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-082.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-083.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '영계' AND p.name = '영계 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-083.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-083.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '영계' AND p.name = '영계 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-083.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-084.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '햄' AND p.name = '햄 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-084.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-084.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '햄' AND p.name = '햄 340g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-084.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-085.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소시지' AND p.name = '소시지 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-085.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-085.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소시지' AND p.name = '소시지 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-085.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-086.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '동태' AND p.name = '동태 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-086.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-086.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '동태' AND p.name = '동태 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-086.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-087.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '오징어' AND p.name = '오징어 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-087.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-087.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '오징어' AND p.name = '오징어 800g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-087.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-088.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '낙지' AND p.name = '낙지 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-088.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-088.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '낙지' AND p.name = '낙지 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-088.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-089.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쭈꾸미' AND p.name = '쭈꾸미 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-089.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-089.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쭈꾸미' AND p.name = '쭈꾸미 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-089.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-090.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '북어채' AND p.name = '북어채 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-090.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-090.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '북어채' AND p.name = '북어채 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-090.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-091.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고등어' AND p.name = '고등어 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-091.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-091.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고등어' AND p.name = '고등어 800g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-091.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-092.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '새우' AND p.name = '새우 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-092.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-092.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '새우' AND p.name = '새우 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-092.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-093.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바지락' AND p.name = '바지락 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-093.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-093.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바지락' AND p.name = '바지락 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-093.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-094.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '홍합' AND p.name = '홍합 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-094.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-094.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '홍합' AND p.name = '홍합 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-094.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-095.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '어묵' AND p.name = '어묵 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-095.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-095.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '어묵' AND p.name = '어묵 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-095.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-096.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '맛살' AND p.name = '맛살 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-096.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-096.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '맛살' AND p.name = '맛살 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-096.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-097.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '잔멸치' AND p.name = '잔멸치 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-097.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-097.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '잔멸치' AND p.name = '잔멸치 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-097.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-098.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다시마' AND p.name = '다시마 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-098.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-098.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '다시마' AND p.name = '다시마 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-098.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-099.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김가루' AND p.name = '김가루 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-099.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-099.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김가루' AND p.name = '김가루 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-099.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-100.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김밥김' AND p.name = '김밥김 10개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-100.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-100.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '김밥김' AND p.name = '김밥김 20개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-100.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-101.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '안초비' AND p.name = '안초비 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-101.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-101.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '안초비' AND p.name = '안초비 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-101.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-102.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '순두부' AND p.name = '순두부 350g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-102.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-102.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '순두부' AND p.name = '순두부 700g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-102.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-103.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우유' AND p.name = '우유 900ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-103.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-103.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우유' AND p.name = '우유 1.8L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-103.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-104.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '버터' AND p.name = '버터 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-104.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-104.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '버터' AND p.name = '버터 450g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-104.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-105.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '모차렐라 치즈' AND p.name = '모차렐라 치즈 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-105.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-105.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '모차렐라 치즈' AND p.name = '모차렐라 치즈 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-105.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-106.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '체다 치즈' AND p.name = '체다 치즈 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-106.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-106.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '체다 치즈' AND p.name = '체다 치즈 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-106.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-107.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '요거트' AND p.name = '요거트 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-107.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-107.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '요거트' AND p.name = '요거트 800g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-107.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-108.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쌀' AND p.name = '쌀 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-108.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-108.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쌀' AND p.name = '쌀 4kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-108.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-109.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '찹쌀' AND p.name = '찹쌀 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-109.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-109.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '찹쌀' AND p.name = '찹쌀 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-109.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-110.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '백태' AND p.name = '백태 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-110.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-110.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '백태' AND p.name = '백태 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-110.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-111.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '밀가루' AND p.name = '밀가루 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-111.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-111.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '밀가루' AND p.name = '밀가루 2.5kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-111.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-112.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '부침가루' AND p.name = '부침가루 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-112.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-112.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '부침가루' AND p.name = '부침가루 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-112.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-113.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '빵가루' AND p.name = '빵가루 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-113.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-113.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '빵가루' AND p.name = '빵가루 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-113.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-114.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '전분' AND p.name = '전분 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-114.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-114.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '전분' AND p.name = '전분 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-114.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-115.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소면' AND p.name = '소면 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-115.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-115.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '소면' AND p.name = '소면 900g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-115.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-116.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '칼국수면' AND p.name = '칼국수면 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-116.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-116.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '칼국수면' AND p.name = '칼국수면 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-116.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-117.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '냉면사리' AND p.name = '냉면사리 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-117.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-117.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '냉면사리' AND p.name = '냉면사리 800g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-117.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-118.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쫄면사리' AND p.name = '쫄면사리 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-118.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-118.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '쫄면사리' AND p.name = '쫄면사리 800g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-118.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-119.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '메밀면' AND p.name = '메밀면 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-119.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-119.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '메밀면' AND p.name = '메밀면 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-119.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-120.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '당면' AND p.name = '당면 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-120.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-120.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '당면' AND p.name = '당면 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-120.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-121.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '라면사리' AND p.name = '라면사리 420g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-121.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-121.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '라면사리' AND p.name = '라면사리 840g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-121.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-122.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '펜네' AND p.name = '펜네 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-122.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-122.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '펜네' AND p.name = '펜네 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-122.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-123.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '마카로니' AND p.name = '마카로니 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-123.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-123.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '마카로니' AND p.name = '마카로니 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-123.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-124.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '라자냐면' AND p.name = '라자냐면 250g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-124.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-124.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '라자냐면' AND p.name = '라자냐면 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-124.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-125.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '떡국떡' AND p.name = '떡국떡 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-125.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-125.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '떡국떡' AND p.name = '떡국떡 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-125.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-126.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '떡볶이떡' AND p.name = '떡볶이떡 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-126.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-126.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '떡볶이떡' AND p.name = '떡볶이떡 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-126.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-127.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식빵' AND p.name = '식빵 450g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-127.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-127.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식빵' AND p.name = '식빵 900g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-127.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-128.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바게트' AND p.name = '바게트 250g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-128.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-128.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바게트' AND p.name = '바게트 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-128.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-129.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '번' AND p.name = '번 4개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-129.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-129.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '번' AND p.name = '번 8개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-129.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-130.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '잉글리시 머핀' AND p.name = '잉글리시 머핀 4개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-130.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-130.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '잉글리시 머핀' AND p.name = '잉글리시 머핀 8개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-130.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-131.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '또띠아' AND p.name = '또띠아 8개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-131.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-131.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '또띠아' AND p.name = '또띠아 16개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-131.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-132.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '유부' AND p.name = '유부 20개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-132.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-132.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '유부' AND p.name = '유부 40개' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-132.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-133.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '후레이크' AND p.name = '후레이크 40g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-133.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-133.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '후레이크' AND p.name = '후레이크 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-133.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-134.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '단무지' AND p.name = '단무지 300g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-134.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-134.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '단무지' AND p.name = '단무지 600g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-134.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-135.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통조림 콩' AND p.name = '통조림 콩 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-135.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-135.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통조림 콩' AND p.name = '통조림 콩 800g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-135.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-136.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '참치캔' AND p.name = '참치캔 150g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-136.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-136.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '참치캔' AND p.name = '참치캔 450g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-136.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-137.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '청국장' AND p.name = '청국장 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-137.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-137.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '청국장' AND p.name = '청국장 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-137.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-138.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '액젓' AND p.name = '액젓 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-138.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-138.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '액젓' AND p.name = '액젓 1L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-138.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-139.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '케첩' AND p.name = '케첩 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-139.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-139.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '케첩' AND p.name = '케첩 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-139.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-140.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '마요네즈' AND p.name = '마요네즈 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-140.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-140.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '마요네즈' AND p.name = '마요네즈 1kg' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-140.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-141.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '머스터드' AND p.name = '머스터드 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-141.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-141.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '머스터드' AND p.name = '머스터드 400g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-141.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-142.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '겨자' AND p.name = '겨자 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-142.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-142.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '겨자' AND p.name = '겨자 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-142.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-143.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '굴소스' AND p.name = '굴소스 260ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-143.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-143.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '굴소스' AND p.name = '굴소스 510ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-143.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-144.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우스터소스' AND p.name = '우스터소스 150ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-144.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-144.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '우스터소스' AND p.name = '우스터소스 300ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-144.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-145.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '냉면육수' AND p.name = '냉면육수 340ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-145.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-145.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '냉면육수' AND p.name = '냉면육수 1.02L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-145.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-146.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '들기름' AND p.name = '들기름 320ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-146.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-146.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '들기름' AND p.name = '들기름 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-146.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-147.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고추기름' AND p.name = '고추기름 150ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-147.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-147.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '고추기름' AND p.name = '고추기름 300ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-147.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-148.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식초' AND p.name = '식초 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-148.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-148.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '식초' AND p.name = '식초 900ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-148.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-149.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '맛술' AND p.name = '맛술 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-149.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-149.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '맛술' AND p.name = '맛술 1L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-149.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-150.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '물엿' AND p.name = '물엿 700ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-150.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-150.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '물엿' AND p.name = '물엿 1.2L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-150.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-151.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통깨' AND p.name = '통깨 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-151.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-151.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통깨' AND p.name = '통깨 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-151.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-152.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '들깻가루' AND p.name = '들깻가루 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-152.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-152.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '들깻가루' AND p.name = '들깻가루 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-152.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-153.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '페페론치노' AND p.name = '페페론치노 30g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-153.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-153.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '페페론치노' AND p.name = '페페론치노 60g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-153.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-154.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '월계수잎' AND p.name = '월계수잎 10g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-154.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-154.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '월계수잎' AND p.name = '월계수잎 20g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-154.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-155.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통후추' AND p.name = '통후추 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-155.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-155.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '통후추' AND p.name = '통후추 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-155.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-156.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '시나몬' AND p.name = '시나몬 30g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-156.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-156.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '시나몬' AND p.name = '시나몬 60g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-156.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-157.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바닐라익스트랙' AND p.name = '바닐라익스트랙 50ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-157.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-157.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '바닐라익스트랙' AND p.name = '바닐라익스트랙 100ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-157.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-158.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '사프란' AND p.name = '사프란 1g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-158.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-158.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '사프란' AND p.name = '사프란 2g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-158.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-159.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파프리카 가루' AND p.name = '파프리카 가루 50g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-159.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-159.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '파프리카 가루' AND p.name = '파프리카 가루 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-159.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-160.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '슈가파우더' AND p.name = '슈가파우더 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-160.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-160.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '슈가파우더' AND p.name = '슈가파우더 500g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-160.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-161.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '화이트와인' AND p.name = '화이트와인 750ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-161.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-161.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '화이트와인' AND p.name = '화이트와인 1.5L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-161.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-162.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '카레가루' AND p.name = '카레가루 100g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-162.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-162.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '카레가루' AND p.name = '카레가루 200g' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-162.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-163.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '매실청' AND p.name = '매실청 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-163.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-163.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '매실청' AND p.name = '매실청 1L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-163.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-164.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '메이플시럽' AND p.name = '메이플시럽 250ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-164.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-164.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '메이플시럽' AND p.name = '메이플시럽 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-164.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-165.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '배즙' AND p.name = '배즙 500ml' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-165.webp');
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN mart m ON m.id = p.mart_id
SET p.image_url = '/images/products/ingredient-165.webp', p.updated_at = NOW()
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND i.name = '배즙' AND p.name = '배즙 1L' AND p.brand = '레시픽' AND NOT (p.image_url <=> '/images/products/ingredient-165.webp');

-- 예전 시드로 만든 DB의 상품을 새 카테고리로 옮기고, 비게 된 예전 카테고리는 숨긴다.
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '채소·과일'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('김치', '대파', '양파', '다진마늘', '애호박', '청양고추', '당근', '통마늘', '생강', '홍고추', '건고추', '꽈리고추', '감자', '무', '콩나물', '시금치', '오이', '양배추', '고구마', '깻잎', '표고버섯', '양송이버섯', '파프리카', '피망', '토마토', '방울토마토', '단호박', '고사리', '숙주', '우거지', '곤드레', '쑥갓', '미나리', '우엉', '밤', '대추', '인삼', '셀러리', '로메인', '양상추', '아보카도', '옥수수', '레몬', '바질', '파슬리', '로즈마리', '피클', '견과류', '쪽파') AND p.category_id <> c.id;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '육류·가공육'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('돼지고기 앞다리살', '소고기 국거리', '베이컨', '다진 돼지고기', '다진 소고기', '소고기 불고기용', '소갈비', '소고기 홍두깨살', '소고기 스테이크용', '돼지 등뼈', '닭볶음탕용 닭', '닭다리살', '닭가슴살', '영계', '햄', '소시지') AND p.category_id <> c.id;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '수산·건어물'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('국물용 멸치', '건미역', '새우젓', '동태', '오징어', '낙지', '쭈꾸미', '북어채', '고등어', '새우', '바지락', '홍합', '어묵', '맛살', '잔멸치', '다시마', '김가루', '김밥김', '안초비') AND p.category_id <> c.id;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '두부·계란·유제품'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('두부', '계란', '파마산치즈 가루', '생크림', '순두부', '우유', '버터', '모차렐라 치즈', '체다 치즈', '요거트') AND p.category_id <> c.id;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '쌀·곡물·가루'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('쌀', '찹쌀', '백태', '밀가루', '부침가루', '빵가루', '전분') AND p.category_id <> c.id;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '면·파스타·떡'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('스파게티면', '소면', '칼국수면', '냉면사리', '쫄면사리', '메밀면', '당면', '라면사리', '펜네', '마카로니', '라자냐면', '떡국떡', '떡볶이떡') AND p.category_id <> c.id;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '빵·가공식품'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('식빵', '바게트', '번', '잉글리시 머핀', '또띠아', '유부', '후레이크', '단무지', '통조림 콩', '참치캔') AND p.category_id <> c.id;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '장류·소스'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('된장', '국간장', '고추장', '간장', '토마토소스', '청국장', '액젓', '케첩', '마요네즈', '머스터드', '겨자', '굴소스', '우스터소스', '냉면육수') AND p.category_id <> c.id;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = '기름·양념·향신료'
SET p.category_id = c.id, p.updated_at = NOW()
WHERE i.name IN ('고춧가루', '참기름', '설탕', '소금', '식용유', '후추', '올리브유', '들기름', '고추기름', '식초', '맛술', '물엿', '통깨', '들깻가루', '페페론치노', '월계수잎', '통후추', '시나몬', '바닐라익스트랙', '사프란', '파프리카 가루', '슈가파우더', '화이트와인', '카레가루', '매실청', '메이플시럽', '배즙') AND p.category_id <> c.id;
UPDATE category SET deleted = TRUE, updated_at = NOW() WHERE name = '양념·소스' AND NOT EXISTS (SELECT 1 FROM product p WHERE p.category_id = category.id);
UPDATE category SET deleted = TRUE, updated_at = NOW() WHERE name = '면·가공식품' AND NOT EXISTS (SELECT 1 FROM product p WHERE p.category_id = category.id);

UPDATE recipe SET auto_cart_enabled = TRUE, updated_at = NOW()
WHERE name IN ('김치찌개', '된장찌개', '순두부찌개', '부대찌개', '청국장찌개', '고추장찌개', '동태찌개', '참치김치찌개', '닭볶음탕', '미역국', '소고기무국', '콩나물국', '북엇국', '시금치된장국', '떡국', '육개장', '삼계탕', '갈비탕', '감자탕', '어묵탕', '오이냉국', '계란국', '제육볶음', '오징어볶음', '낙지볶음', '쭈꾸미볶음', '소불고기', '닭갈비', '돼지고기두루치기', '김치볶음밥', '비빔밥', '콩나물밥', '김밥', '유부초밥', '제육덮밥', '참치마요덮밥', '새우볶음밥', '주먹밥', '곤드레밥', '낙지덮밥', '잔치국수', '비빔국수', '칼국수', '수제비', '물냉면', '비빔냉면', '콩국수', '라볶이', '쫄면', '들기름막국수', '계란말이', '시금치나물', '콩나물무침', '멸치볶음', '감자조림', '두부조림', '장조림', '오이무침', '감자채볶음', '어묵볶음', '무생채', '고등어조림', '잡채', '김치전', '해물파전', '떡볶이', '계란찜', '갈비찜', '찜닭', '수육', '까르보나라', '알리오올리오', '토마토파스타', '봉골레파스타', '로제파스타', '미트볼스파게티', '라자냐', '바질페스토파스타', '아라비아타', '맥앤치즈', '뇨끼', '양송이수프', '단호박수프', '클램차우더', '미네스트로네', '버섯리조또', '토마토리조또', '빠에야', '오므라이스', '스테이크', '함박스테이크', '감바스알아히요', '프렌치토스트', '시저샐러드', '콥샐러드', '클럽샌드위치', '에그베네딕트', '또띠아피자', '수제버거', '프리타타');

-- 하안 마트 좌표가 철산동 쪽에 있어 하안동 안에서 현재 위치를 찾아도 이용 가능 지역과 어긋났다. 하안동 안쪽으로 옮긴다.
-- data.sql의 INSERT는 이미 있는 행을 건너뛰므로 기존 로컬 DB에는 UPDATE로 반영한다. 예전 좌표일 때만 바꿔 직접 고친 값은 덮어쓰지 않는다.
UPDATE mart SET latitude = 37.4640000, longitude = 126.8760000, updated_at = NOW()
WHERE id = 2 AND region_id = 2 AND latitude = 37.4710000 AND longitude = 126.8760000;
