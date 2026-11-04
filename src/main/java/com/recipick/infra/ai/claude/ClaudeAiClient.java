package com.recipick.infra.ai.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.AiFridgeTools;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiFridgeAnswer;
import com.recipick.infra.ai.dto.AiPlannedMeal;
import com.recipick.infra.ai.dto.AiRecipeCandidate;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiToolResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Claude API 호출만 담당한다. 지시문은 domain이 활성 버전을 골라 넘기고, 고른 레시피를 실제로 쓸 수 있는지는 domain Service가 다시 확인한다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.client", havingValue = "claude")
public class ClaudeAiClient implements AiClient {

    // 냉장고 파먹기 지시문(AiPromptType.FRIDGE)이 이 이름으로 Tool을 부르라고 안내하므로, 이름을 바꾸면 지시문도 함께 바꾼다
    static final String FIND_RECIPES_TOOL = "find_recipes_by_ingredients";
    static final String FIND_PRICES_TOOL = "find_ingredient_prices";

    // 한 요청에서 Tool 결과를 돌려주는 횟수. 넘으면 끝없이 호출하는 것으로 보고 실패 처리한다
    private static final int MAX_TOOL_ROUNDS = 5;

    private static final Tool FIND_RECIPES = Tool.builder()
        .name(FIND_RECIPES_TOOL)
        .description("가진 재료 이름으로 만들 수 있는 레시피를 가진 재료를 많이 쓰고 부족한 필수 재료가 적은 순으로 최대 10개 찾는다. "
            + "레시피마다 가진 필수 재료와 부족한 필수 재료(ID 포함)를 돌려주고, 등록되지 않은 이름은 unknownIngredientNames로 돌려준다.")
        .inputSchema(inputSchema("ingredientNames", Map.of(
            "type", "array",
            "items", Map.of("type", "string"),
            "description", "사용자가 가진 재료 이름")))
        .build();

    private static final Tool FIND_PRICES = Tool.builder()
        .name(FIND_PRICES_TOOL)
        .description("회원의 이용 마트에서 재료별 판매 상품의 가격과 살 수 있는지를 조회한다. "
            + "unavailableReason이 비어 있으면 살 수 있고, NOT_SOLD_IN_MART·SOLD_OUT·SALE_STOPPED이면 살 수 없다.")
        .inputSchema(inputSchema("ingredientIds", Map.of(
            "type", "array",
            "items", Map.of("type", "integer"),
            "description", "find_recipes_by_ingredients가 돌려준 부족한 재료 ID")))
        .build();

    private final AnthropicClient client;
    private final ClaudeProperties properties;

    @Override
    public AiResult<List<AiExtractedMenu>> extractMenus(String instruction, String text, List<AiRecipeOption> recipes) {
        // 레시피 목록은 요청마다 같으므로 캐시해 입력 토큰 비용을 줄인다
        AiResult<List<MenuOutput>> result =
            call(MenuOutput.class, instruction + catalog(recipes), true, text, "메뉴 분석");
        if (!result.isSuccess()) {
            return AiResult.failure(result.failureType(), result.usage());
        }
        List<AiExtractedMenu> menus = result.content().stream()
            .flatMap(output -> output.menus().stream())
            .map(MenuItem::toExtractedMenu)
            .toList();
        return AiResult.success(menus, result.usage());
    }

    @Override
    public AiResult<AiRecipeConditions> extractConditions(String instruction, String text) {
        AiResult<List<ConditionOutput>> result =
            call(ConditionOutput.class, instruction, false, text, "조건 분석");
        if (!result.isSuccess()) {
            return AiResult.failure(result.failureType(), result.usage());
        }
        // 응답 블록이 없으면 조건이 비었는지 알 수 없으므로, 빈 조건으로 성공 처리하지 않는다
        if (result.content().isEmpty()) {
            log.warn("Claude 조건 분석 응답에 결과가 없음: model={}", result.usage().model());
            return AiResult.failure(AiFailureType.INVALID_RESPONSE, result.usage());
        }
        return AiResult.success(result.content().get(0).toConditions(), result.usage());
    }

