package com.recipick.infra.ai.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.FridgeRecipe;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.IngredientRef;
import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Difficulty;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeBookmarkRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.domain.recipe.service.RecipeRecommendCondition;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.global.common.SaleUnit;
import com.recipick.infra.ai.AiFridgeTools;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFridgeAnswer;
import com.recipick.infra.ai.dto.AiFridgeRecipeSearch;
import com.recipick.infra.ai.dto.AiIngredientPrice;
import com.recipick.infra.ai.dto.AiRecipeCandidate;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiToolResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

// 실제 Claude API를 불러 평가 문장의 정답률·응답 시간·토큰을 잰다. 호출마다 비용이 들므로 평소 테스트와 CI에서는 건너뛰고,
// AI_EVAL=true와 ANTHROPIC_API_KEY를 넣었을 때만 돈다. 모델은 AI_EVAL_MODEL로 바꿔 같은 문장으로 비교한다.
// 정답률을 재는 측정이라 점수로 테스트를 실패시키지 않고, 결과는 build/ai-eval/에 표로 남긴다
@EnabledIfEnvironmentVariable(named = "AI_EVAL", matches = "true")
class ClaudeAiEvaluationTest {

    private static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    // 로컬 시드와 같은 100개 목록을 쓰려고 시드 SQL에서 레시피 이름을 순서대로 읽는다. ID는 시드 순서(1부터)다
    private static final Pattern SEED_RECIPE_NAME =
        Pattern.compile("^SELECT '([^']+)', '(?:KOREAN|WESTERN)'", Pattern.MULTILINE);
    // 전체 흐름 평가에서 서버와 같은 조건으로 거르려고 레시피 속성과 재료도 시드 SQL에서 읽는다
    private static final Pattern SEED_RECIPE = Pattern.compile(
        "^SELECT '([^']+)', '(KOREAN|WESTERN)', '([A-Z_]+)', (\\d+), (\\d+), '([A-Z]+)', (\\d+),", Pattern.MULTILINE);
    private static final Pattern SEED_RECIPE_INGREDIENT = Pattern.compile(
        "SELECT r\\.id, i\\.id, [\\d.]+, (TRUE|FALSE)\\s+FROM recipe r JOIN ingredient i ON i\\.name = '([^']+)'"
            + "\\s+WHERE r\\.name = '([^']+)'");

    private static final Pattern SEED_INGREDIENT =
        Pattern.compile("INSERT INTO ingredient \\(name, unit\\)\\s+SELECT '([^']+)', '([A-Z]+)'");
    // 냉장고 파먹기 평가에서 품절로 두는 재료. 이 재료가 부족한 레시피를 추천하면 답변에서 품절을 알려야 정답이다
    private static final String SOLD_OUT_INGREDIENT = "돼지고기 앞다리살";
    private static final Pattern WON = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> report = new ArrayList<>();

    private static AnthropicClient anthropicClient;
    private static ClaudeAiClient claudeAiClient;
    private static String model;
    // 같은 모델로 프롬프트만 바꿔 비교할 때 결과 파일을 구분하려고 붙이는 이름(예: recommend-v1)
    private static String label;
    private static List<AiRecipeOption> catalog;
    private static Map<Long, String> nameById;
    private static List<SeedRecipe> seedRecipes;

