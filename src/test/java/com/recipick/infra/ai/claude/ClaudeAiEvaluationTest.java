package com.recipick.infra.ai.claude;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// 실제 Claude API를 불러 평가 문장의 정답률·응답 시간·토큰을 잰다. 호출마다 비용이 들므로 평소 테스트와 CI에서는 건너뛰고,
// AI_EVAL=true와 ANTHROPIC_API_KEY를 넣었을 때만 돈다. 모델은 AI_EVAL_MODEL로 바꿔 같은 문장으로 비교한다.
// 정답률을 재는 측정이라 점수로 테스트를 실패시키지 않고, 결과는 build/ai-eval/에 표로 남긴다
@EnabledIfEnvironmentVariable(named = "AI_EVAL", matches = "true")
class ClaudeAiEvaluationTest {

    private static final String DEFAULT_MODEL = "claude-opus-5-5";
    // 로컬 시드와 같은 100개 목록을 쓰려고 시드 SQL에서 레시피 이름을 순서대로 읽는다. ID는 시드 순서(1부터)다
    private static final Pattern SEED_RECIPE_NAME =
        Pattern.compile("^SELECT '([^']+)', '(?:KOREAN|WESTERN)'", Pattern.MULTILINE);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> report = new ArrayList<>();

    private static AnthropicClient anthropicClient;
    private static ClaudeAiClient claudeAiClient;
    private static String model;
    private static List<AiRecipeOption> catalog;
    private static Map<Long, String> nameById;

