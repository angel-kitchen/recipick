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

INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김치 500g', '종가', NULL, 500, 'G',
       '/images/products/kimchi.jpg', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '김치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김치 500g' AND p.brand = '종가');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김치 900g', '풀무원', NULL, 900, 'G',
       '/images/products/kimchi.jpg', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '김치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김치 900g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지고기 앞다리살 500g', '국내산', NULL, 500, 'G',
       '/images/products/pork-shoulder.jpg', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지고기 앞다리살 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지고기 앞다리살 600g', '한돈', NULL, 600, 'G',
       '/images/products/pork-shoulder.jpg', 13900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지고기 앞다리살 600g' AND p.brand = '한돈');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '두부 300g', '풀무원', NULL, 300, 'G',
       '/images/products/tofu.jpg', 2300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '두부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '두부 300g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '두부 500g', 'CJ', NULL, 500, 'G',
       '/images/products/tofu.jpg', 3100, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '두부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '두부 500g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대파 300g', '국내산', NULL, 300, 'G',
       '/images/products/green-onion.jpg', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '대파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대파 300g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대파 500g', '친환경', NULL, 500, 'G',
       '/images/products/green-onion.jpg', 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '대파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대파 500g' AND p.brand = '친환경');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양파 1kg', '국내산', NULL, 1000, 'G',
       '/images/products/onion.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양파 1kg' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양파 1.5kg', '국내산 대', NULL, 1500, 'G',
       '/images/products/onion.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양파 1.5kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진마늘 200g', '국내산', NULL, 200, 'G',
       '/images/products/minced-garlic.jpg', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '다진마늘'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진마늘 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진마늘 500g', '국내산 대용량', NULL, 500, 'G',
       '/images/products/minced-garlic.jpg', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '다진마늘'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진마늘 500g' AND p.brand = '국내산 대용량');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고춧가루 200g', '청정원', NULL, 200, 'G',
       '/images/products/chili-powder.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '고춧가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고춧가루 200g' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고춧가루 500g', '해찬들', NULL, 500, 'G',
       '/images/products/chili-powder.jpg', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '고춧가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고춧가루 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '된장 500g', '해찬들', NULL, 500, 'G',
       '/images/products/doenjang.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '된장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '된장 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '된장 1kg', '사조', NULL, 1000, 'G',
       '/images/products/doenjang.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '된장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '된장 1kg' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '애호박 1개', '국내산', NULL, 1, 'EA',
       '/images/products/zucchini.jpg', 1500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '애호박'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '애호박 1개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '애호박 2개', '친환경', NULL, 2, 'EA',
       '/images/products/zucchini.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '애호박'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '애호박 2개' AND p.brand = '친환경');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청양고추 100g', '국내산', NULL, 100, 'G',
       '/images/products/green-chili.jpg', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '청양고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청양고추 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청양고추 200g', '국내산 대', NULL, 200, 'G',
       '/images/products/green-chili.jpg', 3300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '청양고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청양고추 200g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국물용 멸치 200g', '남해', NULL, 200, 'G',
       '/images/products/anchovy.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '국물용 멸치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국물용 멸치 200g' AND p.brand = '남해');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국물용 멸치 400g', '통영', NULL, 400, 'G',
       '/images/products/anchovy.jpg', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '국물용 멸치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국물용 멸치 400g' AND p.brand = '통영');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건미역 50g', '완도', NULL, 50, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '건미역'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건미역 50g' AND p.brand = '완도');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건미역 100g', '기장', NULL, 100, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '건미역'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건미역 100g' AND p.brand = '기장');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 국거리 300g', '국내산 한우', NULL, 300, 'G',
       '/images/products/beef-soup.jpg', 17900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 국거리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 국거리 300g' AND p.brand = '국내산 한우');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 국거리 400g', '호주산', NULL, 400, 'G',
       '/images/products/beef-soup.jpg', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 국거리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 국거리 400g' AND p.brand = '호주산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국간장 500ml', '청정원', NULL, 500, 'ML',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '국간장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국간장 500ml' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국간장 900ml', '샘표', NULL, 900, 'ML',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '국간장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국간장 900ml' AND p.brand = '샘표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참기름 320ml', '오뚜기', NULL, 320, 'ML',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '참기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참기름 320ml' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참기름 500ml', 'CJ', NULL, 500, 'ML',
       NULL, 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '참기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참기름 500ml' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추장 500g', '해찬들', NULL, 500, 'G',
       '/images/products/gochujang.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '고추장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추장 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추장 1kg', 'CJ', NULL, 1000, 'G',
       '/images/products/gochujang.jpg', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '고추장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추장 1kg' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '간장 500ml', '샘표', NULL, 500, 'ML',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '간장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '간장 500ml' AND p.brand = '샘표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '간장 900ml', '몽고', NULL, 900, 'ML',
       NULL, 7500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '간장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '간장 900ml' AND p.brand = '몽고');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '설탕 1kg', 'CJ', NULL, 1000, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '설탕'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '설탕 1kg' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '설탕 3kg', '삼양', NULL, 3000, 'G',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '설탕'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '설탕 3kg' AND p.brand = '삼양');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '계란 10개', '특란', NULL, 10, 'EA',
       '/images/products/egg.jpg', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '계란'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '계란 10개' AND p.brand = '특란');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '계란 30개', '대란', NULL, 30, 'EA',
       '/images/products/egg.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '계란'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '계란 30개' AND p.brand = '대란');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당근 2개', '국내산', NULL, 2, 'EA',
       '/images/products/carrot.jpg', 2200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '당근'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당근 2개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당근 4개', '국내산 대', NULL, 4, 'EA',
       '/images/products/carrot.jpg', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '당근'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당근 4개' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소금 500g', '꽃소금', NULL, 500, 'G',
       '/images/products/salt.jpg', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '소금'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소금 500g' AND p.brand = '꽃소금');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소금 1kg', '천일염', NULL, 1000, 'G',
       '/images/products/salt.jpg', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '소금'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소금 1kg' AND p.brand = '천일염');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식용유 900ml', 'CJ 해표', NULL, 900, 'ML',
       '/images/products/cooking-oil.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '식용유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식용유 900ml' AND p.brand = 'CJ 해표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식용유 1.8L', '오뚜기', NULL, 1800, 'ML',
       '/images/products/cooking-oil.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '식용유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식용유 1.8L' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우젓 200g', '육젓', NULL, 200, 'G',
       '/images/products/salted-shrimp.jpg', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우젓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우젓 200g' AND p.brand = '육젓');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우젓 500g', '추젓', NULL, 500, 'G',
       '/images/products/salted-shrimp.jpg', 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우젓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우젓 500g' AND p.brand = '추젓');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '스파게티면 500g', '폰타나', NULL, 500, 'G',
       '/images/products/spaghetti.jpg', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '스파게티면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '스파게티면 500g' AND p.brand = '폰타나');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '스파게티면 500g', '데체코', NULL, 500, 'G',
       '/images/products/spaghetti.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '스파게티면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '스파게티면 500g' AND p.brand = '데체코');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '베이컨 200g', '대림', NULL, 200, 'G',
       '/images/products/bacon.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '베이컨'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '베이컨 200g' AND p.brand = '대림');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '베이컨 400g', '롯데', NULL, 400, 'G',
       '/images/products/bacon.jpg', 8200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '베이컨'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '베이컨 400g' AND p.brand = '롯데');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파마산치즈 가루 100g', '서울우유', NULL, 100, 'G',
       '/images/products/parmesan.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파마산치즈 가루 100g' AND p.brand = '서울우유');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파마산치즈 가루 200g', '데이지', NULL, 200, 'G',
       '/images/products/parmesan.jpg', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파마산치즈 가루 200g' AND p.brand = '데이지');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생크림 250ml', '서울우유', NULL, 250, 'ML',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '생크림'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생크림 250ml' AND p.brand = '서울우유');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생크림 500ml', '매일', NULL, 500, 'ML',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '생크림'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생크림 500ml' AND p.brand = '매일');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후추 50g', '오뚜기', NULL, 50, 'G',
       '/images/products/black-pepper.jpg', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '후추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후추 50g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후추 100g', '백설', NULL, 100, 'G',
       '/images/products/black-pepper.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '후추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후추 100g' AND p.brand = '백설');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '올리브유 500ml', '사조', NULL, 500, 'ML',
       '/images/products/olive-oil.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '올리브유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '올리브유 500ml' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '올리브유 1L', '오뚜기', NULL, 1000, 'ML',
       '/images/products/olive-oil.jpg', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '올리브유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '올리브유 1L' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토소스 400g', '오뚜기', NULL, 400, 'G',
       '/images/products/tomato-sauce.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '토마토소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토소스 400g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토소스 600g', '폰타나', NULL, 600, 'G',
       '/images/products/tomato-sauce.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '토마토소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토소스 600g' AND p.brand = '폰타나');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통마늘 200g', '국내산', NULL, 200, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '통마늘'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통마늘 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통마늘 500g', '의성', NULL, 500, 'G',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '통마늘'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통마늘 500g' AND p.brand = '의성');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생강 100g', '국내산', NULL, 100, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '생강'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생강 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생강 300g', '국내산 대', NULL, 300, 'G',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '생강'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생강 300g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '홍고추 100g', '국내산', NULL, 100, 'G',
       NULL, 2000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '홍고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '홍고추 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '홍고추 200g', '국내산 대', NULL, 200, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '홍고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '홍고추 200g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건고추 50g', '국내산', NULL, 50, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '건고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건고추 50g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건고추 100g', '영양', NULL, 100, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '건고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건고추 100g' AND p.brand = '영양');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '꽈리고추 150g', '국내산', NULL, 150, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '꽈리고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '꽈리고추 150g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '꽈리고추 300g', '국내산 대', NULL, 300, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '꽈리고추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '꽈리고추 300g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '감자 1kg', '국내산', NULL, 1000, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '감자'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '감자 1kg' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '감자 2kg', '수미', NULL, 2000, 'G',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '감자'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '감자 2kg' AND p.brand = '수미');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '무 800g', '국내산', NULL, 800, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '무'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '무 800g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '무 1.5kg', '국내산 대', NULL, 1500, 'G',
       NULL, 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '무'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '무 1.5kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '콩나물 300g', '풀무원', NULL, 300, 'G',
       NULL, 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '콩나물'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '콩나물 300g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '콩나물 500g', '국내산', NULL, 500, 'G',
       NULL, 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '콩나물'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '콩나물 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '시금치 200g', '국내산', NULL, 200, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '시금치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '시금치 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '시금치 400g', '포항초', NULL, 400, 'G',
       NULL, 6200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '시금치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '시금치 400g' AND p.brand = '포항초');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '오이 2개', '국내산', NULL, 2, 'EA',
       NULL, 2200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '오이'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '오이 2개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '오이 5개', '취청', NULL, 5, 'EA',
       NULL, 4800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '오이'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '오이 5개' AND p.brand = '취청');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양배추 800g', '국내산', NULL, 800, 'G',
       NULL, 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양배추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양배추 800g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양배추 1.5kg', '국내산 대', NULL, 1500, 'G',
       NULL, 4800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양배추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양배추 1.5kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고구마 1kg', '국내산', NULL, 1000, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '고구마'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고구마 1kg' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고구마 2kg', '호박고구마', NULL, 2000, 'G',
       NULL, 7200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '고구마'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고구마 2kg' AND p.brand = '호박고구마');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '깻잎 100g', '국내산', NULL, 100, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '깻잎'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '깻잎 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '깻잎 200g', '국내산 대', NULL, 200, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '깻잎'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '깻잎 200g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '표고버섯 150g', '국내산', NULL, 150, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '표고버섯'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '표고버섯 150g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '표고버섯 300g', '국내산 대', NULL, 300, 'G',
       NULL, 6200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '표고버섯'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '표고버섯 300g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양송이버섯 200g', '국내산', NULL, 200, 'G',
       NULL, 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양송이버섯'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양송이버섯 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양송이버섯 400g', '국내산 대', NULL, 400, 'G',
       NULL, 5800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양송이버섯'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양송이버섯 400g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파프리카 2개', '국내산', NULL, 2, 'EA',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '파프리카'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파프리카 2개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파프리카 4개', '국내산 대', NULL, 4, 'EA',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '파프리카'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파프리카 4개' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '피망 3개', '국내산', NULL, 3, 'EA',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '피망'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '피망 3개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '피망 6개', '국내산 대', NULL, 6, 'EA',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '피망'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '피망 6개' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토 3개', '국내산', NULL, 3, 'EA',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '토마토'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토 3개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토 6개', '국내산 대', NULL, 6, 'EA',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '토마토'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토 6개' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '방울토마토 500g', '국내산', NULL, 500, 'G',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '방울토마토'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '방울토마토 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '방울토마토 1kg', '대추방울', NULL, 1000, 'G',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '방울토마토'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '방울토마토 1kg' AND p.brand = '대추방울');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '단호박 1개', '국내산', NULL, 1, 'EA',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '단호박'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '단호박 1개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '단호박 2개', '국내산 대', NULL, 2, 'EA',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '단호박'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '단호박 2개' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고사리 150g', '국내산', NULL, 150, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '고사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고사리 150g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고사리 300g', '국내산 대', NULL, 300, 'G',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '고사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고사리 300g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '숙주 300g', '국내산', NULL, 300, 'G',
       NULL, 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '숙주'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '숙주 300g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '숙주 500g', '국내산 대', NULL, 500, 'G',
       NULL, 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '숙주'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '숙주 500g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우거지 300g', '국내산', NULL, 300, 'G',
       NULL, 3000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '우거지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우거지 300g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우거지 600g', '국내산 대', NULL, 600, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '우거지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우거지 600g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '곤드레 200g', '국내산', NULL, 200, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '곤드레'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '곤드레 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '곤드레 400g', '국내산 대', NULL, 400, 'G',
       NULL, 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '곤드레'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '곤드레 400g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쑥갓 100g', '국내산', NULL, 100, 'G',
       NULL, 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '쑥갓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쑥갓 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쑥갓 200g', '국내산 대', NULL, 200, 'G',
       NULL, 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '쑥갓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쑥갓 200g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '미나리 200g', '국내산', NULL, 200, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '미나리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '미나리 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '미나리 400g', '청도', NULL, 400, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '미나리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '미나리 400g' AND p.brand = '청도');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우엉 200g', '국내산', NULL, 200, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '우엉'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우엉 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우엉 400g', '국내산 대', NULL, 400, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '우엉'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우엉 400g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '밤 300g', '국내산', NULL, 300, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '밤'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '밤 300g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '밤 600g', '공주', NULL, 600, 'G',
       NULL, 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '밤'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '밤 600g' AND p.brand = '공주');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대추 100g', '국내산', NULL, 100, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '대추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대추 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대추 200g', '경산', NULL, 200, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '대추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대추 200g' AND p.brand = '경산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '인삼 100g', '국내산', NULL, 100, 'G',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '인삼'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '인삼 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '인삼 200g', '금산', NULL, 200, 'G',
       NULL, 10500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '인삼'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '인삼 200g' AND p.brand = '금산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '셀러리 200g', '국내산', NULL, 200, 'G',
       NULL, 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '셀러리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '셀러리 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '셀러리 400g', '국내산 대', NULL, 400, 'G',
       NULL, 4800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '셀러리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '셀러리 400g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '로메인 150g', '국내산', NULL, 150, 'G',
       NULL, 3000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '로메인'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '로메인 150g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '로메인 300g', '국내산 대', NULL, 300, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '로메인'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '로메인 300g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양상추 200g', '국내산', NULL, 200, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양상추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양상추 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양상추 400g', '국내산 대', NULL, 400, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '양상추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양상추 400g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '아보카도 2개', '페루산', NULL, 2, 'EA',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '아보카도'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '아보카도 2개' AND p.brand = '페루산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '아보카도 4개', '멕시코산', NULL, 4, 'EA',
       NULL, 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '아보카도'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '아보카도 4개' AND p.brand = '멕시코산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '옥수수 340g', '델몬트', NULL, 340, 'G',
       NULL, 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '옥수수'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '옥수수 340g' AND p.brand = '델몬트');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '옥수수 680g', '동원', NULL, 680, 'G',
       NULL, 5000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '옥수수'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '옥수수 680g' AND p.brand = '동원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '레몬 3개', '미국산', NULL, 3, 'EA',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '레몬'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '레몬 3개' AND p.brand = '미국산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '레몬 6개', '칠레산', NULL, 6, 'EA',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '레몬'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '레몬 6개' AND p.brand = '칠레산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바질 20g', '국내산', NULL, 20, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '바질'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바질 20g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바질 50g', '국내산 대', NULL, 50, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '바질'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바질 50g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파슬리 20g', '국내산', NULL, 20, 'G',
       NULL, 2000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '파슬리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파슬리 20g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파슬리 50g', '국내산 대', NULL, 50, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '파슬리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파슬리 50g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '로즈마리 20g', '국내산', NULL, 20, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '로즈마리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '로즈마리 20g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '로즈마리 50g', '국내산 대', NULL, 50, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '로즈마리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '로즈마리 50g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '피클 300g', '오뚜기', NULL, 300, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '피클'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '피클 300g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '피클 600g', '청정원', NULL, 600, 'G',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '피클'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '피클 600g' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '견과류 150g', '견과믹스', NULL, 150, 'G',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '견과류'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '견과류 150g' AND p.brand = '견과믹스');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '견과류 300g', '견과믹스 대', NULL, 300, 'G',
       NULL, 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '견과류'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '견과류 300g' AND p.brand = '견과믹스 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쪽파 200g', '국내산', NULL, 200, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '쪽파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쪽파 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쪽파 400g', '국내산 대', NULL, 400, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소·과일' JOIN ingredient i ON i.name = '쪽파'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쪽파 400g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진 돼지고기 300g', '국내산', NULL, 300, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '다진 돼지고기'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진 돼지고기 300g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진 돼지고기 600g', '한돈', NULL, 600, 'G',
       NULL, 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '다진 돼지고기'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진 돼지고기 600g' AND p.brand = '한돈');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진 소고기 300g', '호주산', NULL, 300, 'G',
       NULL, 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '다진 소고기'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진 소고기 300g' AND p.brand = '호주산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진 소고기 500g', '한우', NULL, 500, 'G',
       NULL, 24900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '다진 소고기'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진 소고기 500g' AND p.brand = '한우');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 불고기용 300g', '호주산', NULL, 300, 'G',
       NULL, 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 불고기용'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 불고기용 300g' AND p.brand = '호주산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 불고기용 500g', '한우', NULL, 500, 'G',
       NULL, 29900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 불고기용'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 불고기용 500g' AND p.brand = '한우');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소갈비 1kg', '호주산', NULL, 1000, 'G',
       NULL, 24900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소갈비'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소갈비 1kg' AND p.brand = '호주산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소갈비 1.5kg', '한우', NULL, 1500, 'G',
       NULL, 69900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소갈비'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소갈비 1.5kg' AND p.brand = '한우');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 홍두깨살 500g', '호주산', NULL, 500, 'G',
       NULL, 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 홍두깨살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 홍두깨살 500g' AND p.brand = '호주산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 홍두깨살 1kg', '한우', NULL, 1000, 'G',
       NULL, 49900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 홍두깨살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 홍두깨살 1kg' AND p.brand = '한우');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 스테이크용 250g', '호주산 안심', NULL, 250, 'G',
       NULL, 14900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 스테이크용'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 스테이크용 250g' AND p.brand = '호주산 안심');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 스테이크용 400g', '한우 등심', NULL, 400, 'G',
       NULL, 39900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소고기 스테이크용'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 스테이크용 400g' AND p.brand = '한우 등심');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지 등뼈 1kg', '국내산', NULL, 1000, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '돼지 등뼈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지 등뼈 1kg' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지 등뼈 2kg', '국내산 대', NULL, 2000, 'G',
       NULL, 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '돼지 등뼈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지 등뼈 2kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭볶음탕용 닭 1kg', '하림', NULL, 1000, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭볶음탕용 닭'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭볶음탕용 닭 1kg' AND p.brand = '하림');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭볶음탕용 닭 1.5kg', '마니커', NULL, 1500, 'G',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭볶음탕용 닭'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭볶음탕용 닭 1.5kg' AND p.brand = '마니커');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭다리살 500g', '하림', NULL, 500, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭다리살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭다리살 500g' AND p.brand = '하림');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭다리살 1kg', '마니커', NULL, 1000, 'G',
       NULL, 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭다리살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭다리살 1kg' AND p.brand = '마니커');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭가슴살 500g', '하림', NULL, 500, 'G',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭가슴살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭가슴살 500g' AND p.brand = '하림');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '닭가슴살 1kg', '마니커', NULL, 1000, 'G',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '닭가슴살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '닭가슴살 1kg' AND p.brand = '마니커');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '영계 500g', '삼계탕용', NULL, 500, 'G',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '영계'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '영계 500g' AND p.brand = '삼계탕용');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '영계 1kg', '삼계탕용 2마리', NULL, 1000, 'G',
       NULL, 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '영계'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '영계 1kg' AND p.brand = '삼계탕용 2마리');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '햄 200g', '스팸', NULL, 200, 'G',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '햄'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '햄 200g' AND p.brand = '스팸');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '햄 340g', '롯데', NULL, 340, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '햄'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '햄 340g' AND p.brand = '롯데');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소시지 300g', '롯데', NULL, 300, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소시지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소시지 300g' AND p.brand = '롯데');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소시지 600g', 'CJ', NULL, 600, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류·가공육' JOIN ingredient i ON i.name = '소시지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소시지 600g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '동태 500g', '국내 해동', NULL, 500, 'G',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '동태'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '동태 500g' AND p.brand = '국내 해동');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '동태 1kg', '국내 해동 대', NULL, 1000, 'G',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '동태'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '동태 1kg' AND p.brand = '국내 해동 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '오징어 400g', '국내산', NULL, 400, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '오징어'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '오징어 400g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '오징어 800g', '동해', NULL, 800, 'G',
       NULL, 12500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '오징어'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '오징어 800g' AND p.brand = '동해');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '낙지 200g', '국내산', NULL, 200, 'G',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '낙지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '낙지 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '낙지 400g', '서해', NULL, 400, 'G',
       NULL, 18000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '낙지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '낙지 400g' AND p.brand = '서해');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쭈꾸미 300g', '국내산', NULL, 300, 'G',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '쭈꾸미'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쭈꾸미 300g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쭈꾸미 600g', '서해', NULL, 600, 'G',
       NULL, 17900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '쭈꾸미'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쭈꾸미 600g' AND p.brand = '서해');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '북어채 100g', '국내산', NULL, 100, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '북어채'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '북어채 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '북어채 200g', '황태', NULL, 200, 'G',
       NULL, 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '북어채'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '북어채 200g' AND p.brand = '황태');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고등어 400g', '노르웨이산', NULL, 400, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '고등어'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고등어 400g' AND p.brand = '노르웨이산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고등어 800g', '국내산', NULL, 800, 'G',
       NULL, 12500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '고등어'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고등어 800g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우 300g', '칵테일', NULL, 300, 'G',
       NULL, 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우 300g' AND p.brand = '칵테일');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우 500g', '흰다리', NULL, 500, 'G',
       NULL, 13900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우 500g' AND p.brand = '흰다리');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바지락 300g', '해감', NULL, 300, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '바지락'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바지락 300g' AND p.brand = '해감');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바지락 500g', '국내산', NULL, 500, 'G',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '바지락'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바지락 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '홍합 500g', '국내산', NULL, 500, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '홍합'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '홍합 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '홍합 1kg', '국내산 대', NULL, 1000, 'G',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '홍합'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '홍합 1kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '어묵 200g', '삼진', NULL, 200, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '어묵'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '어묵 200g' AND p.brand = '삼진');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '어묵 400g', '사각', NULL, 400, 'G',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '어묵'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '어묵 400g' AND p.brand = '사각');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '맛살 200g', '크라운', NULL, 200, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '맛살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '맛살 200g' AND p.brand = '크라운');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '맛살 400g', '사조', NULL, 400, 'G',
       NULL, 5000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '맛살'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '맛살 400g' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '잔멸치 100g', '남해', NULL, 100, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '잔멸치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '잔멸치 100g' AND p.brand = '남해');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '잔멸치 200g', '통영', NULL, 200, 'G',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '잔멸치'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '잔멸치 200g' AND p.brand = '통영');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다시마 50g', '완도', NULL, 50, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '다시마'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다시마 50g' AND p.brand = '완도');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다시마 100g', '기장', NULL, 100, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '다시마'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다시마 100g' AND p.brand = '기장');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김가루 50g', '광천', NULL, 50, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '김가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김가루 50g' AND p.brand = '광천');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김가루 100g', '성경', NULL, 100, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '김가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김가루 100g' AND p.brand = '성경');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김밥김 10개', '광천', NULL, 10, 'EA',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '김밥김'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김밥김 10개' AND p.brand = '광천');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김밥김 20개', '성경', NULL, 20, 'EA',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '김밥김'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김밥김 20개' AND p.brand = '성경');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '안초비 50g', '국내산', NULL, 50, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '안초비'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '안초비 50g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '안초비 100g', '수입산', NULL, 100, 'G',
       NULL, 8000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '안초비'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '안초비 100g' AND p.brand = '수입산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '순두부 350g', '풀무원', NULL, 350, 'G',
       NULL, 2300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '순두부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '순두부 350g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '순두부 700g', 'CJ', NULL, 700, 'G',
       NULL, 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '순두부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '순두부 700g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우유 900ml', '서울우유', NULL, 900, 'ML',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '우유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우유 900ml' AND p.brand = '서울우유');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우유 1.8L', '매일', NULL, 1800, 'ML',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '우유'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우유 1.8L' AND p.brand = '매일');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '버터 200g', '서울우유 무염', NULL, 200, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '버터'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '버터 200g' AND p.brand = '서울우유 무염');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '버터 450g', '앵커', NULL, 450, 'G',
       NULL, 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '버터'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '버터 450g' AND p.brand = '앵커');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '모차렐라 치즈 200g', '서울우유', NULL, 200, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '모차렐라 치즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '모차렐라 치즈 200g' AND p.brand = '서울우유');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '모차렐라 치즈 1kg', '대용량', NULL, 1000, 'G',
       NULL, 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '모차렐라 치즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '모차렐라 치즈 1kg' AND p.brand = '대용량');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '체다 치즈 200g', '슬라이스', NULL, 200, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '체다 치즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '체다 치즈 200g' AND p.brand = '슬라이스');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '체다 치즈 400g', '슬라이스 대', NULL, 400, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '체다 치즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '체다 치즈 400g' AND p.brand = '슬라이스 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '요거트 400g', '플레인', NULL, 400, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '요거트'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '요거트 400g' AND p.brand = '플레인');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '요거트 800g', '플레인 대', NULL, 800, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '요거트'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '요거트 800g' AND p.brand = '플레인 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쌀 1kg', '국내산', NULL, 1000, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '쌀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쌀 1kg' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쌀 4kg', '신동진', NULL, 4000, 'G',
       NULL, 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '쌀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쌀 4kg' AND p.brand = '신동진');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '찹쌀 500g', '국내산', NULL, 500, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '찹쌀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '찹쌀 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '찹쌀 1kg', '국내산 대', NULL, 1000, 'G',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '찹쌀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '찹쌀 1kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '백태 500g', '국산', NULL, 500, 'G',
       NULL, 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '백태'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '백태 500g' AND p.brand = '국산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '백태 1kg', '국산 대', NULL, 1000, 'G',
       NULL, 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '백태'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '백태 1kg' AND p.brand = '국산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '밀가루 1kg', 'CJ', NULL, 1000, 'G',
       NULL, 2400, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '밀가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '밀가루 1kg' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '밀가루 2.5kg', '곰표', NULL, 2500, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '밀가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '밀가루 2.5kg' AND p.brand = '곰표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '부침가루 500g', 'CJ', NULL, 500, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '부침가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '부침가루 500g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '부침가루 1kg', '백설', NULL, 1000, 'G',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '부침가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '부침가루 1kg' AND p.brand = '백설');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '빵가루 200g', '오뚜기', NULL, 200, 'G',
       NULL, 1900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '빵가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '빵가루 200g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '빵가루 500g', '큐원', NULL, 500, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '빵가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '빵가루 500g' AND p.brand = '큐원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '전분 200g', 'CJ', NULL, 200, 'G',
       NULL, 2000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '전분'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '전분 200g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '전분 500g', '옥수수전분', NULL, 500, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '쌀·곡물·가루' JOIN ingredient i ON i.name = '전분'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '전분 500g' AND p.brand = '옥수수전분');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소면 500g', '사조', NULL, 500, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '소면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소면 500g' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소면 900g', '오뚜기', NULL, 900, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '소면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소면 900g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '칼국수면 500g', '풀무원', NULL, 500, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '칼국수면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '칼국수면 500g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '칼국수면 1kg', 'CJ', NULL, 1000, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '칼국수면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '칼국수면 1kg' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '냉면사리 400g', '풀무원', NULL, 400, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '냉면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '냉면사리 400g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '냉면사리 800g', 'CJ', NULL, 800, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '냉면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '냉면사리 800g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쫄면사리 400g', '풀무원', NULL, 400, 'G',
       NULL, 3000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '쫄면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쫄면사리 400g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '쫄면사리 800g', 'CJ', NULL, 800, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '쫄면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '쫄면사리 800g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '메밀면 200g', '봉평', NULL, 200, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '메밀면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '메밀면 200g' AND p.brand = '봉평');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '메밀면 400g', '봉평 대', NULL, 400, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '메밀면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '메밀면 400g' AND p.brand = '봉평 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당면 300g', '대상', NULL, 300, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '당면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당면 300g' AND p.brand = '대상');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당면 500g', 'CJ', NULL, 500, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '당면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당면 500g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '라면사리 420g', '농심', NULL, 420, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '라면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '라면사리 420g' AND p.brand = '농심');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '라면사리 840g', '오뚜기', NULL, 840, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '라면사리'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '라면사리 840g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '펜네 500g', '데체코', NULL, 500, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '펜네'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '펜네 500g' AND p.brand = '데체코');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '펜네 1kg', '폰타나', NULL, 1000, 'G',
       NULL, 7200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '펜네'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '펜네 1kg' AND p.brand = '폰타나');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '마카로니 500g', '폰타나', NULL, 500, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '마카로니'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '마카로니 500g' AND p.brand = '폰타나');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '마카로니 1kg', '데체코', NULL, 1000, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '마카로니'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '마카로니 1kg' AND p.brand = '데체코');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '라자냐면 250g', '데체코', NULL, 250, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '라자냐면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '라자냐면 250g' AND p.brand = '데체코');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '라자냐면 500g', '바릴라', NULL, 500, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '라자냐면'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '라자냐면 500g' AND p.brand = '바릴라');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '떡국떡 500g', '국내산', NULL, 500, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '떡국떡'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '떡국떡 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '떡국떡 1kg', '국내산 대', NULL, 1000, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '떡국떡'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '떡국떡 1kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '떡볶이떡 500g', '국내산', NULL, 500, 'G',
       NULL, 3000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '떡볶이떡'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '떡볶이떡 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '떡볶이떡 1kg', '국내산 대', NULL, 1000, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·파스타·떡' JOIN ingredient i ON i.name = '떡볶이떡'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '떡볶이떡 1kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식빵 450g', '삼립', NULL, 450, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '식빵'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식빵 450g' AND p.brand = '삼립');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식빵 900g', '샤니', NULL, 900, 'G',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '식빵'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식빵 900g' AND p.brand = '샤니');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바게트 250g', '파리바게뜨', NULL, 250, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '바게트'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바게트 250g' AND p.brand = '파리바게뜨');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바게트 500g', '뚜레쥬르', NULL, 500, 'G',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '바게트'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바게트 500g' AND p.brand = '뚜레쥬르');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '번 4개', '햄버거번', NULL, 4, 'EA',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '번'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '번 4개' AND p.brand = '햄버거번');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '번 8개', '햄버거번 대', NULL, 8, 'EA',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '번'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '번 8개' AND p.brand = '햄버거번 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '잉글리시 머핀 4개', '국내산', NULL, 4, 'EA',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '잉글리시 머핀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '잉글리시 머핀 4개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '잉글리시 머핀 8개', '국내산 대', NULL, 8, 'EA',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '잉글리시 머핀'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '잉글리시 머핀 8개' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '또띠아 8개', '오뚜기', NULL, 8, 'EA',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '또띠아'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '또띠아 8개' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '또띠아 16개', 'CJ', NULL, 16, 'EA',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '또띠아'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '또띠아 16개' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '유부 20개', '유부초밥용', NULL, 20, 'EA',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '유부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '유부 20개' AND p.brand = '유부초밥용');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '유부 40개', '유부초밥용 대', NULL, 40, 'EA',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '유부'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '유부 40개' AND p.brand = '유부초밥용 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후레이크 40g', '유부초밥용', NULL, 40, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '후레이크'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후레이크 40g' AND p.brand = '유부초밥용');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후레이크 100g', '유부초밥용 대', NULL, 100, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '후레이크'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후레이크 100g' AND p.brand = '유부초밥용 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '단무지 300g', '오뚜기', NULL, 300, 'G',
       NULL, 1500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '단무지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '단무지 300g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '단무지 600g', '청정원', NULL, 600, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '단무지'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '단무지 600g' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통조림 콩 400g', '오뚜기', NULL, 400, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '통조림 콩'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통조림 콩 400g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통조림 콩 800g', '델몬트', NULL, 800, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '통조림 콩'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통조림 콩 800g' AND p.brand = '델몬트');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참치캔 150g', '동원', NULL, 150, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '참치캔'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참치캔 150g' AND p.brand = '동원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참치캔 450g', '사조', NULL, 450, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '빵·가공식품' JOIN ingredient i ON i.name = '참치캔'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참치캔 450g' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청국장 200g', '국내산', NULL, 200, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '청국장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청국장 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청국장 400g', '국내산 대', NULL, 400, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '청국장'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청국장 400g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '액젓 500ml', '까나리', NULL, 500, 'ML',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '액젓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '액젓 500ml' AND p.brand = '까나리');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '액젓 1L', '멸치', NULL, 1000, 'ML',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '액젓'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '액젓 1L' AND p.brand = '멸치');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '케첩 500g', '오뚜기', NULL, 500, 'G',
       NULL, 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '케첩'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '케첩 500g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '케첩 1kg', '하인즈', NULL, 1000, 'G',
       NULL, 5500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '케첩'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '케첩 1kg' AND p.brand = '하인즈');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '마요네즈 500g', '오뚜기', NULL, 500, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '마요네즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '마요네즈 500g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '마요네즈 1kg', '청정원', NULL, 1000, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '마요네즈'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '마요네즈 1kg' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '머스터드 200g', '오뚜기', NULL, 200, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '머스터드'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '머스터드 200g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '머스터드 400g', '하인즈', NULL, 400, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '머스터드'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '머스터드 400g' AND p.brand = '하인즈');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '겨자 50g', '오뚜기', NULL, 50, 'G',
       NULL, 1900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '겨자'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '겨자 50g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '겨자 100g', '청정원', NULL, 100, 'G',
       NULL, 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '겨자'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '겨자 100g' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '굴소스 260ml', '이금기', NULL, 260, 'ML',
       NULL, 3800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '굴소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '굴소스 260ml' AND p.brand = '이금기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '굴소스 510ml', '이금기 대', NULL, 510, 'ML',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '굴소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '굴소스 510ml' AND p.brand = '이금기 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우스터소스 150ml', '오뚜기', NULL, 150, 'ML',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '우스터소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우스터소스 150ml' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '우스터소스 300ml', '하인즈', NULL, 300, 'ML',
       NULL, 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '우스터소스'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '우스터소스 300ml' AND p.brand = '하인즈');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '냉면육수 340ml', '풀무원', NULL, 340, 'ML',
       NULL, 1500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '냉면육수'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '냉면육수 340ml' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '냉면육수 1.02L', '풀무원 3개입', NULL, 1020, 'ML',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '장류·소스' JOIN ingredient i ON i.name = '냉면육수'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '냉면육수 1.02L' AND p.brand = '풀무원 3개입');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '들기름 320ml', '오뚜기', NULL, 320, 'ML',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '들기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '들기름 320ml' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '들기름 500ml', '국산', NULL, 500, 'ML',
       NULL, 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '들기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '들기름 500ml' AND p.brand = '국산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추기름 150ml', '오뚜기', NULL, 150, 'ML',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '고추기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추기름 150ml' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추기름 300ml', '청정원', NULL, 300, 'ML',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '고추기름'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추기름 300ml' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식초 500ml', '오뚜기', NULL, 500, 'ML',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '식초'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식초 500ml' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식초 900ml', '사과식초', NULL, 900, 'ML',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '식초'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식초 900ml' AND p.brand = '사과식초');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '맛술 500ml', '미림', NULL, 500, 'ML',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '맛술'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '맛술 500ml' AND p.brand = '미림');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '맛술 1L', '미림 대', NULL, 1000, 'ML',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '맛술'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '맛술 1L' AND p.brand = '미림 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '물엿 700ml', 'CJ', NULL, 700, 'ML',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '물엿'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '물엿 700ml' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '물엿 1.2L', '백설', NULL, 1200, 'ML',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '물엿'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '물엿 1.2L' AND p.brand = '백설');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통깨 100g', '국산', NULL, 100, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '통깨'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통깨 100g' AND p.brand = '국산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통깨 200g', '국산 대', NULL, 200, 'G',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '통깨'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통깨 200g' AND p.brand = '국산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '들깻가루 100g', '국산', NULL, 100, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '들깻가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '들깻가루 100g' AND p.brand = '국산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '들깻가루 200g', '국산 대', NULL, 200, 'G',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '들깻가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '들깻가루 200g' AND p.brand = '국산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '페페론치노 30g', '수입산', NULL, 30, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '페페론치노'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '페페론치노 30g' AND p.brand = '수입산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '페페론치노 60g', '수입산 대', NULL, 60, 'G',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '페페론치노'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '페페론치노 60g' AND p.brand = '수입산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '월계수잎 10g', '수입산', NULL, 10, 'G',
       NULL, 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '월계수잎'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '월계수잎 10g' AND p.brand = '수입산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '월계수잎 20g', '수입산 대', NULL, 20, 'G',
       NULL, 4000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '월계수잎'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '월계수잎 20g' AND p.brand = '수입산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통후추 50g', '수입산', NULL, 50, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '통후추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통후추 50g' AND p.brand = '수입산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '통후추 100g', '수입산 대', NULL, 100, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '통후추'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '통후추 100g' AND p.brand = '수입산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '시나몬 30g', '수입산', NULL, 30, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '시나몬'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '시나몬 30g' AND p.brand = '수입산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '시나몬 60g', '수입산 대', NULL, 60, 'G',
       NULL, 6000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '시나몬'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '시나몬 60g' AND p.brand = '수입산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바닐라익스트랙 50ml', '수입산', NULL, 50, 'ML',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '바닐라익스트랙'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바닐라익스트랙 50ml' AND p.brand = '수입산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '바닐라익스트랙 100ml', '수입산 대', NULL, 100, 'ML',
       NULL, 11000, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '바닐라익스트랙'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '바닐라익스트랙 100ml' AND p.brand = '수입산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '사프란 1g', '스페인산', NULL, 1, 'G',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '사프란'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '사프란 1g' AND p.brand = '스페인산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '사프란 2g', '스페인산 대', NULL, 2, 'G',
       NULL, 17900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '사프란'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '사프란 2g' AND p.brand = '스페인산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파프리카 가루 50g', '수입산', NULL, 50, 'G',
       NULL, 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '파프리카 가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파프리카 가루 50g' AND p.brand = '수입산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파프리카 가루 100g', '수입산 대', NULL, 100, 'G',
       NULL, 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '파프리카 가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파프리카 가루 100g' AND p.brand = '수입산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '슈가파우더 200g', 'CJ', NULL, 200, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '슈가파우더'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '슈가파우더 200g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '슈가파우더 500g', '백설', NULL, 500, 'G',
       NULL, 6500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '슈가파우더'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '슈가파우더 500g' AND p.brand = '백설');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '화이트와인 750ml', '요리용', NULL, 750, 'ML',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '화이트와인'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '화이트와인 750ml' AND p.brand = '요리용');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '화이트와인 1.5L', '요리용 대', NULL, 1500, 'ML',
       NULL, 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '화이트와인'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '화이트와인 1.5L' AND p.brand = '요리용 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '카레가루 100g', '오뚜기', NULL, 100, 'G',
       NULL, 2800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '카레가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '카레가루 100g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '카레가루 200g', '오뚜기 대', NULL, 200, 'G',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '카레가루'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '카레가루 200g' AND p.brand = '오뚜기 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '매실청 500ml', '청정원', NULL, 500, 'ML',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '매실청'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '매실청 500ml' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '매실청 1L', '청정원 대', NULL, 1000, 'ML',
       NULL, 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '매실청'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '매실청 1L' AND p.brand = '청정원 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '메이플시럽 250ml', '캐나다산', NULL, 250, 'ML',
       NULL, 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '메이플시럽'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '메이플시럽 250ml' AND p.brand = '캐나다산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '메이플시럽 500ml', '캐나다산 대', NULL, 500, 'ML',
       NULL, 16900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '메이플시럽'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '메이플시럽 500ml' AND p.brand = '캐나다산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '배즙 500ml', '국내산', NULL, 500, 'ML',
       NULL, 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '배즙'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '배즙 500ml' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '배즙 1L', '국내산 대', NULL, 1000, 'ML',
       NULL, 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '기름·양념·향신료' JOIN ingredient i ON i.name = '배즙'
