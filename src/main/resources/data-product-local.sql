-- 로컬·시연용 상품·재료·레시피 재료 초기 데이터. 이미 있는 행은 건너뛰므로 서버를 다시 띄워도 중복으로 쌓이지 않는다.
-- 반드시 data.sql(마트)과 data-recipe-local.sql(레시피) 다음에 읽는다.
-- 이 파일을 다시 읽을 때 아래 레시피의 자동 담기가 다시 켜진다(관리자가 끈 설정도 되돌려진다).

INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '채소', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '채소');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '육류', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '육류');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '수산·건어물', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '수산·건어물');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '두부·계란·유제품', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '두부·계란·유제품');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '양념·소스', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '양념·소스');
INSERT INTO category (name, deleted, created_at, updated_at)
SELECT '면·가공식품', FALSE, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '면·가공식품');

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

INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김치 500g', '종가', NULL, 500, 'G',
       '/images/products/kimchi.jpg', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '김치'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김치 500g' AND p.brand = '종가');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김치 900g', '풀무원', NULL, 900, 'G',
       '/images/products/kimchi.jpg', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '김치'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김치 900g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지고기 앞다리살 500g', '국내산', NULL, 500, 'G',
       '/images/products/pork-shoulder.jpg', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지고기 앞다리살 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지고기 앞다리살 600g', '한돈', NULL, 600, 'G',
       '/images/products/pork-shoulder.jpg', 13900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지고기 앞다리살 600g' AND p.brand = '한돈');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '두부 300g', '풀무원', NULL, 300, 'G',
       '/images/products/tofu.jpg', 2300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '두부'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '두부 300g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '두부 500g', 'CJ', NULL, 500, 'G',
       '/images/products/tofu.jpg', 3100, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '두부'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '두부 500g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대파 300g', '국내산', NULL, 300, 'G',
       '/images/products/green-onion.jpg', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '대파'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대파 300g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대파 500g', '친환경', NULL, 500, 'G',
       '/images/products/green-onion.jpg', 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '대파'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대파 500g' AND p.brand = '친환경');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양파 1kg', '국내산', NULL, 1000, 'G',
       '/images/products/onion.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '양파'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양파 1kg' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양파 1.5kg', '국내산 대', NULL, 1500, 'G',
       '/images/products/onion.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '양파'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양파 1.5kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진마늘 200g', '국내산', NULL, 200, 'G',
       '/images/products/minced-garlic.jpg', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '다진마늘'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진마늘 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진마늘 500g', '국내산 대용량', NULL, 500, 'G',
       '/images/products/minced-garlic.jpg', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '다진마늘'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진마늘 500g' AND p.brand = '국내산 대용량');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고춧가루 200g', '청정원', NULL, 200, 'G',
       '/images/products/chili-powder.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '고춧가루'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고춧가루 200g' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고춧가루 500g', '해찬들', NULL, 500, 'G',
       '/images/products/chili-powder.jpg', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '고춧가루'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고춧가루 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '된장 500g', '해찬들', NULL, 500, 'G',
       '/images/products/doenjang.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '된장'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '된장 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '된장 1kg', '사조', NULL, 1000, 'G',
       '/images/products/doenjang.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '된장'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '된장 1kg' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '애호박 1개', '국내산', NULL, 1, 'EA',
       '/images/products/zucchini.jpg', 1500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '애호박'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '애호박 1개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '애호박 2개', '친환경', NULL, 2, 'EA',
       '/images/products/zucchini.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '애호박'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '애호박 2개' AND p.brand = '친환경');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청양고추 100g', '국내산', NULL, 100, 'G',
       '/images/products/green-chili.jpg', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '청양고추'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청양고추 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청양고추 200g', '국내산 대', NULL, 200, 'G',
       '/images/products/green-chili.jpg', 3300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '청양고추'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청양고추 200g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국물용 멸치 200g', '남해', NULL, 200, 'G',
       '/images/products/anchovy.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '국물용 멸치'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국물용 멸치 200g' AND p.brand = '남해');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국물용 멸치 400g', '통영', NULL, 400, 'G',
       '/images/products/anchovy.jpg', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '국물용 멸치'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국물용 멸치 400g' AND p.brand = '통영');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건미역 50g', '완도', NULL, 50, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '건미역'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건미역 50g' AND p.brand = '완도');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건미역 100g', '기장', NULL, 100, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '건미역'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건미역 100g' AND p.brand = '기장');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 국거리 300g', '국내산 한우', NULL, 300, 'G',
       '/images/products/beef-soup.jpg', 17900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '소고기 국거리'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 국거리 300g' AND p.brand = '국내산 한우');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 국거리 400g', '호주산', NULL, 400, 'G',
       '/images/products/beef-soup.jpg', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '소고기 국거리'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 국거리 400g' AND p.brand = '호주산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국간장 500ml', '청정원', NULL, 500, 'ML',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '국간장'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국간장 500ml' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국간장 900ml', '샘표', NULL, 900, 'ML',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '국간장'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국간장 900ml' AND p.brand = '샘표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참기름 320ml', '오뚜기', NULL, 320, 'ML',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '참기름'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참기름 320ml' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참기름 500ml', 'CJ', NULL, 500, 'ML',
       NULL, 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '참기름'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참기름 500ml' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추장 500g', '해찬들', NULL, 500, 'G',
       '/images/products/gochujang.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '고추장'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추장 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추장 1kg', 'CJ', NULL, 1000, 'G',
       '/images/products/gochujang.jpg', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '고추장'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추장 1kg' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '간장 500ml', '샘표', NULL, 500, 'ML',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '간장'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '간장 500ml' AND p.brand = '샘표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '간장 900ml', '몽고', NULL, 900, 'ML',
       NULL, 7500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '간장'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '간장 900ml' AND p.brand = '몽고');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '설탕 1kg', 'CJ', NULL, 1000, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '설탕'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '설탕 1kg' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '설탕 3kg', '삼양', NULL, 3000, 'G',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '설탕'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '설탕 3kg' AND p.brand = '삼양');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '계란 10개', '특란', NULL, 10, 'EA',
       '/images/products/egg.jpg', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '계란'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '계란 10개' AND p.brand = '특란');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '계란 30개', '대란', NULL, 30, 'EA',
       '/images/products/egg.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '계란'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '계란 30개' AND p.brand = '대란');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당근 2개', '국내산', NULL, 2, 'EA',
       '/images/products/carrot.jpg', 2200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '당근'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당근 2개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당근 4개', '국내산 대', NULL, 4, 'EA',
       '/images/products/carrot.jpg', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '당근'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당근 4개' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소금 500g', '꽃소금', NULL, 500, 'G',
       '/images/products/salt.jpg', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '소금'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소금 500g' AND p.brand = '꽃소금');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소금 1kg', '천일염', NULL, 1000, 'G',
       '/images/products/salt.jpg', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '소금'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소금 1kg' AND p.brand = '천일염');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식용유 900ml', 'CJ 해표', NULL, 900, 'ML',
       '/images/products/cooking-oil.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '식용유'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식용유 900ml' AND p.brand = 'CJ 해표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식용유 1.8L', '오뚜기', NULL, 1800, 'ML',
       '/images/products/cooking-oil.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '식용유'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식용유 1.8L' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우젓 200g', '육젓', NULL, 200, 'G',
       '/images/products/salted-shrimp.jpg', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우젓'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우젓 200g' AND p.brand = '육젓');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우젓 500g', '추젓', NULL, 500, 'G',
       '/images/products/salted-shrimp.jpg', 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우젓'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우젓 500g' AND p.brand = '추젓');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '스파게티면 500g', '폰타나', NULL, 500, 'G',
       '/images/products/spaghetti.jpg', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '스파게티면'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '스파게티면 500g' AND p.brand = '폰타나');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '스파게티면 500g', '데체코', NULL, 500, 'G',
       '/images/products/spaghetti.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '스파게티면'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '스파게티면 500g' AND p.brand = '데체코');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '베이컨 200g', '대림', NULL, 200, 'G',
       '/images/products/bacon.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '베이컨'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '베이컨 200g' AND p.brand = '대림');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '베이컨 400g', '롯데', NULL, 400, 'G',
       '/images/products/bacon.jpg', 8200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '베이컨'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '베이컨 400g' AND p.brand = '롯데');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파마산치즈 가루 100g', '서울우유', NULL, 100, 'G',
       '/images/products/parmesan.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파마산치즈 가루 100g' AND p.brand = '서울우유');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파마산치즈 가루 200g', '데이지', NULL, 200, 'G',
       '/images/products/parmesan.jpg', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파마산치즈 가루 200g' AND p.brand = '데이지');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생크림 250ml', '서울우유', NULL, 250, 'ML',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '생크림'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생크림 250ml' AND p.brand = '서울우유');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생크림 500ml', '매일', NULL, 500, 'ML',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '생크림'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생크림 500ml' AND p.brand = '매일');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후추 50g', '오뚜기', NULL, 50, 'G',
       '/images/products/black-pepper.jpg', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '후추'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후추 50g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후추 100g', '백설', NULL, 100, 'G',
       '/images/products/black-pepper.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '후추'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후추 100g' AND p.brand = '백설');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '올리브유 500ml', '사조', NULL, 500, 'ML',
       '/images/products/olive-oil.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '올리브유'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '올리브유 500ml' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '올리브유 1L', '오뚜기', NULL, 1000, 'ML',
       '/images/products/olive-oil.jpg', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '올리브유'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '올리브유 1L' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토소스 400g', '오뚜기', NULL, 400, 'G',
       '/images/products/tomato-sauce.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '토마토소스'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토소스 400g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토소스 600g', '폰타나', NULL, 600, 'G',
       '/images/products/tomato-sauce.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '토마토소스'
