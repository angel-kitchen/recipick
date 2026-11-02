import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ingredients.csv, products.csv, recipe-ingredients.csv를 읽어 로컬·시연용 상품·재료·레시피 재료
 * 초기 데이터 SQL(data-product-local.sql)을 만든다.
 *
 * <p>프로젝트 루트에서 컴파일 없이 실행한다 (JDK 17 이상의 단일 파일 실행).
 * <pre>
 * java scripts/recipe-seed/GenerateProductSeed.java
 * </pre>
 *
 * <p>CSV 컬럼
 * <br>ingredients.csv: name, unit(G/ML/EA), category
 * <br>products.csv: ingredient, name, brand, sale_unit_amount, sale_unit, price, image_url(없으면 빈 칸)
 * <br>recipe-ingredients.csv: recipe, ingredient, amount_per_serving(1인분 기준), required(T/F)
 * <br>모든 상품은 시연 마트 두 곳에 똑같이 들어간다. 값에 쉼표가 들어가면 안 된다.
 */
public class GenerateProductSeed {

    private static final Path DIR = Path.of("scripts/recipe-seed");
    private static final Path OUT = Path.of("src/main/resources/data-product-local.sql");
    private static final List<String> MARTS = List.of("삼청 마트", "하안 마트");
    private static final List<String> CATEGORIES = List.of(
            "채소·과일", "육류·가공육", "수산·건어물", "두부·계란·유제품", "쌀·곡물·가루",
            "면·파스타·떡", "빵·가공식품", "장류·소스", "기름·양념·향신료");
    // 생성 결과의 순서가 매번 같도록 순서가 유지되는 Map을 쓴다
    private static final Map<String, String> RENAMED = new LinkedHashMap<>();

    static {
        RENAMED.put("채소", "채소·과일");
        RENAMED.put("육류", "육류·가공육");
    }
    private static final List<String> RETIRED = List.of("양념·소스", "면·가공식품");

    private record Ingredient(String name, String unit, String category) {}

    private record Product(String ingredient, String name, String brand, String amount, String unit,
                           String price, String image) {}

    private record RecipeIngredient(String recipe, String ingredient, String amount, boolean required) {}