WHERE m.id IN (1, 2) AND m.name IN ('삼청 마트', '하안 마트')
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '배즙 1L' AND p.brand = '국내산 대');

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

-- 양념류(액체·가루·고추장·된장)와 쌀은 자동 담기에서 기본 제외하고 사용자가 고르는 선택 재료로 둔다.
-- 위 INSERT는 이미 있는 행을 건너뛰므로, 이미 만들어진 DB에도 반영되게 UPDATE로 맞춘다(여러 번 실행해도 같은 결과).
-- 밀가루·부침가루·카레가루·우유처럼 요리의 주재료가 되는 가루·액체는 필수 재료로 둔다.
UPDATE recipe_ingredient ri JOIN ingredient i ON i.id = ri.ingredient_id
SET ri.required = FALSE
WHERE ri.required = TRUE AND i.name IN ('간장', '국간장', '식용유', '올리브유', '참기름', '들기름', '고추기름', '식초', '맛술', '물엿', '굴소스', '우스터소스', '액젓', '바닐라익스트랙', '고춧가루', '설탕', '소금', '후추', '통후추', '다진마늘', '들깻가루', '시나몬', '파프리카 가루', '페페론치노', '월계수잎', '슈가파우더', '케첩', '마요네즈', '머스터드', '겨자', '통깨', '고추장', '된장', '쌀');