    @BeforeAll
    static void setUp() throws IOException {
        model = System.getenv().getOrDefault("AI_EVAL_MODEL", DEFAULT_MODEL);
        label = System.getenv().getOrDefault("AI_EVAL_LABEL", "");
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
        seedRecipes = loadSeedRecipes();
        report.add("# AI 평가 결과: " + model + (label.isBlank() ? "" : " (" + label + ")"));
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
        Path file = dir.resolve(model + (label.isBlank() ? "" : "-" + label) + "-" + stamp + ".md");
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

    // 조건 추천 ③: 서버가 조건으로 거른 후보 안에서 문장에 맞는 레시피를 고르는지, 맞는 게 없으면 빈 목록을 주는지 잰다.
    // AI는 후보 이름만 보므로 정답도 이름으로 판단할 수 있는 문장만 둔다
    // 대충·줄여·띄어쓰기 틀리게 쓴 문장도 추천이 나오는지 잰다. 문제는 한 단계가 아니라 이어진 흐름에서 생기므로
    // Facade와 같이 ① 조건 정리 → ② 서버 조건(RecipeRecommendCondition)과 재료 빼기로 후보 거르기 → ③ 후보 안에서 고르기를
    // 그대로 재현해 최종 추천으로 채점한다
    @Test
    @DisplayName("대충 쓴 문장: 조건 정리부터 후보 고르기까지 거친 최종 추천이 문장에 맞는지 잰다")
    void evaluateVagueRecommend() throws IOException {
        List<PickCase> cases = readCases("ai-eval/vague-cases.json", new TypeReference<>() {
        });
        List<Row> rows = new ArrayList<>();
        for (PickCase evalCase : cases) {
            AiResult<AiRecipeConditions> conditionResult = claudeAiClient.extractConditions(evalCase.text());
            if (!conditionResult.isSuccess()) {
                rows.add(new Row(evalCase.text(), false, "실패: " + conditionResult.failureType(), conditionResult));
                continue;
            }
            RecipeRecommendCondition condition = RecipeRecommendCondition.from(conditionResult.content());
            String conditionText = describeCondition(condition);
            List<SeedRecipe> candidates = seedRecipes.stream()
                .filter(recipe -> condition.matches(recipe.recipe()))
                .filter(recipe -> condition.excludedIngredients().stream().noneMatch(recipe::contains))
                .toList();
            if (candidates.isEmpty()) {
                rows.add(new Row(evalCase.text(), evalCase.accept().isEmpty(),
                    conditionText + " → NO_MATCH(후보 없음)", conditionResult));
                continue;
            }
            List<AiRecipeCandidate> options = candidates.stream().map(SeedRecipe::toCandidate).toList();
            AiResult<List<AiRecommendedRecipe>> pickResult = claudeAiClient.recommendRecipes(evalCase.text(), options);
            AiResult<?> combined = new AiResult<>(null, pickResult.failureType(),
                conditionResult.usage().plus(pickResult.usage()));
            if (!pickResult.isSuccess()) {
                rows.add(new Row(evalCase.text(), false,
                    conditionText + " → 실패: " + pickResult.failureType(), combined));
                continue;
            }
            // 서버도 후보 밖 ID는 버리므로 후보 안에서 고른 것만 최종 추천으로 본다
            Set<Long> candidateIds = options.stream().map(AiRecipeCandidate::id).collect(Collectors.toSet());
            List<AiRecommendedRecipe> picks = pickResult.content().stream()
                .filter(pick -> candidateIds.contains(pick.recipeId()))
                .toList();
            rows.add(new Row(evalCase.text(), evalCase.matches(picks, nameById),
                conditionText + " → 후보 " + candidates.size() + "개 → " + describePicks(picks), combined));
        }
        appendSection("대충 쓴 문장 (조건 정리 → 후보 거르기 → 고르기)", rows);
        assertThat(rows).hasSameSizeAs(cases);
    }

    @Test
    @DisplayName("조건 추천 후보 고르기: 후보 안에서 맞는 레시피를 고르고, 없으면 빈 목록을 주는지 잰다")
    void evaluateRecommendPicks() throws IOException {
        List<PickCase> cases = readCases("ai-eval/recommend-pick-cases.json", new TypeReference<>() {
        });
        Map<String, SeedRecipe> seedByName = seedRecipes.stream()
            .collect(Collectors.toMap(recipe -> recipe.recipe().getName(), Function.identity()));
        List<Row> rows = new ArrayList<>();
        for (PickCase evalCase : cases) {
            List<AiRecipeCandidate> candidates = evalCase.candidates().stream()
                .map(name -> seedByName.get(name).toCandidate())
                .toList();
            AiResult<List<AiRecommendedRecipe>> result = claudeAiClient.recommendRecipes(evalCase.text(), candidates);
            String answer = result.isSuccess() ? describePicks(result.content()) : "실패: " + result.failureType();
            rows.add(new Row(evalCase.text(), result.isSuccess() && evalCase.matches(result.content(), nameById),
                answer, result));
        }
        appendSection("조건 추천 후보 고르기 (③)", rows);
        assertThat(rows).hasSameSizeAs(cases);
    }

    @Test
    @DisplayName("냉장고 파먹기: Tool을 불러 가진 재료로 만들 레시피를 고르고, 가격·품절·마트 미설정을 Tool 결과대로 안내하는지 잰다")
    void evaluateFridge() throws IOException {
        List<FridgeCase> cases = readCases("ai-eval/fridge-cases.json", new TypeReference<>() {
        });
        RecipeService recipeService = seedRecipeService();
        List<String> ingredientNames = recipeService.findIngredientNames();
        List<Row> rows = new ArrayList<>();
        for (FridgeCase evalCase : cases) {
            SeedFridgeTools tools = new SeedFridgeTools(recipeService, evalCase.martSelected() != Boolean.FALSE);
            AiResult<AiFridgeAnswer> result =
                claudeAiClient.answerFridgeQuestion(evalCase.text(), ingredientNames, tools);
            if (!result.isSuccess()) {
                rows.add(new Row(evalCase.text(), false, tools.describeCalls() + " → 실패: " + result.failureType(),
                    result));
                continue;
            }
            // 서버도 레시피 찾기가 돌려주지 않은 ID는 버리고 3개까지만 남긴다
            List<FridgeRecipe> picks = result.content().recipeIds().stream().distinct()
                .map(tools.found::get).filter(Objects::nonNull).limit(3).toList();
            List<String> problems = evalCase.problems(result.content(), picks, tools);
            String names = picks.stream().map(FridgeRecipe::name).collect(Collectors.joining(", "));
            String answer = result.content().answer().replace("\n", " ");
            rows.add(new Row(evalCase.text(), problems.isEmpty(), tools.describeCalls() + " → "
                + (names.isEmpty() ? "(빈 목록)" : names) + " → " + answer
                + (problems.isEmpty() ? "" : " **[" + String.join(", ", problems) + "]**"), result));
        }
        appendSection("냉장고 파먹기 (Tool Calling)", rows);
        assertThat(rows).hasSameSizeAs(cases);
    }

    // 서버의 레시피 찾기를 그대로 쓰려고 실제 RecipeService에 시드 SQL의 재료·레시피를 넣은 저장소를 붙인다.
    // 재료·레시피 ID는 빈 DB에 시드를 넣은 순서(1부터)다
    private static RecipeService seedRecipeService() throws IOException {
        String productSql = readResource("/data-product-local.sql");
        Map<String, Ingredient> ingredients = new LinkedHashMap<>();
        Matcher ingredientMatcher = SEED_INGREDIENT.matcher(productSql);
        while (ingredientMatcher.find()) {
            Ingredient ingredient = Ingredient.builder()
                .name(ingredientMatcher.group(1))
                .unit(SaleUnit.valueOf(ingredientMatcher.group(2)))
                .build();
            ReflectionTestUtils.setField(ingredient, "id", (long) ingredients.size() + 1);
            ingredients.put(ingredient.getName(), ingredient);
        }
        Map<String, Recipe> recipes = new LinkedHashMap<>();
        seedRecipes.forEach(seed -> {
            ReflectionTestUtils.setField(seed.recipe(), "id", seed.id());
            recipes.put(seed.recipe().getName(), seed.recipe());
        });
        List<RecipeIngredient> recipeIngredients = new ArrayList<>();
        Matcher matcher = SEED_RECIPE_INGREDIENT.matcher(productSql);
        while (matcher.find()) {
            recipeIngredients.add(RecipeIngredient.builder()
                .recipe(recipes.get(matcher.group(3)))
                .ingredient(ingredients.get(matcher.group(2)))
                .amountPerServing(BigDecimal.ONE)
                .required(matcher.group(1).equals("TRUE"))
                .build());
        }
        IngredientRepository ingredientRepository = mock(IngredientRepository.class);
        when(ingredientRepository.findAll()).thenReturn(List.copyOf(ingredients.values()));
        when(ingredientRepository.findAll(any(Sort.class))).thenReturn(List.copyOf(ingredients.values()));
        RecipeRepository recipeRepository = mock(RecipeRepository.class);
        when(recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE)).thenReturn(List.copyOf(recipes.values()));
        RecipeIngredientRepository recipeIngredientRepository = mock(RecipeIngredientRepository.class);
        when(recipeIngredientRepository.findAllWithIngredientByRecipeIds(anyCollection())).thenReturn(recipeIngredients);
        return new RecipeService(recipeRepository, recipeIngredientRepository, ingredientRepository,
            mock(RecipeBookmarkRepository.class), Clock.systemDefaultZone());
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

    // ID는 loadCatalog와 같이 시드 순서(1부터)라 nameById로 이름을 찾을 수 있다
    private static List<SeedRecipe> loadSeedRecipes() throws IOException {
        Map<String, List<String>> ingredientsByRecipe = new HashMap<>();
        Map<String, List<String>> requiredByRecipe = new HashMap<>();
        Matcher ingredientMatcher = SEED_RECIPE_INGREDIENT.matcher(readResource("/data-product-local.sql"));
        while (ingredientMatcher.find()) {
            String recipeName = ingredientMatcher.group(3);
            ingredientsByRecipe.computeIfAbsent(recipeName, name -> new ArrayList<>()).add(ingredientMatcher.group(2));
            if (ingredientMatcher.group(1).equals("TRUE")) {
                requiredByRecipe.computeIfAbsent(recipeName, name -> new ArrayList<>()).add(ingredientMatcher.group(2));
            }
        }
        Matcher matcher = SEED_RECIPE.matcher(readResource("/data-recipe-local.sql"));
        List<SeedRecipe> recipes = new ArrayList<>();
        while (matcher.find()) {
            Recipe recipe = Recipe.builder()
                .name(matcher.group(1))
                .cuisine(CuisineType.valueOf(matcher.group(2)))
                .dishType(DishType.valueOf(matcher.group(3)))
                .baseServings(Integer.parseInt(matcher.group(4)))
                .cookTimeMin(Integer.parseInt(matcher.group(5)))
                .difficulty(Difficulty.valueOf(matcher.group(6)))
                .spicyLevel(Integer.parseInt(matcher.group(7)))
                .steps(List.of())
                .build();
            recipes.add(new SeedRecipe((long) recipes.size() + 1, recipe,
                ingredientsByRecipe.getOrDefault(recipe.getName(), List.of()),
                requiredByRecipe.getOrDefault(recipe.getName(), List.of())));
        }
        return recipes;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = ClaudeAiEvaluationTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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

    private static String describePicks(List<AiRecommendedRecipe> picks) {
        return picks.isEmpty()
            ? "(빈 목록)"
            : picks.stream().map(pick -> nameById.getOrDefault(pick.recipeId(), "목록 밖 " + pick.recipeId()))
                .collect(Collectors.joining(", "));
    }

    private static String describeCondition(RecipeRecommendCondition condition) {
        List<String> parts = new ArrayList<>();
        if (condition.cuisineType() != null) {
            parts.add(condition.cuisineType().name());
        }
        if (condition.dishType() != null) {
            parts.add(condition.dishType().name());
        }
        if (condition.maxCookingMinutes() != null) {
            parts.add(condition.maxCookingMinutes() + "분 이내");
        }
        if (condition.maxSpicyLevel() != null) {
            parts.add("맵기 " + condition.maxSpicyLevel() + " 이하");
        }
        if (!condition.excludedIngredients().isEmpty()) {
            parts.add(condition.excludedIngredients() + " 빼기");
        }
        return parts.isEmpty() ? "[조건 없음]" : parts.toString();
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

    // 서버의 재료 빼기와 같이 재료 이름에 빼는 이름이 들어 있으면(LIKE '%이름%') 그 레시피를 뺀다.
    // AI에게는 서버(RecipeService.findRequiredIngredientNames)와 같이 필수 재료만 넘긴다
    record SeedRecipe(Long id, Recipe recipe, List<String> ingredients, List<String> requiredIngredients) {

        boolean contains(String excludedName) {
            return ingredients.stream().anyMatch(ingredient -> ingredient.contains(excludedName));
        }

        AiRecipeCandidate toCandidate() {
            return new AiRecipeCandidate(id, recipe.getName(), recipe.getCuisine().name(),
                recipe.getDishType().name(), recipe.getCookTimeMin(), recipe.getSpicyLevel(),
                recipe.getDifficulty().name(), requiredIngredients);
        }
    }

    // 서버의 Tool 실행기(AiFridgeToolExecutor)와 같이 레시피 찾기가 돌려준 재료 ID만 가격을 조회한다.
    // 가격은 재료마다 다르게(1,000원 + ID×10) 줘서, 답변의 금액이 Tool 결과에서 온 것인지 확인한다
    static class SeedFridgeTools implements AiFridgeTools {

        private final RecipeService recipeService;
        private final boolean martSelected;
        private final Map<Long, FridgeRecipe> found = new HashMap<>();
        private final Map<Long, String> foundIngredientNames = new HashMap<>();
        private final List<Long> returnedPrices = new ArrayList<>();
        private int recipeCalls;
        private int priceCalls;

        SeedFridgeTools(RecipeService recipeService, boolean martSelected) {
            this.recipeService = recipeService;
            this.martSelected = martSelected;
        }

        @Override
        public AiToolResult<AiFridgeRecipeSearch> findRecipes(List<String> ingredientNames) {
            recipeCalls++;
            FridgeRecipeSearchResult result = recipeService.findRecipesByOwnedIngredients(ingredientNames);
            result.recipes().forEach(recipe -> {
                found.put(recipe.recipeId(), recipe);
                recipe.missingIngredients()
                    .forEach(ingredient -> foundIngredientNames.put(ingredient.ingredientId(), ingredient.name()));
            });
            return AiToolResult.success(new AiFridgeRecipeSearch(result.unknownIngredientNames(),
                result.recipes().stream().map(recipe -> new AiFridgeRecipeSearch.Recipe(recipe.recipeId(),
                    recipe.name(), toAi(recipe.ownedIngredients()), toAi(recipe.missingIngredients()))).toList()));
        }

        @Override
        public AiToolResult<List<AiIngredientPrice>> findIngredientPrices(List<Long> ingredientIds) {
            priceCalls++;
            List<Long> requested = ingredientIds.stream().distinct().filter(foundIngredientNames::containsKey).toList();
            if (requested.isEmpty()) {
                return AiToolResult.error("find_recipes_by_ingredients가 돌려준 부족한 재료 ID로 다시 호출하세요.");
            }
            if (!martSelected) {
                return AiToolResult.error("이용 마트가 설정되지 않아 가격·재고를 조회할 수 없습니다. 이용 마트를 먼저 설정하도록 안내하세요.");
            }
            return AiToolResult.success(requested.stream().map(id -> {
                long price = 1000 + id * 10;
                returnedPrices.add(price);
                String name = foundIngredientNames.get(id);
                return new AiIngredientPrice(id, name + " 상품", price, "1EA",
                    SOLD_OUT_INGREDIENT.equals(name) ? "SOLD_OUT" : null);
            }).toList());
        }

        String describeCalls() {
            return "[찾기 " + recipeCalls + "·가격 " + priceCalls + "]";
        }

        // 답변의 금액은 Tool이 준 가격 하나이거나 그 합계여야 한다(여러 재료를 더해 말할 수 있다)
        boolean pricesFromTools(String answer) {
            Set<Long> allowed = new HashSet<>(List.of(0L));
            for (Long price : returnedPrices) {
                Set<Long> next = new HashSet<>(allowed);
                allowed.forEach(sum -> next.add(sum + price));
                allowed = next;
            }
            Matcher matcher = WON.matcher(answer);
            while (matcher.find()) {
                if (!allowed.contains(Long.parseLong(matcher.group(1).replace(",", "")))) {
                    return false;
                }
            }
            return true;
        }

        private static List<AiFridgeRecipeSearch.Ingredient> toAi(List<IngredientRef> ingredients) {
            return ingredients.stream()
                .map(ingredient -> new AiFridgeRecipeSearch.Ingredient(ingredient.ingredientId(), ingredient.name()))
                .toList();
        }
    }

    // accept가 비어 있으면 만들 레시피가 없는 문장이라 빈 목록이어야 정답이다. 그 밖에는 첫 번째(가장 추천하는) 레시피가 accept 안에
    // 있어야 한다. 최대 3개를 고르면 나머지는 가진 재료를 덜 쓰는 대안이라 정답 목록으로 막지 않는다.
    // 추가로 안내해야 할 말(mustMention), 품절 안내, 가격 조회, Tool에 없는 금액을 함께 확인한다
    record FridgeCase(String text, List<String> accept, Boolean martSelected, List<String> mustMention) {

        List<String> problems(AiFridgeAnswer answer, List<FridgeRecipe> picks, SeedFridgeTools tools) {
            List<String> problems = new ArrayList<>();
            List<String> names = picks.stream().map(FridgeRecipe::name).toList();
            if (accept.isEmpty() ? !answer.recipeIds().isEmpty() : names.isEmpty() || !accept.contains(names.get(0))) {
                problems.add("레시피");
            }
            if (mustMention != null && !mustMention.stream().allMatch(answer.answer()::contains)) {
                problems.add("안내 누락 " + mustMention);
            }
            boolean hasMissing = picks.stream().anyMatch(recipe -> !recipe.missingIngredients().isEmpty());
            if (martSelected != Boolean.FALSE && hasMissing && tools.priceCalls == 0) {
                problems.add("가격 미조회");
            }
            boolean needsSoldOut = martSelected != Boolean.FALSE && picks.stream()
                .flatMap(recipe -> recipe.missingIngredients().stream())
                .anyMatch(ingredient -> ingredient.name().equals(SOLD_OUT_INGREDIENT));
            if (needsSoldOut && !answer.answer().contains("품절")) {
                problems.add("품절 안내 누락");
            }
            if (!tools.pricesFromTools(answer.answer())) {
                problems.add("Tool에 없는 금액");
            }
            return problems;
        }
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

    // accept가 비어 있으면 후보 중 맞는 레시피가 없는 문장이라 빈 목록이어야 정답이다.
    // 그 밖에는 하나 이상 골라야 하고, 고른 레시피가 모두 accept 안에 있어야 정답이다(후보 밖 ID도 틀림)
    record PickCase(String text, List<String> candidates, List<String> accept) {

        boolean matches(List<AiRecommendedRecipe> picks, Map<Long, String> nameById) {
            List<String> names = picks.stream().map(pick -> nameById.get(pick.recipeId())).toList();
            if (accept.isEmpty()) {
                return names.isEmpty();
            }
            return !names.isEmpty() && accept.containsAll(names);
        }
    }
}