    @BeforeAll
    static void setUp() throws IOException {
        model = System.getenv().getOrDefault("AI_EVAL_MODEL", DEFAULT_MODEL);
        // 평가 결과로 운영 모델을 고르므로 타임아웃·재시도·최대 토큰은 ClaudeProperties 기본값과 같게 둔다
        ClaudeProperties properties = new ClaudeProperties(
            System.getenv("ANTHROPIC_API_KEY"), model, Duration.ofSeconds(15), 2, 4096);
        anthropicClient = AnthropicOkHttpClient.builder()
            .apiKey(properties.apiKey())
            .timeout(properties.timeout())
            .maxRetries(properties.maxRetries())
            .build();
        claudeAiClient = new ClaudeAiClient(anthropicClient, properties);
        catalog = loadCatalog();
        nameById = catalog.stream().collect(Collectors.toMap(AiRecipeOption::id, AiRecipeOption::name));
        report.add("# AI 평가 결과: " + model);
        report.add("");
        report.add("- 실행 시각: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        report.add("- 레시피 목록: " + catalog.size() + "개");
    }

    @AfterAll
    static void writeReport() throws IOException {
        anthropicClient.close();
        Path dir = Path.of("build", "ai-eval");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve(model + "-" + stamp + ".md");
        Files.writeString(file, String.join("\n", report) + "\n");
        System.out.println("AI 평가 결과: " + file.toAbsolutePath());
    }

    @Test
    @DisplayName("자연어 장보기: 문장에서 메뉴·인분을 맞게 고르는지 잰다")
    void evaluateAutoCart() throws IOException {
        List<AutoCartCase> cases = readCases("ai-eval/auto-cart-cases.json", new TypeReference<>() {
        });
        List<Row> rows = new ArrayList<>();
        for (AutoCartCase evalCase : cases) {
            AiResult<List<AiExtractedMenu>> result = claudeAiClient.extractMenus(evalCase.text(), catalog);
            String answer = result.isSuccess() ? describeMenus(result.content()) : "실패: " + result.failureType();
            rows.add(new Row(evalCase.text(), result.isSuccess() && evalCase.matches(result.content(), nameById),
                answer, result));
        }
        appendSection("자연어 장보기 (메뉴·인분)", rows);
        assertThat(rows).hasSameSizeAs(cases);
    }

    @Test
    @DisplayName("조건 추천: 문장에서 조건 다섯 가지를 맞게 정리하는지 잰다")
    void evaluateConditions() throws IOException {
        List<ConditionCase> cases = readCases("ai-eval/recommend-cases.json", new TypeReference<>() {
        });
        List<Row> rows = new ArrayList<>();
        for (ConditionCase evalCase : cases) {
            AiResult<AiRecipeConditions> result = claudeAiClient.extractConditions(evalCase.text());
            String answer = result.isSuccess() ? result.content().toString() : "실패: " + result.failureType();
            rows.add(new Row(evalCase.text(), result.isSuccess() && evalCase.matches(result.content()),
                answer, result));
        }
        appendSection("조건 추천 (조건 분석)", rows);
        assertThat(rows).hasSameSizeAs(cases);
    }

    private static List<AiRecipeOption> loadCatalog() throws IOException {
        try (InputStream in = ClaudeAiEvaluationTest.class.getResourceAsStream("/data-recipe-local.sql")) {
            Matcher matcher = SEED_RECIPE_NAME.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<AiRecipeOption> options = new ArrayList<>();
            while (matcher.find()) {
                options.add(new AiRecipeOption((long) options.size() + 1, matcher.group(1)));
            }
            return options;
        }
    }

    private static <T> List<T> readCases(String path, TypeReference<List<T>> type) throws IOException {
        try (InputStream in = ClaudeAiEvaluationTest.class.getClassLoader().getResourceAsStream(path)) {
            return objectMapper.readValue(in, type);
        }
    }

    private static String describeMenus(List<AiExtractedMenu> menus) {
        return menus.stream()
            .map(menu -> menu.recipeIds().stream().map(nameById::get).toList() + "×" + menu.servings())
            .collect(Collectors.joining(", "));
    }

    private static void appendSection(String title, List<Row> rows) {
        long passed = rows.stream().filter(Row::passed).count();
        long failed = rows.stream().filter(row -> !row.result().isSuccess()).count();
        List<Long> latencies = rows.stream().map(row -> row.result().usage().latencyMs()).sorted().toList();
        report.add("");
        report.add("## " + title);
        report.add("");
        report.add("| 항목 | 값 |");
        report.add("| --- | --- |");
        report.add("| 정답 | " + passed + " / " + rows.size() + " (" + percent(passed, rows.size()) + ") |");
        report.add("| 호출 실패 | " + failed + " |");
        report.add("| 응답 시간 평균·p95 | " + average(latencies) + "ms · " + p95(latencies) + "ms |");
        report.add("| 입력·출력·캐시 토큰 합계 | " + sum(rows, AiUsage::inputTokens) + " · "
            + sum(rows, AiUsage::outputTokens) + " · " + sum(rows, AiUsage::cacheReadTokens) + " |");
        report.add("");
        report.add("| 문장 | 결과 | AI 응답 | 응답 시간(ms) |");
        report.add("| --- | --- | --- | --- |");
        rows.forEach(row -> report.add("| " + row.text() + " | " + (row.passed() ? "O" : "X") + " | "
            + row.answer().replace("|", "/") + " | " + row.result().usage().latencyMs() + " |"));
    }

    private static String percent(long part, int total) {
        return Math.round(part * 1000.0 / total) / 10.0 + "%";
    }

    private static long average(List<Long> values) {
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static long p95(List<Long> sorted) {
        return sorted.isEmpty() ? 0 : sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
    }

    private static long sum(List<Row> rows, Function<AiUsage, Integer> tokens) {
        return rows.stream().map(row -> tokens.apply(row.result().usage())).filter(Objects::nonNull)
            .mapToLong(Integer::longValue).sum();
    }

    record Row(String text, boolean passed, String answer, AiResult<?> result) {
    }

    record ExpectedMenu(List<String> accept, Integer servings) {
    }

    // 메뉴마다 허용하는 레시피 이름 안에서만 골랐는지, 인분이 맞는지, 기대하지 않은 메뉴를 더 고르지 않았는지 본다.
    // "파스타"처럼 하나로 정할 수 없는 문장은 허용 이름을 여러 개 두어 후보로 여러 개를 고른 경우도 정답으로 본다
    record AutoCartCase(String text, List<ExpectedMenu> menus) {

        boolean matches(List<AiExtractedMenu> extracted, Map<Long, String> nameById) {
            List<AiExtractedMenu> matched = extracted.stream().filter(menu -> !menu.recipeIds().isEmpty()).toList();
            if (matched.size() != menus.size()) {
                return false;
            }
            List<AiExtractedMenu> remaining = new ArrayList<>(matched);
            for (ExpectedMenu expected : menus) {
                AiExtractedMenu hit = remaining.stream()
                    .filter(menu -> expected.accept().containsAll(
                        menu.recipeIds().stream().map(nameById::get).toList()))
                    .filter(menu -> Objects.equals(menu.servings(), expected.servings()))
                    .findFirst()
                    .orElse(null);
                if (hit == null) {
                    return false;
                }
                remaining.remove(hit);
            }
            return true;
        }
    }

    record ExpectedConditions(String cuisineType, String dishType, Integer maxCookingMinutes, Integer maxSpicyLevel,
                              List<String> excludedIngredients) {
    }

    // 다섯 조건이 모두 같아야 정답이다. 말하지 않은 조건을 채우거나(예: 맵기를 마음대로 정함) 빠뜨리면 틀린 것으로 본다
    record ConditionCase(String text, ExpectedConditions expected) {

        boolean matches(AiRecipeConditions actual) {
            return Objects.equals(expected.cuisineType(), actual.cuisineType())
                && Objects.equals(expected.dishType(), actual.dishType())
                && Objects.equals(expected.maxCookingMinutes(), actual.maxCookingMinutes())
                && Objects.equals(expected.maxSpicyLevel(), actual.maxSpicyLevel())
                && toSet(expected.excludedIngredients()).equals(toSet(actual.excludedIngredients()));
        }

        private static Set<String> toSet(List<String> names) {
            return names == null
                ? Set.of()
                : names.stream().map(String::strip).collect(Collectors.toCollection(HashSet::new));
        }
    }
}