    // 조건 없는 문장은 매번 같은 전체 레시피 목록(약 8천 토큰)을 보내므로 캐시해 입력 비용을 줄인다.
    // 조건으로 거른 후보도 같은 조건이면 목록이 같아 재사용되고, 최소 길이보다 짧으면 캐시되지 않아 손해가 없다
    @Override
    public AiResult<List<AiRecommendedRecipe>> recommendRecipes(String instruction, String text,
                                                                List<AiRecipeCandidate> candidates) {
        AiResult<List<RecommendOutput>> result =
            call(RecommendOutput.class, instruction + candidateCatalog(candidates), true, text, "레시피 추천");
        if (!result.isSuccess()) {
            return AiResult.failure(result.failureType(), result.usage());
        }
        // 응답 블록이 없으면 "맞는 레시피 없음"(빈 목록)과 구분할 수 없으므로, 빈 추천으로 성공 처리하지 않는다
        if (result.content().isEmpty()) {
            log.warn("Claude 레시피 추천 응답에 결과가 없음: model={}", result.usage().model());
            return AiResult.failure(AiFailureType.INVALID_RESPONSE, result.usage());
        }
        List<AiRecommendedRecipe> recommendations = result.content().stream()
            .flatMap(output -> output.recommendations().stream())
            .map(item -> new AiRecommendedRecipe(item.recipeId(), item.reason()))
            .toList();
        return AiResult.success(recommendations, result.usage());
    }

    // 자동 장보기 가능 레시피 목록은 요청마다 같아 조건 추천의 전체 후보처럼 캐시한다
    @Override
    public AiResult<List<AiPlannedMeal>> planMeals(String instruction, String text,
                                                   List<AiRecipeCandidate> candidates) {
        AiResult<List<MealPlanOutput>> result =
            call(MealPlanOutput.class, instruction + candidateCatalog(candidates), true, text, "식단 구성");
        if (!result.isSuccess()) {
            return AiResult.failure(result.failureType(), result.usage());
        }
        // 응답 블록이 없으면 "식단과 관계없는 문장"(빈 목록)과 구분할 수 없으므로, 빈 식단으로 성공 처리하지 않는다
        if (result.content().isEmpty()) {
            log.warn("Claude 식단 구성 응답에 결과가 없음: model={}", result.usage().model());
            return AiResult.failure(AiFailureType.INVALID_RESPONSE, result.usage());
        }
        List<AiPlannedMeal> meals = result.content().stream()
            .flatMap(output -> output.meals().stream())
            .map(MealItem::toPlannedMeal)
            .toList();
        return AiResult.success(meals, result.usage());
    }