    public static void main(String[] args) throws IOException {
        List<Ingredient> ingredients = new ArrayList<>();
        for (String[] f : read("ingredients.csv", 3)) {
            ingredients.add(new Ingredient(f[0], f[1], f[2]));
        }
        List<Product> products = new ArrayList<>();
        for (String[] f : read("products.csv", 7)) {
            products.add(new Product(f[0], f[1], f[2], f[3], f[4], f[5], f[6]));
        }
        List<RecipeIngredient> recipeIngredients = new ArrayList<>();
        for (String[] f : read("recipe-ingredients.csv", 4)) {
            if (!f[3].equals("T") && !f[3].equals("F")) {
                throw new IllegalStateException("recipe-ingredients.csv의 required는 T 또는 F여야 합니다: " + f[0] + " " + f[1] + " -> " + f[3]);
            }
            recipeIngredients.add(new RecipeIngredient(f[0], f[1], f[2], f[3].equals("T")));
        }
        validate(ingredients, products, recipeIngredients);

        Map<String, Ingredient> byName = new LinkedHashMap<>();
        ingredients.forEach(i -> byName.put(i.name(), i));

        StringBuilder sb = new StringBuilder();
        sb.append("-- 로컬·시연용 상품·재료·레시피 재료 초기 데이터. scripts/recipe-seed/GenerateProductSeed.java로 생성하며 직접 고치지 않는다.\n");
        sb.append("-- 이미 있는 행은 건너뛰므로 서버를 다시 띄워도 중복으로 쌓이지 않는다.\n");
        sb.append("-- 반드시 data.sql(마트)과 data-recipe-local.sql(레시피) 다음에 읽는다.\n");
        sb.append("-- 이 파일을 다시 읽을 때 아래 레시피의 자동 담기가 다시 켜진다(관리자가 끈 설정도 되돌려진다).\n\n");

        // 예전 카테고리 이름을 쓰던 DB를 위해 이름을 바꾼다
        RENAMED.forEach((from, to) -> sb.append("UPDATE category SET name = ").append(q(to)).append(", updated_at = NOW()\n")
                .append("WHERE name = ").append(q(from)).append(" AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM category WHERE name = ")
                .append(q(to)).append(") c);\n"));
        sb.append('\n');
        for (String category : CATEGORIES) {
            sb.append("INSERT INTO category (name, deleted, created_at, updated_at)\n")
                    .append("SELECT ").append(q(category)).append(", FALSE, NOW(), NOW()\n")
                    .append("FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = ").append(q(category)).append(");\n");
        }
        sb.append('\n');

        for (Ingredient i : ingredients) {
            sb.append("INSERT INTO ingredient (name, unit)\n")
                    .append("SELECT ").append(q(i.name())).append(", ").append(q(i.unit())).append("\n")
                    .append("FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name = ").append(q(i.name())).append(");\n");
        }
        sb.append('\n');

        for (RecipeIngredient r : recipeIngredients) {
            sb.append("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, amount_per_serving, required)\n")
                    .append("SELECT r.id, i.id, ").append(r.amount()).append(", ").append(r.required() ? "TRUE" : "FALSE").append("\n")
                    .append("FROM recipe r JOIN ingredient i ON i.name = ").append(q(r.ingredient())).append("\n")
                    .append("WHERE r.name = ").append(q(r.recipe())).append("\n")
                    .append("  AND NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id AND ri.ingredient_id = i.id);\n");
        }
        sb.append('\n');

        String martNames = String.join(", ", MARTS.stream().map(GenerateProductSeed::q).toList());
        for (Product p : products) {
            Ingredient i = byName.get(p.ingredient());
            String image = p.image().isEmpty() ? "NULL" : q(p.image());
            sb.append("INSERT INTO product (mart_id, category_id, ingredient_id, name, brand, description, sale_unit_amount, sale_unit,\n")
                    .append("                     image_url, price, status, total_quantity, deleted, created_at, updated_at)\n")
                    .append("SELECT m.id, c.id, i.id, ").append(q(p.name())).append(", ").append(q(p.brand())).append(", NULL, ")
                    .append(p.amount()).append(", ").append(q(p.unit())).append(",\n")
                    .append("       ").append(image).append(", ").append(p.price()).append(", 'SELLING', 100, FALSE, NOW(), NOW()\n")
                    .append("FROM mart m JOIN category c ON c.name = ").append(q(i.category()))
                    .append(" JOIN ingredient i ON i.name = ").append(q(i.name())).append("\n")
                    .append("WHERE m.id IN (1, 2) AND m.name IN (").append(martNames).append(")\n")
                    .append("  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.mart_id = m.id AND p.name = ").append(q(p.name()))
                    .append(" AND p.brand = ").append(q(p.brand())).append(");\n");
        }
        sb.append('\n');

        // 예전 카테고리에 들어 있던 상품을 새 카테고리로 옮긴다
        sb.append("-- 예전 시드로 만든 DB의 상품을 새 카테고리로 옮기고, 비게 된 예전 카테고리는 숨긴다.\n");
        for (String category : CATEGORIES) {
            List<String> names = ingredients.stream().filter(i -> i.category().equals(category)).map(i -> q(i.name())).toList();
            sb.append("UPDATE product p JOIN ingredient i ON i.id = p.ingredient_id JOIN category c ON c.name = ").append(q(category)).append("\n")
                    .append("SET p.category_id = c.id, p.updated_at = NOW()\n")
                    .append("WHERE i.name IN (").append(String.join(", ", names)).append(") AND p.category_id <> c.id;\n");
        }
        for (String category : RETIRED) {
            sb.append("UPDATE category SET deleted = TRUE, updated_at = NOW() WHERE name = ").append(q(category))
                    .append(" AND NOT EXISTS (SELECT 1 FROM product p WHERE p.category_id = category.id);\n");
        }
        sb.append('\n');

        Set<String> recipes = new LinkedHashSet<>();
        recipeIngredients.forEach(r -> recipes.add(q(r.recipe())));
        sb.append("UPDATE recipe SET auto_cart_enabled = TRUE, updated_at = NOW()\n")
                .append("WHERE name IN (").append(String.join(", ", recipes)).append(");\n");

        // 하안 마트 좌표를 하안동 안쪽으로 옮긴다. 예전 좌표일 때만 바꿔 직접 고친 값은 덮어쓰지 않는다.
        sb.append("\n-- 하안 마트 좌표가 철산동 쪽에 있어 하안동 안에서 현재 위치를 찾아도 이용 가능 지역과 어긋났다. 하안동 안쪽으로 옮긴다.\n")
                .append("-- data.sql의 INSERT는 이미 있는 행을 건너뛰므로 기존 로컬 DB에는 UPDATE로 반영한다. 예전 좌표일 때만 바꿔 직접 고친 값은 덮어쓰지 않는다.\n")
                .append("UPDATE mart SET latitude = 37.4640000, longitude = 126.8760000, updated_at = NOW()\n")
                .append("WHERE id = 2 AND region_id = 2 AND latitude = 37.4710000 AND longitude = 126.8760000;\n");

        Files.writeString(OUT, sb.toString(), StandardCharsets.UTF_8);
        System.out.printf("재료 %d개, 상품 %d종(마트 %d곳), 레시피 재료 %d행, 레시피 %d개 -> %s%n",
                ingredients.size(), products.size(), MARTS.size(), recipeIngredients.size(), recipes.size(), OUT);
    }