WHERE m.name = '삼청 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토소스 600g' AND p.brand = '폰타나');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김치 500g', '종가', NULL, 500, 'G',
       '/images/products/kimchi.jpg', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '김치'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김치 500g' AND p.brand = '종가');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '김치 900g', '풀무원', NULL, 900, 'G',
       '/images/products/kimchi.jpg', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '김치'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '김치 900g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지고기 앞다리살 500g', '국내산', NULL, 500, 'G',
       '/images/products/pork-shoulder.jpg', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지고기 앞다리살 500g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '돼지고기 앞다리살 600g', '한돈', NULL, 600, 'G',
       '/images/products/pork-shoulder.jpg', 13900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '돼지고기 앞다리살'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '돼지고기 앞다리살 600g' AND p.brand = '한돈');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '두부 300g', '풀무원', NULL, 300, 'G',
       '/images/products/tofu.jpg', 2300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '두부'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '두부 300g' AND p.brand = '풀무원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '두부 500g', 'CJ', NULL, 500, 'G',
       '/images/products/tofu.jpg', 3100, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '두부'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '두부 500g' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대파 300g', '국내산', NULL, 300, 'G',
       '/images/products/green-onion.jpg', 2500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '대파'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대파 300g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '대파 500g', '친환경', NULL, 500, 'G',
       '/images/products/green-onion.jpg', 4200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '대파'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '대파 500g' AND p.brand = '친환경');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양파 1kg', '국내산', NULL, 1000, 'G',
       '/images/products/onion.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '양파'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양파 1kg' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '양파 1.5kg', '국내산 대', NULL, 1500, 'G',
       '/images/products/onion.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '양파'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '양파 1.5kg' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진마늘 200g', '국내산', NULL, 200, 'G',
       '/images/products/minced-garlic.jpg', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '다진마늘'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진마늘 200g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '다진마늘 500g', '국내산 대용량', NULL, 500, 'G',
       '/images/products/minced-garlic.jpg', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '다진마늘'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '다진마늘 500g' AND p.brand = '국내산 대용량');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고춧가루 200g', '청정원', NULL, 200, 'G',
       '/images/products/chili-powder.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '고춧가루'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고춧가루 200g' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고춧가루 500g', '해찬들', NULL, 500, 'G',
       '/images/products/chili-powder.jpg', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '고춧가루'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고춧가루 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '된장 500g', '해찬들', NULL, 500, 'G',
       '/images/products/doenjang.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '된장'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '된장 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '된장 1kg', '사조', NULL, 1000, 'G',
       '/images/products/doenjang.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '된장'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '된장 1kg' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '애호박 1개', '국내산', NULL, 1, 'EA',
       '/images/products/zucchini.jpg', 1500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '애호박'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '애호박 1개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '애호박 2개', '친환경', NULL, 2, 'EA',
       '/images/products/zucchini.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '애호박'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '애호박 2개' AND p.brand = '친환경');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청양고추 100g', '국내산', NULL, 100, 'G',
       '/images/products/green-chili.jpg', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '청양고추'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청양고추 100g' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '청양고추 200g', '국내산 대', NULL, 200, 'G',
       '/images/products/green-chili.jpg', 3300, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '청양고추'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '청양고추 200g' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국물용 멸치 200g', '남해', NULL, 200, 'G',
       '/images/products/anchovy.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '국물용 멸치'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국물용 멸치 200g' AND p.brand = '남해');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국물용 멸치 400g', '통영', NULL, 400, 'G',
       '/images/products/anchovy.jpg', 10900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '국물용 멸치'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국물용 멸치 400g' AND p.brand = '통영');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건미역 50g', '완도', NULL, 50, 'G',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '건미역'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건미역 50g' AND p.brand = '완도');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '건미역 100g', '기장', NULL, 100, 'G',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '건미역'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '건미역 100g' AND p.brand = '기장');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 국거리 300g', '국내산 한우', NULL, 300, 'G',
       '/images/products/beef-soup.jpg', 17900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '소고기 국거리'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 국거리 300g' AND p.brand = '국내산 한우');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소고기 국거리 400g', '호주산', NULL, 400, 'G',
       '/images/products/beef-soup.jpg', 12900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '소고기 국거리'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소고기 국거리 400g' AND p.brand = '호주산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국간장 500ml', '청정원', NULL, 500, 'ML',
       NULL, 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '국간장'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국간장 500ml' AND p.brand = '청정원');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '국간장 900ml', '샘표', NULL, 900, 'ML',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '국간장'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '국간장 900ml' AND p.brand = '샘표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참기름 320ml', '오뚜기', NULL, 320, 'ML',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '참기름'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참기름 320ml' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '참기름 500ml', 'CJ', NULL, 500, 'ML',
       NULL, 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '참기름'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '참기름 500ml' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추장 500g', '해찬들', NULL, 500, 'G',
       '/images/products/gochujang.jpg', 5900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '고추장'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추장 500g' AND p.brand = '해찬들');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '고추장 1kg', 'CJ', NULL, 1000, 'G',
       '/images/products/gochujang.jpg', 9900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '고추장'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '고추장 1kg' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '간장 500ml', '샘표', NULL, 500, 'ML',
       NULL, 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '간장'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '간장 500ml' AND p.brand = '샘표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '간장 900ml', '몽고', NULL, 900, 'ML',
       NULL, 7500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '간장'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '간장 900ml' AND p.brand = '몽고');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '설탕 1kg', 'CJ', NULL, 1000, 'G',
       NULL, 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '설탕'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '설탕 1kg' AND p.brand = 'CJ');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '설탕 3kg', '삼양', NULL, 3000, 'G',
       NULL, 7900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '설탕'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '설탕 3kg' AND p.brand = '삼양');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '계란 10개', '특란', NULL, 10, 'EA',
       '/images/products/egg.jpg', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '계란'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '계란 10개' AND p.brand = '특란');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '계란 30개', '대란', NULL, 30, 'EA',
       '/images/products/egg.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '계란'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '계란 30개' AND p.brand = '대란');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당근 2개', '국내산', NULL, 2, 'EA',
       '/images/products/carrot.jpg', 2200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '당근'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당근 2개' AND p.brand = '국내산');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '당근 4개', '국내산 대', NULL, 4, 'EA',
       '/images/products/carrot.jpg', 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '채소' JOIN ingredient i ON i.name = '당근'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '당근 4개' AND p.brand = '국내산 대');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소금 500g', '꽃소금', NULL, 500, 'G',
       '/images/products/salt.jpg', 1800, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '소금'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소금 500g' AND p.brand = '꽃소금');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '소금 1kg', '천일염', NULL, 1000, 'G',
       '/images/products/salt.jpg', 3500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '소금'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '소금 1kg' AND p.brand = '천일염');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식용유 900ml', 'CJ 해표', NULL, 900, 'ML',
       '/images/products/cooking-oil.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '식용유'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식용유 900ml' AND p.brand = 'CJ 해표');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '식용유 1.8L', '오뚜기', NULL, 1800, 'ML',
       '/images/products/cooking-oil.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '식용유'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '식용유 1.8L' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우젓 200g', '육젓', NULL, 200, 'G',
       '/images/products/salted-shrimp.jpg', 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우젓'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우젓 200g' AND p.brand = '육젓');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '새우젓 500g', '추젓', NULL, 500, 'G',
       '/images/products/salted-shrimp.jpg', 11900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '수산·건어물' JOIN ingredient i ON i.name = '새우젓'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '새우젓 500g' AND p.brand = '추젓');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '스파게티면 500g', '폰타나', NULL, 500, 'G',
       '/images/products/spaghetti.jpg', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '스파게티면'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '스파게티면 500g' AND p.brand = '폰타나');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '스파게티면 500g', '데체코', NULL, 500, 'G',
       '/images/products/spaghetti.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '스파게티면'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '스파게티면 500g' AND p.brand = '데체코');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '베이컨 200g', '대림', NULL, 200, 'G',
       '/images/products/bacon.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '베이컨'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '베이컨 200g' AND p.brand = '대림');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '베이컨 400g', '롯데', NULL, 400, 'G',
       '/images/products/bacon.jpg', 8200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '육류' JOIN ingredient i ON i.name = '베이컨'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '베이컨 400g' AND p.brand = '롯데');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파마산치즈 가루 100g', '서울우유', NULL, 100, 'G',
       '/images/products/parmesan.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파마산치즈 가루 100g' AND p.brand = '서울우유');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '파마산치즈 가루 200g', '데이지', NULL, 200, 'G',
       '/images/products/parmesan.jpg', 8500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '파마산치즈 가루'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '파마산치즈 가루 200g' AND p.brand = '데이지');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생크림 250ml', '서울우유', NULL, 250, 'ML',
       NULL, 3900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '생크림'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생크림 250ml' AND p.brand = '서울우유');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '생크림 500ml', '매일', NULL, 500, 'ML',
       NULL, 6900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '두부·계란·유제품' JOIN ingredient i ON i.name = '생크림'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '생크림 500ml' AND p.brand = '매일');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후추 50g', '오뚜기', NULL, 50, 'G',
       '/images/products/black-pepper.jpg', 2900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '후추'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후추 50g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '후추 100g', '백설', NULL, 100, 'G',
       '/images/products/black-pepper.jpg', 4500, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '후추'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '후추 100g' AND p.brand = '백설');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '올리브유 500ml', '사조', NULL, 500, 'ML',
       '/images/products/olive-oil.jpg', 8900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '올리브유'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '올리브유 500ml' AND p.brand = '사조');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '올리브유 1L', '오뚜기', NULL, 1000, 'ML',
       '/images/products/olive-oil.jpg', 15900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '양념·소스' JOIN ingredient i ON i.name = '올리브유'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '올리브유 1L' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토소스 400g', '오뚜기', NULL, 400, 'G',
       '/images/products/tomato-sauce.jpg', 3200, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '토마토소스'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토소스 400g' AND p.brand = '오뚜기');
INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,
                     image_url, price, status, total_quantity, deleted, created_at, updated_at)
SELECT m.id, c.id, i.id, '토마토소스 600g', '폰타나', NULL, 600, 'G',
       '/images/products/tomato-sauce.jpg', 4900, 'SELLING', 100, FALSE, NOW(), NOW()
FROM mart m JOIN category c ON c.name = '면·가공식품' JOIN ingredient i ON i.name = '토마토소스'
WHERE m.name = '하안 마트'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = '토마토소스 600g' AND p.brand = '폰타나');

UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/kimchi.jpg', p.updated_at = NOW()
WHERE i.name = '김치' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/pork-shoulder.jpg', p.updated_at = NOW()
WHERE i.name = '돼지고기 앞다리살' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/tofu.jpg', p.updated_at = NOW()
WHERE i.name = '두부' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/green-onion.jpg', p.updated_at = NOW()
WHERE i.name = '대파' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/onion.jpg', p.updated_at = NOW()
WHERE i.name = '양파' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/minced-garlic.jpg', p.updated_at = NOW()
WHERE i.name = '다진마늘' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/chili-powder.jpg', p.updated_at = NOW()
WHERE i.name = '고춧가루' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/doenjang.jpg', p.updated_at = NOW()
WHERE i.name = '된장' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/zucchini.jpg', p.updated_at = NOW()
WHERE i.name = '애호박' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/green-chili.jpg', p.updated_at = NOW()
WHERE i.name = '청양고추' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/anchovy.jpg', p.updated_at = NOW()
WHERE i.name = '국물용 멸치' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/beef-soup.jpg', p.updated_at = NOW()
WHERE i.name = '소고기 국거리' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/gochujang.jpg', p.updated_at = NOW()
WHERE i.name = '고추장' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/egg.jpg', p.updated_at = NOW()
WHERE i.name = '계란' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/carrot.jpg', p.updated_at = NOW()
WHERE i.name = '당근' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/salt.jpg', p.updated_at = NOW()
WHERE i.name = '소금' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/cooking-oil.jpg', p.updated_at = NOW()
WHERE i.name = '식용유' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/salted-shrimp.jpg', p.updated_at = NOW()
WHERE i.name = '새우젓' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/spaghetti.jpg', p.updated_at = NOW()
WHERE i.name = '스파게티면' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/bacon.jpg', p.updated_at = NOW()
WHERE i.name = '베이컨' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/parmesan.jpg', p.updated_at = NOW()
WHERE i.name = '파마산치즈 가루' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/black-pepper.jpg', p.updated_at = NOW()
WHERE i.name = '후추' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/olive-oil.jpg', p.updated_at = NOW()
WHERE i.name = '올리브유' AND p.image_url IS NULL;
UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id
SET p.image_url = '/images/products/tomato-sauce.jpg', p.updated_at = NOW()
WHERE i.name = '토마토소스' AND p.image_url IS NULL;

UPDATE recipe SET auto_cart_enabled = TRUE, updated_at = NOW()
WHERE name IN ('김치찌개', '된장찌개', '미역국', '제육볶음', '계란말이', '계란찜', '까르보나라', '토마토파스타');