    // AI가 Tool을 고르면 서버가 실행해 결과를 돌려주고, AI가 답변을 낼 때까지 반복한다.
    // 여러 번 부른 토큰·응답 시간은 이력 한 건으로 남기려고 합친다
    @Override
    public AiResult<AiFridgeAnswer> answerFridgeQuestion(String instruction, String text, List<String> ingredientNames,
                                                         AiFridgeTools tools) {
        // 지시문·등록 재료·Tool 정의는 요청마다 같아 캐시한다(Tool 정의는 시스템 프롬프트 앞에 놓여 함께 캐시된다)
        TextBlockParam system = TextBlockParam.builder()
            .text(instruction + String.join(", ", ingredientNames))
            .cacheControl(CacheControlEphemeral.builder().build())
            .build();
        StructuredMessageCreateParams.Builder<FridgeOutput> params = MessageCreateParams.builder()
            .model(properties.model())
            .maxTokens(properties.maxTokens())
            .outputConfig(FridgeOutput.class)
            .systemOfTextBlockParams(List.of(system))
            .addTool(FIND_RECIPES)
            .addTool(FIND_PRICES)
            .addUserMessage(text);

        AiUsage usage = null;
        for (int round = 0; ; round++) {
            long startedAt = System.nanoTime();
            StructuredMessage<FridgeOutput> message;
            try {
                message = client.messages().create(withLowEffort(params.build(), FridgeOutput.class));
            } catch (AnthropicException e) {
                AiFailureType failureType = classify(e);
                log.warn("Claude 냉장고 파먹기 호출 실패: type={}, round={}", failureType, round, e);
                return AiResult.failure(failureType,
                    plus(usage, AiUsage.failed(properties.model(), elapsedMs(startedAt))));
            }
            usage = plus(usage, toUsage(message, elapsedMs(startedAt)));
            if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                log.warn("Claude가 냉장고 파먹기를 거절함: model={}", usage.model());
                return AiResult.failure(AiFailureType.REFUSED, usage);
            }
            if (message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
                log.warn("Claude 냉장고 파먹기 응답이 최대 토큰에서 잘림: model={}, outputTokens={}",
                    usage.model(), usage.outputTokens());
                return AiResult.failure(AiFailureType.INVALID_RESPONSE, usage);
            }
            if (message.stopReason().filter(StopReason.TOOL_USE::equals).isPresent()) {
                if (round == MAX_TOOL_ROUNDS) {
                    log.warn("Claude 냉장고 파먹기 Tool 호출 횟수 초과: model={}, rounds={}", usage.model(), round);
                    return AiResult.failure(AiFailureType.TOOL_LIMIT_EXCEEDED, usage);
                }
                params.addMessage(message.rawMessage());
                params.addUserMessageOfBlockParams(runTools(message, tools));
                continue;
            }
            List<FridgeOutput> outputs = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .toList();
            if (outputs.isEmpty()) {
                log.warn("Claude 냉장고 파먹기 응답에 결과가 없음: model={}", usage.model());
                return AiResult.failure(AiFailureType.INVALID_RESPONSE, usage);
            }
            return AiResult.success(outputs.get(0).toAnswer(), usage);
        }
    }

    private List<ContentBlockParam> runTools(StructuredMessage<FridgeOutput> message, AiFridgeTools tools) {
        return message.content().stream()
            .flatMap(block -> block.toolUse().stream())
            .map(toolUse -> ContentBlockParam.ofToolResult(runTool(toolUse, tools)))
            .toList();
    }

    // 입력 형식이 틀리거나 없는 Tool이면 오류 결과로 돌려줘 AI가 고쳐서 다시 부르게 한다
    private ToolResultBlockParam runTool(ToolUseBlock toolUse, AiFridgeTools tools) {
        AiToolResult<?> result;
        try {
            result = switch (toolUse.name()) {
                case FIND_RECIPES_TOOL -> tools.findRecipes(
                    orEmpty(toolUse._input().convert(FindRecipesInput.class).ingredientNames()));
                case FIND_PRICES_TOOL -> tools.findIngredientPrices(
                    orEmpty(toolUse._input().convert(FindPricesInput.class).ingredientIds()));
                default -> AiToolResult.error("없는 기능입니다: " + toolUse.name());
            };
        } catch (IllegalArgumentException e) {
            log.warn("Claude Tool 입력 형식 오류: tool={}", toolUse.name(), e);
            result = AiToolResult.error("입력 형식이 잘못됐습니다. 설명에 맞는 형식으로 다시 호출하세요.");
        }
        ToolResultBlockParam.Builder block = ToolResultBlockParam.builder().toolUseId(toolUse.id());
        return result.isError()
            ? block.content(result.errorReason()).isError(true).build()
            : block.contentAsJson(result.content()).build();
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static AiUsage plus(AiUsage total, AiUsage usage) {
        return total == null ? usage : total.plus(usage);
    }

    private static Tool.InputSchema inputSchema(String name, Map<String, Object> property) {
        return Tool.InputSchema.builder()
            .properties(Tool.InputSchema.Properties.builder()
                .putAdditionalProperty(name, JsonValue.from(property))
                .build())
            .required(List.of(name))
            .build();
    }

    private <T> AiResult<List<T>> call(Class<T> outputType, String instruction, boolean cacheInstruction,
                                       String text, String task) {
        TextBlockParam.Builder system = TextBlockParam.builder().text(instruction);
        if (cacheInstruction) {
            system.cacheControl(CacheControlEphemeral.builder().build());
        }
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
            .model(properties.model())
            .maxTokens(properties.maxTokens())
            // SDK가 출력 레코드로 JSON 스키마를 만들어 보내므로, 응답이 항상 이 형태로 온다
            .outputConfig(outputType)
            .systemOfTextBlockParams(List.of(system.build()))
            .addUserMessage(text)
            .build();

        long startedAt = System.nanoTime();
        try {
            StructuredMessage<T> message = client.messages().create(withLowEffort(params, outputType));
            AiUsage usage = toUsage(message, elapsedMs(startedAt));
            if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                log.warn("Claude가 {}을 거절함: model={}", task, usage.model());
                return AiResult.failure(AiFailureType.REFUSED, usage);
            }
            // 생각과 응답이 같은 max_tokens를 나눠 써서, 잘리면 JSON이 깨지거나 응답 블록 없이 빈 목록이 성공처럼 보인다
            if (message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
                log.warn("Claude {} 응답이 최대 토큰에서 잘림: model={}, outputTokens={}",
                    task, usage.model(), usage.outputTokens());
                return AiResult.failure(AiFailureType.INVALID_RESPONSE, usage);
            }
            List<T> outputs = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .toList();
            return AiResult.success(outputs, usage);
        } catch (AnthropicException e) {
            AiFailureType failureType = classify(e);
            log.warn("Claude {} 호출 실패: type={}", task, failureType, e);
            return AiResult.failure(failureType, AiUsage.failed(properties.model(), elapsedMs(startedAt)));
        }
    }

    // 메뉴·조건 분석과 후보 고르기는 깊은 추론이 필요 없어 응답 시간을 줄이려고 effort를 낮춘다(Opus 5.5 기본값은 MEDIUM).
    // outputConfig(Class)가 스키마만 채우므로, 만든 설정을 꺼내 effort를 더한다.
    private <T> StructuredMessageCreateParams<T> withLowEffort(StructuredMessageCreateParams<T> params,
                                                                Class<T> outputType) {
        MessageCreateParams raw = params.rawParams();
        OutputConfig outputConfig = raw.outputConfig().orElseThrow().toBuilder()
            .effort(OutputConfig.Effort.LOW)
            .build();
        return new StructuredMessageCreateParams<>(
            outputType, raw.toBuilder().outputConfig(outputConfig).build());
    }

    private String catalog(List<AiRecipeOption> recipes) {
        return recipes.stream()
            .map(recipe -> recipe.id() + ": " + recipe.name())
            .collect(Collectors.joining("\n"));
    }

    private String candidateCatalog(List<AiRecipeCandidate> candidates) {
        return candidates.stream()
            .map(candidate -> candidate.id() + ": " + candidate.name()
                + " | " + orUnknown(candidate.cuisineType()) + " " + orUnknown(candidate.dishType())
                + " | " + (candidate.cookingMinutes() == null ? "?" : candidate.cookingMinutes() + "분")
                + " | 맵기 " + orUnknown(candidate.spicyLevel())
                + " | " + orUnknown(candidate.difficulty())
                + " | " + String.join(", ", candidate.requiredIngredients()))
            .collect(Collectors.joining("\n"));
    }

    // 등록되지 않은 속성은 AI가 지어내지 않도록 모른다고 표시한다
    private String orUnknown(Object value) {
        return value == null ? "?" : value.toString();
    }

    private AiUsage toUsage(StructuredMessage<?> message, long latencyMs) {
        Usage usage = message.usage();
        return new AiUsage(
            message.model().asString(),
            Math.toIntExact(usage.inputTokens()),
            Math.toIntExact(usage.outputTokens()),
            usage.cacheReadInputTokens().map(Math::toIntExact).orElse(0),
            latencyMs);
    }

    // SDK가 429·5xx를 정해진 횟수만큼 재시도한 뒤에도 실패한 경우만 여기로 온다
    private AiFailureType classify(AnthropicException e) {
        if (e instanceof RateLimitException) {
            return AiFailureType.RATE_LIMITED;
        }
        if (e instanceof AnthropicIoException && e.getCause() instanceof InterruptedIOException) {
            return AiFailureType.TIMEOUT;
        }
        if (e instanceof AnthropicInvalidDataException) {
            return AiFailureType.INVALID_RESPONSE;
        }
        return AiFailureType.UNAVAILABLE;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    // 구조화 출력은 모든 필드를 채워야 해서 null을 쓸 수 없으므로, 인분을 말하지 않은 경우를 0으로 받는다
    record MenuOutput(List<MenuItem> menus) {
    }

    record MenuItem(String mention, List<Long> recipeIds, int servings) {

        AiExtractedMenu toExtractedMenu() {
            return new AiExtractedMenu(mention, recipeIds, servings > 0 ? servings : null);
        }
    }

    // 구조화 출력은 null을 쓸 수 없으므로, 말하지 않은 조건을 빈 문자열·0·-1로 받는다.
    // 맵기 0은 '안 매움'이라는 조건이라 -1을 비어 있음으로 쓴다
    record ConditionOutput(
        String cuisineType,
        String dishType,
        int maxCookingMinutes,
        int maxSpicyLevel,
        List<String> excludedIngredients,
        int maxBudget,
        int servings
    ) {

        AiRecipeConditions toConditions() {
            return new AiRecipeConditions(
                blankToNull(cuisineType),
                blankToNull(dishType),
                maxCookingMinutes > 0 ? maxCookingMinutes : null,
                maxSpicyLevel >= 0 ? maxSpicyLevel : null,
                excludedIngredients,
                maxBudget > 0 ? maxBudget : null,
                servings > 0 ? servings : null);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }

    record RecommendOutput(List<RecommendItem> recommendations) {
    }

    record RecommendItem(Long recipeId, String reason) {
    }

    record MealPlanOutput(List<MealItem> meals) {
    }

    record MealItem(Long recipeId, int servings, String reason) {

        AiPlannedMeal toPlannedMeal() {
            return new AiPlannedMeal(recipeId, servings > 0 ? servings : null, reason);
        }
    }

    record FridgeOutput(String answer, List<Long> recipeIds) {

        AiFridgeAnswer toAnswer() {
            return new AiFridgeAnswer(answer, recipeIds == null ? List.of() : recipeIds);
        }
    }

    record FindRecipesInput(List<String> ingredientNames) {
    }

    record FindPricesInput(List<Long> ingredientIds) {
    }
}