    private static void validate(List<Ingredient> ingredients, List<Product> products, List<RecipeIngredient> recipeIngredients) {
        Map<String, Ingredient> byName = new LinkedHashMap<>();
        for (Ingredient i : ingredients) {
            if (byName.put(i.name(), i) != null) {
                throw new IllegalStateException("재료 이름이 중복됩니다: " + i.name());
            }
            if (!CATEGORIES.contains(i.category())) {
                throw new IllegalStateException("알 수 없는 카테고리: " + i.name() + " -> " + i.category());
            }
            if (!List.of("G", "ML", "EA").contains(i.unit())) {
                throw new IllegalStateException("알 수 없는 단위: " + i.name() + " -> " + i.unit());
            }
        }
        Map<String, Integer> productCount = new LinkedHashMap<>();
        Set<String> productKeys = new LinkedHashSet<>();
        for (Product p : products) {
            Ingredient i = byName.get(p.ingredient());
            if (i == null) {
                throw new IllegalStateException("products.csv에 없는 재료: " + p.ingredient());
            }
            if (!i.unit().equals(p.unit())) {
                throw new IllegalStateException("재료와 상품의 단위가 다릅니다: " + p.name());
            }
            requirePositive("판매 단위 수량(" + p.name() + ")", p.amount());
            requirePositive("가격(" + p.name() + ")", p.price());
            if (!productKeys.add(p.name() + "|" + p.brand())) {
                throw new IllegalStateException("상품(이름+브랜드)이 중복됩니다: " + p.name() + " " + p.brand());
            }
            productCount.merge(p.ingredient(), 1, Integer::sum);
        }
        for (Ingredient i : ingredients) {
            if (!productCount.containsKey(i.name())) {
                throw new IllegalStateException("이 재료를 파는 상품이 없습니다: " + i.name());
            }
        }
        Set<String> recipeIngredientKeys = new LinkedHashSet<>();
        for (RecipeIngredient r : recipeIngredients) {
            if (!byName.containsKey(r.ingredient())) {
                throw new IllegalStateException("recipe-ingredients.csv에 없는 재료: " + r.recipe() + " -> " + r.ingredient());
            }
            requirePositive("1인분 양(" + r.recipe() + " " + r.ingredient() + ")", r.amount());
            if (!recipeIngredientKeys.add(r.recipe() + "|" + r.ingredient())) {
                throw new IllegalStateException("레시피 재료가 중복됩니다: " + r.recipe() + " " + r.ingredient());
            }
        }
    }

    private static void requirePositive(String label, String value) {
        try {
            if (new BigDecimal(value).signum() <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException(label + "은(는) 0보다 큰 숫자여야 합니다: " + value);
        }
    }

    private static List<String[]> read(String file, int columns) throws IOException {
        List<String> lines = Files.readAllLines(DIR.resolve(file), StandardCharsets.UTF_8);
        List<String[]> rows = new ArrayList<>();
        for (int n = 1; n < lines.size(); n++) {
            String line = lines.get(n);
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(",", -1);
            if (fields.length != columns) {
                throw new IllegalStateException(file + " " + (n + 1) + "행: 칸이 " + fields.length + "개입니다(" + columns + "개여야 함)");
            }
            for (int c = 0; c < fields.length; c++) {
                fields[c] = fields[c].strip().replace("﻿", "");
            }
            rows.add(fields);
        }
        return rows;
    }

    private static String q(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
