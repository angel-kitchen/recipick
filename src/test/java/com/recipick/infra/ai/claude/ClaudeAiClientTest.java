package com.recipick.infra.ai.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.recipick.infra.ai.AiFridgeTools;
import com.recipick.infra.ai.claude.ClaudeAiClient.ConditionOutput;
import com.recipick.infra.ai.claude.ClaudeAiClient.FridgeOutput;
import com.recipick.infra.ai.claude.ClaudeAiClient.MenuOutput;
import com.recipick.infra.ai.dto.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

class ClaudeAiClientTest {

    // 지시문 내용은 관리자가 바꾸는 값이라, 호출 구조만 보는 이 테스트는 고정 문구를 쓴다
    private static final String INSTRUCTION = "지시문\n";

    private final AnthropicClient client = mock(AnthropicClient.class, Answers.RETURNS_DEEP_STUBS);
    private final ClaudeAiClient claudeAiClient = new ClaudeAiClient(
        client, new ClaudeProperties("test-key", "claude-opus-5-5", Duration.ofSeconds(15), 2, 4096));

    @Test
    @DisplayName("응답이 최대 토큰에서 잘리면 메뉴를 읽지 않고 실패로 돌려준다")
    @SuppressWarnings("unchecked")
    void extractMenus_failsWhenStoppedAtMaxTokens() {
        // given
        StructuredMessage<MenuOutput> message = mock(StructuredMessage.class);
        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(800L);
        when(usage.outputTokens()).thenReturn(4096L);
        when(usage.cacheReadInputTokens()).thenReturn(Optional.empty());
        when(message.usage()).thenReturn(usage);
        when(message.model()).thenReturn(Model.of("claude-opus-5-5"));
        when(message.stopReason()).thenReturn(Optional.of(StopReason.MAX_TOKENS));
        // 생각에 토큰을 다 쓰면 응답 블록이 없어, 잘린 것을 확인하지 않으면 빈 목록이 성공처럼 보인다
        when(message.content()).thenReturn(List.of());
        when(client.messages().create(any(StructuredMessageCreateParams.class))).thenReturn(message);

        // when
        AiResult<List<AiExtractedMenu>> result = claudeAiClient.extractMenus(INSTRUCTION, 
            "김치찌개 2인분", List.of(new AiRecipeOption(1L, "김치찌개")));

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureType()).isEqualTo(AiFailureType.INVALID_RESPONSE);
        assertThat(result.usage().outputTokens()).isEqualTo(4096);
    }

    @Test
    @DisplayName("조건을 말하지 않아 빈 문자열·0·-1로 온 값은 null로 바꾼다")
    void conditionOutput_convertsMissingValuesToNull() {
        // given
        ConditionOutput output = new ConditionOutput("", " ", 0, -1, List.of(), List.of(), 0, 0);

        // when
        AiRecipeConditions conditions = output.toConditions();

        // then
        assertThat(conditions)
            .isEqualTo(new AiRecipeConditions(null, null, null, null, List.of(), List.of(), null, null));
    }

    @Test
    @DisplayName("맵기 0은 '안 매움'이라는 조건이므로 그대로 남긴다")
    void conditionOutput_keepsZeroSpicyLevel() {
        // when
        AiRecipeConditions conditions =
            new ConditionOutput("KOREAN", "STEW", 20, 0, List.of("계란"), List.of("오이"), 0, 0).toConditions();

        // then
        assertThat(conditions)
            .isEqualTo(new AiRecipeConditions("KOREAN", "STEW", 20, 0, List.of("계란"), List.of("오이"), null, null));
    }

    @Test
    @DisplayName("추천 응답 블록이 없으면 맞는 레시피가 없다는 뜻으로 오해하지 않도록 실패로 돌려준다")
    @SuppressWarnings("unchecked")
    void recommendRecipes_failsWhenNoResponseBlock() {
        // given
        StructuredMessage<ClaudeAiClient.RecommendOutput> message = mock(StructuredMessage.class);
        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(900L);
        when(usage.outputTokens()).thenReturn(10L);
        when(usage.cacheReadInputTokens()).thenReturn(Optional.empty());
        when(message.usage()).thenReturn(usage);
        when(message.model()).thenReturn(Model.of("claude-opus-5-5"));
        when(message.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
        when(message.content()).thenReturn(List.of());
        when(client.messages().create(any(StructuredMessageCreateParams.class))).thenReturn(message);

        // when
        AiResult<List<AiRecommendedRecipe>> result = claudeAiClient.recommendRecipes(INSTRUCTION, 
            "찌개 추천", List.of(kimchiStewCandidate()));

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureType()).isEqualTo(AiFailureType.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("추천 후보는 이름과 함께 속성·필수 재료를 한 줄로 보내고, 등록되지 않은 속성은 ?로 표시하며 캐시한다")
    @SuppressWarnings("unchecked")
    void recommendRecipes_sendsCandidateAttributes() {
        // given
        StructuredMessage<ClaudeAiClient.RecommendOutput> message = mock(StructuredMessage.class);
        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(900L);
        when(usage.outputTokens()).thenReturn(10L);
        when(usage.cacheReadInputTokens()).thenReturn(Optional.empty());
        when(message.usage()).thenReturn(usage);
        when(message.model()).thenReturn(Model.of("claude-opus-5-5"));
        when(message.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
        when(message.content()).thenReturn(List.of());
        when(client.messages().create(any(StructuredMessageCreateParams.class))).thenReturn(message);
        AiRecipeCandidate unknown = new AiRecipeCandidate(2L, "새 레시피", null, null, null, null, null, List.of());

        // when
        claudeAiClient.recommendRecipes(INSTRUCTION, "두부", List.of(kimchiStewCandidate(), unknown));

        // then
        ArgumentCaptor<StructuredMessageCreateParams<ClaudeAiClient.RecommendOutput>> params =
            ArgumentCaptor.forClass(StructuredMessageCreateParams.class);
        verify(client.messages()).create(params.capture());
        TextBlockParam system = params.getValue().rawParams().system().orElseThrow()
            .asTextBlockParams().get(0);
        assertThat(system.text())
            .contains("1: 김치찌개 | KOREAN STEW | 30분 | 맵기 2 | EASY | 김치, 두부")
            .contains("2: 새 레시피 | ? ? | ? | 맵기 ? | ? | ");
        assertThat(system.cacheControl()).isPresent();
    }

    private static AiRecipeCandidate kimchiStewCandidate() {
        return new AiRecipeCandidate(1L, "김치찌개", "KOREAN", "STEW", 30, 2, "EASY", List.of("김치", "두부"));
    }

    @Test
    @DisplayName("식단: 인분을 말하지 않아 0으로 온 값은 null로 바꿔 서버가 기본 인분을 쓰게 한다")
    void mealItem_convertsZeroServingsToNull() {
        // when
        AiPlannedMeal unspecified = new ClaudeAiClient.MealItem(1L, 0, "든든해요.").toPlannedMeal();
        AiPlannedMeal twoServings = new ClaudeAiClient.MealItem(2L, 2, "가벼워요.").toPlannedMeal();

        // then
        assertThat(unspecified).isEqualTo(new AiPlannedMeal(1L, null, "든든해요."));
        assertThat(twoServings.servings()).isEqualTo(2);
    }

    @Test
    @DisplayName("식단: 레시피 목록을 지시문 뒤에 붙여 캐시하고, 응답 블록이 없으면 빈 식단이 아니라 실패로 돌려준다")
    @SuppressWarnings("unchecked")
    void planMeals_cachesCatalogAndFailsWhenNoResponseBlock() {
        // given
        StructuredMessage<ClaudeAiClient.MealPlanOutput> message = mock(StructuredMessage.class);
        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(900L);
        when(usage.outputTokens()).thenReturn(10L);
        when(usage.cacheReadInputTokens()).thenReturn(Optional.empty());
        when(message.usage()).thenReturn(usage);
        when(message.model()).thenReturn(Model.of("claude-opus-5-5"));
        when(message.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
        when(message.content()).thenReturn(List.of());
        when(client.messages().create(any(StructuredMessageCreateParams.class))).thenReturn(message);

        // when
        AiResult<List<AiPlannedMeal>> result =
            claudeAiClient.planMeals(INSTRUCTION, "이번 주 저녁 3일치", List.of(kimchiStewCandidate()));

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureType()).isEqualTo(AiFailureType.INVALID_RESPONSE);
        ArgumentCaptor<StructuredMessageCreateParams<ClaudeAiClient.MealPlanOutput>> params =
            ArgumentCaptor.forClass(StructuredMessageCreateParams.class);
        verify(client.messages()).create(params.capture());
        TextBlockParam system = params.getValue().rawParams().system().orElseThrow()
            .asTextBlockParams().get(0);
        assertThat(system.text()).startsWith(INSTRUCTION)
            .contains("1: 김치찌개 | KOREAN STEW | 30분 | 맵기 2 | EASY | 김치, 두부");
        assertThat(system.cacheControl()).isPresent();
    }

    @Test
    @DisplayName("냉장고 파먹기: AI가 고른 Tool을 실행해 결과를 돌려주고, 답변이 나오면 여러 호출의 사용량을 합쳐 준다")
    @SuppressWarnings("unchecked")
    void answerFridgeQuestion_runsToolsUntilAnswer() {
        // given
        StructuredMessage<FridgeOutput> toolCall = fridgeMessage(StopReason.TOOL_USE,
            toolUseBlock(ClaudeAiClient.FIND_RECIPES_TOOL, Map.of("ingredientNames", List.of("두부"))));
        StructuredMessage<FridgeOutput> answer = fridgeMessage(StopReason.END_TURN,
            textBlock(new FridgeOutput("두부조림을 만들 수 있어요.", List.of(7L))));
        when(client.messages().create(any(StructuredMessageCreateParams.class))).thenReturn(toolCall, answer);
        RecordingTools tools = new RecordingTools();

        // when
        AiResult<AiFridgeAnswer> result =
            claudeAiClient.answerFridgeQuestion(INSTRUCTION, "두부 있어", List.of("두부", "김치"), tools);

        // then
        assertThat(tools.recipeSearches).containsExactly(List.of("두부"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.content()).isEqualTo(new AiFridgeAnswer("두부조림을 만들 수 있어요.", List.of(7L)));
        assertThat(result.usage().inputTokens()).isEqualTo(1000);
        ArgumentCaptor<StructuredMessageCreateParams<FridgeOutput>> params =
            ArgumentCaptor.forClass(StructuredMessageCreateParams.class);
        verify(client.messages(), times(2)).create(params.capture());
        TextBlockParam system = params.getValue().rawParams().system().orElseThrow().asTextBlockParams().get(0);
        assertThat(system.text()).endsWith("두부, 김치");
        assertThat(system.cacheControl()).isPresent();
        assertThat(params.getValue().rawParams().messages()).hasSize(3);
    }

    @Test
    @DisplayName("냉장고 파먹기: Tool 호출을 5번 주고받은 뒤에도 또 부르면 횟수 초과로 실패한다")
    @SuppressWarnings("unchecked")
    void answerFridgeQuestion_failsWhenToolRoundsExceeded() {
        // given
        StructuredMessage<FridgeOutput> toolCall = fridgeMessage(StopReason.TOOL_USE,
            toolUseBlock(ClaudeAiClient.FIND_PRICES_TOOL, Map.of("ingredientIds", List.of(3))));
        when(client.messages().create(any(StructuredMessageCreateParams.class))).thenReturn(toolCall);
        RecordingTools tools = new RecordingTools();

        // when
        AiResult<AiFridgeAnswer> result =
            claudeAiClient.answerFridgeQuestion(INSTRUCTION, "두부 있어", List.of("두부"), tools);

        // then
        assertThat(result.failureType()).isEqualTo(AiFailureType.TOOL_LIMIT_EXCEEDED);
        assertThat(tools.priceSearches).hasSize(5).containsOnly(List.of(3L));
        assertThat(result.usage().inputTokens()).isEqualTo(3000);
    }

    @SuppressWarnings("unchecked")
    private StructuredMessage<FridgeOutput> fridgeMessage(StopReason stopReason, StructuredContentBlock<FridgeOutput> block) {
        StructuredMessage<FridgeOutput> message = mock(StructuredMessage.class);
        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(500L);
        when(usage.outputTokens()).thenReturn(40L);
        when(usage.cacheReadInputTokens()).thenReturn(Optional.empty());
        when(message.usage()).thenReturn(usage);
        when(message.model()).thenReturn(Model.of("claude-opus-5-5"));
        when(message.stopReason()).thenReturn(Optional.of(stopReason));
        when(message.content()).thenReturn(List.of(block));
        Message raw = mock(Message.class);
        when(raw.toParam()).thenReturn(MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .content("tool")
            .build());
        when(message.rawMessage()).thenReturn(raw);
        return message;
    }

    @SuppressWarnings("unchecked")
    private StructuredContentBlock<FridgeOutput> toolUseBlock(String name, Map<String, Object> input) {
        ToolUseBlock toolUse = mock(ToolUseBlock.class);
        when(toolUse.id()).thenReturn("toolu_1");
        when(toolUse.name()).thenReturn(name);
        when(toolUse._input()).thenReturn(JsonValue.from(input));
        StructuredContentBlock<FridgeOutput> block = mock(StructuredContentBlock.class);
        when(block.toolUse()).thenReturn(Optional.of(toolUse));
        when(block.text()).thenReturn(Optional.empty());
        return block;
    }

    @SuppressWarnings("unchecked")
    private StructuredContentBlock<FridgeOutput> textBlock(FridgeOutput output) {
        StructuredTextBlock<FridgeOutput> text = mock(StructuredTextBlock.class);
        when(text.text()).thenReturn(output);
        StructuredContentBlock<FridgeOutput> block = mock(StructuredContentBlock.class);
        when(block.toolUse()).thenReturn(Optional.empty());
        when(block.text()).thenReturn(Optional.of(text));
        return block;
    }

    private static class RecordingTools implements AiFridgeTools {

        private final List<List<String>> recipeSearches = new ArrayList<>();
        private final List<List<Long>> priceSearches = new ArrayList<>();

        @Override
        public AiToolResult<AiFridgeRecipeSearch> findRecipes(List<String> ingredientNames) {
            recipeSearches.add(ingredientNames);
            return AiToolResult.success(new AiFridgeRecipeSearch(List.of(), List.of()));
        }

        @Override
        public AiToolResult<List<AiIngredientPrice>> findIngredientPrices(List<Long> ingredientIds) {
            priceSearches.add(ingredientIds);
            return AiToolResult.error("이용 마트가 설정되지 않았습니다.");
        }
    }
}
