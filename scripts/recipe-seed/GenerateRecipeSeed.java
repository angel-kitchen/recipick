import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * recipes.csv를 읽어 로컬·시연용 레시피 초기 데이터 SQL(data-recipe-local.sql)을 만든다.
 *
 * <p>프로젝트 루트에서 컴파일 없이 실행한다 (JDK 17 이상의 단일 파일 실행. record·Stream.toList()를 쓴다).
 * <pre>
 * java scripts/recipe-seed/GenerateRecipeSeed.java
 * java scripts/recipe-seed/GenerateRecipeSeed.java --allow-missing   # 다 채우기 전, 채운 행만 먼저 확인할 때
 * </pre>
 *
 * <p>CSV 컬럼: name, cuisine(KOREAN/WESTERN), dish_type, base_servings, cook_time_min,
 * difficulty(EASY/NORMAL/HARD), spicy_level(0~3), youtube_url, steps
 * <br>steps는 조리 순서를 " | "로 구분해 적는다. 예) 멸치 육수를 낸다 | 된장을 푼다 | 두부를 넣고 끓인다
 */
public class GenerateRecipeSeed {

    private static final Path CSV_PATH = Path.of("scripts/recipe-seed/recipes.csv");
    private static final Path SQL_PATH = Path.of("src/main/resources/data-recipe-local.sql");

    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>();
    private static final Set<String> DISH_TYPES =
        Set.of("SOUP", "STEW", "STIR_FRY", "RICE", "NOODLE", "SIDE", "PASTA", "ETC");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "NORMAL", "HARD");
    private static final int NAME_MAX_LENGTH = 50;
    private static final String STEP_SEPARATOR = "\\|";

    // 서버의 YoutubeUrlParser와 같은 규칙: youtube.com/watch?v=, youtu.be/, youtube.com/shorts/ 만 허용
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final String SHORT_HOST = "youtu.be";
    private static final String SHORTS_PATH = "/shorts/";

    static {
        EXPECTED.put("KOREAN", 70);
        EXPECTED.put("WESTERN", 30);
    }

    record Recipe(String name, String cuisine, String dishType, int baseServings, int cookTimeMin,
                  String difficulty, int spicyLevel, String youtubeVideoId, List<String> steps) {
    }

    public static void main(String[] args) throws IOException {
        boolean allowMissing = Arrays.asList(args).contains("--allow-missing");
        if (!Files.exists(CSV_PATH)) {
            System.err.println("프로젝트 루트에서 실행해 주세요. CSV를 찾을 수 없습니다: " + CSV_PATH);
            System.exit(1);
        }

        List<List<String>> rows;
        try {
            rows = parseCsv(Files.readString(CSV_PATH, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            System.err.println("SQL을 만들지 않았습니다. CSV 형식을 고쳐 주세요.");
            System.err.println("  - " + e.getMessage());
            System.exit(1);
            return;
        }
        List<String> header = rows.get(0);
        List<Recipe> recipes = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            Map<String, String> row = toMap(header, rows.get(i));
            int line = i + 1;
            if (row.get("youtube_url").isBlank() || row.get("steps").isBlank()) {
                missing.add(line + "행 " + row.get("name"));
                continue;
            }
            try {
                recipes.add(parseRow(row));
            } catch (IllegalArgumentException e) {
                errors.add(line + "행 " + row.get("name") + ": " + e.getMessage());
            }
        }

        duplicates(recipes, Recipe::name).forEach(name -> errors.add("이름 중복: " + name));
        duplicates(recipes, Recipe::youtubeVideoId).forEach(id -> errors.add("영상 중복: " + id));
        if (!allowMissing) {
            if (!missing.isEmpty()) {
                errors.add("유튜브 주소나 조리 순서가 비어 있는 행 " + missing.size() + "개: "
                    + String.join(", ", missing.subList(0, Math.min(10, missing.size())))
                    + (missing.size() > 10 ? " ..." : ""));
            }
            EXPECTED.forEach((cuisine, expected) -> {
                long count = recipes.stream().filter(recipe -> recipe.cuisine().equals(cuisine)).count();
                if (count != expected) {
                    errors.add(cuisine + " 레시피는 " + expected + "개여야 합니다 (현재 " + count + "개)");
                }
            });
        }

        if (!errors.isEmpty()) {
            System.err.println("SQL을 만들지 않았습니다. 아래 항목을 고쳐 주세요.");
            errors.forEach(error -> System.err.println("  - " + error));
            System.exit(1);
        }

        String notice = "-- 로컬·시연용 레시피 초기 데이터. "
            + "scripts/recipe-seed/GenerateRecipeSeed.java로 생성하며 직접 고치지 않는다.\n"
            + "-- 같은 이름의 레시피가 이미 있으면 건너뛰므로 서버를 다시 띄워도 중복으로 쌓이지 않는다.\n\n";
        String body = recipes.stream().map(GenerateRecipeSeed::toInsert).collect(Collectors.joining("\n"));
        Files.writeString(SQL_PATH, notice + body, StandardCharsets.UTF_8);

        long korean = recipes.stream().filter(recipe -> recipe.cuisine().equals("KOREAN")).count();
        System.out.println(SQL_PATH + " 생성: 한식 " + korean + "개, 양식 " + (recipes.size() - korean) + "개"
            + (missing.isEmpty() ? "" : " (비어 있어 건너뛴 행 " + missing.size() + "개)"));
    }

    private static Recipe parseRow(Map<String, String> row) {
        String name = row.get("name").strip();
        if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("레시피 이름은 1~" + NAME_MAX_LENGTH + "자여야 합니다: " + name);
        }
        String cuisine = requireIn(row.get("cuisine").strip(), EXPECTED.keySet(), "cuisine");
        String dishType = requireIn(row.get("dish_type").strip(), DISH_TYPES, "dish_type");
        String difficulty = requireIn(row.get("difficulty").strip(), DIFFICULTIES, "difficulty");
        int baseServings = parseInt(row.get("base_servings"), "base_servings", 1, 10);
        int cookTimeMin = parseInt(row.get("cook_time_min"), "cook_time_min", 1, Integer.MAX_VALUE);
        int spicyLevel = parseInt(row.get("spicy_level"), "spicy_level", 0, 3);
        List<String> steps = Arrays.stream(row.get("steps").split(STEP_SEPARATOR))
            .map(String::strip)
            .filter(step -> !step.isEmpty())
            .toList();
        // " | "처럼 구분자만 있으면 빈칸 검사를 통과해 조리 순서가 없는 레시피([])가 들어가므로 여기서 막는다
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("조리 순서를 한 단계 이상 적어야 합니다");
        }
        return new Recipe(name, cuisine, dishType, baseServings, cookTimeMin, difficulty, spicyLevel,
            extractVideoId(row.get("youtube_url")), steps);
    }

    private static String extractVideoId(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유튜브 영상 주소가 아닙니다: " + url);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String videoId = null;
        if (SHORT_HOST.equals(host)) {
            videoId = path.startsWith("/") ? path.substring(1) : null;
        } else if (YOUTUBE_HOSTS.contains(host)) {
            if (path.startsWith(SHORTS_PATH)) {
                videoId = path.substring(SHORTS_PATH.length());
            } else if ("/watch".equals(path) && uri.getQuery() != null) {
                videoId = Arrays.stream(uri.getQuery().split("&"))
                    .filter(pair -> pair.startsWith("v="))
                    .map(pair -> pair.substring(2))
                    .findFirst()
                    .orElse(null);
            }
        }
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            throw new IllegalArgumentException("유튜브 영상 주소가 아닙니다: " + url);
        }
        return videoId;
    }

    private static String toInsert(Recipe recipe) {
        String steps = toStepsJson(recipe.steps());
        String name = sqlString(recipe.name());
        return "INSERT INTO recipe (name, cuisine, dish_type, base_servings, cook_time_min, difficulty, spicy_level,\n"
            + "                    steps, youtube_video_id, status, auto_cart_enabled, created_at, updated_at)\n"
            + "SELECT " + name + ", '" + recipe.cuisine() + "', '" + recipe.dishType() + "', "
            + recipe.baseServings() + ", " + recipe.cookTimeMin() + ", '" + recipe.difficulty() + "', "
            + recipe.spicyLevel() + ",\n"
            + "       " + sqlString(steps) + ", '" + recipe.youtubeVideoId() + "', 'ACTIVE', FALSE, NOW(), NOW()\n"
            + "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM recipe WHERE name = " + name + ");\n";
    }

    // Recipe.steps(List<RecipeStep>) 컬럼 형식: [{"order":1,"description":"..."}]
    private static String toStepsJson(List<String> steps) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            items.add("{\"order\":" + (i + 1) + ",\"description\":\"" + jsonEscape(steps.get(i)) + "\"}");
        }
        return "[" + String.join(",", items) + "]";
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    // MySQL 문자열에서는 역슬래시도 이스케이프 문자라 JSON의 역슬래시가 사라지지 않도록 함께 이스케이프한다
    private static String sqlString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private static String requireIn(String value, Set<String> allowed, String field) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + " 값이 올바르지 않습니다: " + value);
        }
        return value;
    }

    private static int parseInt(String value, String field, int min, int max) {
        int number;
        try {
            number = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 값이 숫자가 아닙니다: " + value);
        }
        if (number < min || number > max) {
            throw new IllegalArgumentException(field + " 값이 허용 범위를 벗어났습니다: " + value);
        }
        return number;
    }

    private static <T> Set<T> duplicates(List<Recipe> recipes, Function<Recipe, T> key) {
        Map<T, Long> counts = recipes.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
        return counts.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Map<String, String> toMap(List<String> header, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            row.put(header.get(i), i < values.size() ? values.get(i) : "");
        }
        return row;
    }

    // 엑셀이 저장한 CSV(맨 앞 BOM, 큰따옴표로 감싼 칸, 칸 안의 쉼표·줄바꿈)를 그대로 읽는다
    private static List<List<String>> parseCsv(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        // 여는 큰따옴표가 닫히지 않으면 나머지 내용이 모두 한 칸으로 합쳐진 채 검사를 통과할 수 있어 여기서 막는다
        if (quoted) {
            throw new IllegalArgumentException(
                (rows.size() + 1) + "행에서 연 큰따옴표(\")가 파일 끝까지 닫히지 않았습니다");
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
