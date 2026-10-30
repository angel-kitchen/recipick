package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.response.MemberMeResponse.MembershipSummary;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.recipe.dto.response.AiRecipeRecommendResponse;
import com.recipick.domain.recipe.dto.response.AiRecipeRecommendResponse.RecommendedRecipe;
import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.repository.AiRequestLogRepository;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AiRecipeRecommendFacadeIntegrationTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9503L;
    private static final AiUsage CONDITION_USAGE = new AiUsage("claude-opus-5-5", 300, 60, 0, 700);
    private static final AiUsage PICK_USAGE = new AiUsage("claude-opus-5-5", 900, 200, 0, 1300);
    // 테스트용 레시피만 남도록 다른 데이터와 겹치지 않는 조건으로 고정한다
    private static final AiRecipeConditions CONDITIONS =
        new AiRecipeConditions("KOREAN", "STEW", 25, 1, List.of());

    @Autowired
    AiRecipeRecommendFacade aiRecipeRecommendFacade;
    @Autowired
    AiRequestLogRepository aiRequestLogRepository;
    @Autowired
    RecipeRepository recipeRepository;
    @Autowired
    IngredientRepository ingredientRepository;
    @Autowired
    RecipeIngredientRepository recipeIngredientRepository;
    @Autowired
    StringRedisTemplate redisTemplate;

    @MockitoBean
    AiClient aiClient;
    @MockitoBean
    MembershipService membershipService;

    private Recipe kimchiStew;
    private Recipe soybeanStew;
    private Ingredient cucumber;

    @BeforeEach
    void setUp() {
        kimchiStew = saveRecipe("AI추천 김치찌개", 21, 1);
        soybeanStew = saveRecipe("AI추천 된장찌개", 22, 0);
        cucumber = ingredientRepository.save(Ingredient.builder().name("AI추천테스트오이").unit(SaleUnit.EA).build());
        recipeIngredientRepository.save(RecipeIngredient.builder().recipe(kimchiStew).ingredient(cucumber)
            .amountPerServing(new BigDecimal("1")).required(true).build());
        given(membershipService.getSummary(eq(MEMBER_ID), any()))
            .willReturn(new MembershipSummary(true, "TRIAL", null));
    }

    @AfterEach
    void cleanUp() {
        aiRequestLogRepository.deleteAll(findLogs());
        recipeIngredientRepository.deleteAll(recipeIngredientRepository.findAllWithIngredient(kimchiStew.getId()));
        ingredientRepository.deleteById(cucumber.getId());
        recipeRepository.deleteAllById(List.of(kimchiStew.getId(), soybeanStew.getId()));
        redisTemplate.delete("ai:request:" + MEMBER_ID);
    }

    private Recipe saveRecipe(String name, int cookTimeMin, int spicyLevel) {
        return recipeRepository.save(Recipe.builder()
            .name(name)
            .cuisine(CuisineType.KOREAN)
            .dishType(DishType.STEW)
            .baseServings(2)
            .cookTimeMin(cookTimeMin)
            .spicyLevel(spicyLevel)
            .steps(List.of())
            .build());
    }

    private List<AiRequestLog> findLogs() {
        return aiRequestLogRepository.findAll().stream()
            .filter(log -> log.getMemberId().equals(MEMBER_ID))
            .toList();
    }

    private void givenConditions(AiRecipeConditions conditions) {
        given(aiClient.extractConditions(anyString())).willReturn(AiResult.success(conditions, CONDITION_USAGE));
    }

    private List<Long> recipeIds(AiRecipeRecommendResponse response) {
        return response.recipes().stream().map(RecommendedRecipe::recipeId).toList();
    }

    @Test
    @DisplayName("조건으로 거른 후보 안에서 AI가 고른 레시피를 이유와 함께 추천하고, 두 호출의 사용량을 합쳐 남긴다")
    void recommend_returnsPicksWithReason() {
        // given
        givenConditions(CONDITIONS);
        given(aiClient.recommendRecipes(anyString(), anyList())).willReturn(AiResult.success(List.of(
            new AiRecommendedRecipe(soybeanStew.getId(), "맵지 않고 빨리 끓일 수 있어요."),
            new AiRecommendedRecipe(99_999L, "후보에 없는 레시피")), PICK_USAGE));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "안 매운 찌개 추천해줘");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.RECOMMENDED);
        assertThat(recipeIds(response)).containsExactly(soybeanStew.getId());
        assertThat(response.recipes().get(0).reason()).isEqualTo("맵지 않고 빨리 끓일 수 있어요.");

        AiRequestLog log = findLogs().get(0);
        assertThat(log.getRequestType()).isEqualTo(AiRequestType.RECOMMENDATION);
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getMatchedRecipeIds()).isEqualTo(String.valueOf(soybeanStew.getId()));
        assertThat(log.getParsedResult()).contains("STEW", "맵지 않고");
        assertThat(log.getInputTokens()).isEqualTo(1200);
        assertThat(log.getLatencyMs()).isEqualTo(2000);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("제외 재료가 들어간 레시피는 AI에게 후보로 보내지 않는다")
    void recommend_excludesRecipesWithExcludedIngredient() {
        // given
        givenConditions(new AiRecipeConditions("KOREAN", "STEW", 25, 1, List.of("AI추천테스트오이")));
        given(aiClient.recommendRecipes(anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(soybeanStew.getId(), "오이가 없어요.")), PICK_USAGE));

        // when
        aiRecipeRecommendFacade.recommend(MEMBER_ID, "오이 빼고 찌개");

        // then
        ArgumentCaptor<List<AiRecipeOption>> candidates = ArgumentCaptor.forClass(List.class);
        verify(aiClient).recommendRecipes(anyString(), candidates.capture());
        assertThat(candidates.getValue()).extracting(AiRecipeOption::id)
            .contains(soybeanStew.getId())
            .doesNotContain(kimchiStew.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("제외 재료 이름의 %·_는 와일드카드가 아니라 글자로 찾아, 그 글자가 없는 재료의 레시피는 빼지 않는다")
    void recommend_treatsLikeWildcardsAsLiteral() {
        // given
        givenConditions(new AiRecipeConditions("KOREAN", "STEW", 25, 1, List.of("%", "AI추천테스트_이")));
        given(aiClient.recommendRecipes(anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(kimchiStew.getId(), "추천 이유")), PICK_USAGE));

        // when
        aiRecipeRecommendFacade.recommend(MEMBER_ID, "% 빼고 찌개");

        // then
        ArgumentCaptor<List<AiRecipeOption>> candidates = ArgumentCaptor.forClass(List.class);
        verify(aiClient).recommendRecipes(anyString(), candidates.capture());
        assertThat(candidates.getValue()).extracting(AiRecipeOption::id)
            .contains(kimchiStew.getId(), soybeanStew.getId());
    }

    @Test
    @DisplayName("조건 분석이 실패하면 추천 없이 FALLBACK을 주고, 두 번째 AI는 부르지 않는다")
    void recommend_fallsBackWithoutRecipesWhenConditionFails() {
        // given
        given(aiClient.extractConditions(anyString()))
            .willReturn(AiResult.failure(AiFailureType.TIMEOUT, AiUsage.failed("claude-opus-5-5", 15000)));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "찌개 추천");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(response.recipes()).isEmpty();
        verify(aiClient, never()).recommendRecipes(anyString(), anyList());

        AiRequestLog log = findLogs().get(0);
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getFailureReason()).isEqualTo("TIMEOUT");
        assertThat(log.getParsedResult()).isNull();
        assertThat(log.getInputTokens()).isNull();
        assertThat(log.getMatchedRecipeIds()).isNull();
    }

    @Test
    @DisplayName("고르기가 실패하면 서버가 거른 후보를 추천 이유 없이 FALLBACK으로 주고, 받은 조건 분석만 기록한다")
    void recommend_fallsBackToCandidatesWhenPickFails() {
        // given
        givenConditions(CONDITIONS);
        given(aiClient.recommendRecipes(anyString(), anyList()))
            .willReturn(AiResult.failure(AiFailureType.RATE_LIMITED, AiUsage.failed("claude-opus-5-5", 500)));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "찌개 추천");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(recipeIds(response)).contains(kimchiStew.getId(), soybeanStew.getId());
        assertThat(response.recipes()).allSatisfy(recipe -> assertThat(recipe.reason()).isNull());

        AiRequestLog log = findLogs().get(0);
        assertThat(log.getFailureReason()).isEqualTo("RATE_LIMITED");
        assertThat(log.getParsedResult()).contains("STEW").doesNotContain("reason");
        assertThat(log.getInputTokens()).isEqualTo(300);
        assertThat(log.getLatencyMs()).isEqualTo(1200);
    }

    @Test
    @DisplayName("AI가 후보 안의 레시피를 하나도 고르지 못하면 응답 오류로 보고 FALLBACK을 준다")
    void recommend_fallsBackWhenNoValidPick() {
        // given
        givenConditions(CONDITIONS);
        given(aiClient.recommendRecipes(anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(99_999L, "지어낸 레시피")), PICK_USAGE));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "찌개 추천");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(findLogs().get(0).getFailureReason()).isEqualTo("INVALID_RESPONSE");
    }

    @Test
    @DisplayName("조건을 만족하는 레시피가 없으면 두 번째 AI를 부르지 않고 NO_MATCH를 준다")
    void recommend_noMatchWhenNoCandidates() {
        // given
        givenConditions(new AiRecipeConditions("KOREAN", "STEW", 1, 0, List.of()));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "1분 안에 되는 찌개");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.NO_MATCH);
        verify(aiClient, never()).recommendRecipes(anyString(), anyList());
        assertThat(findLogs()).singleElement().satisfies(log -> {
            assertThat(log.isSuccess()).isTrue();
            assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.NO_MATCH);
            assertThat(log.getMatchedRecipeIds()).isNull();
        });
    }

    @Test
    @DisplayName("분석한 조건이 하나도 없으면 전체 레시피에서 고르지 않고 NO_MATCH를 준다")
    void recommend_noMatchWhenNoConditionRead() {
        // given
        givenConditions(new AiRecipeConditions(null, null, null, null, List.of()));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "통오징어찜");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.NO_MATCH);
        verify(aiClient, never()).recommendRecipes(anyString(), anyList());
        assertThat(findLogs()).singleElement().satisfies(log -> {
            assertThat(log.isSuccess()).isTrue();
            assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.NO_MATCH);
        });
    }

    @Test
    @DisplayName("AI가 후보 중 맞는 레시피가 없다고 빈 목록을 주면 장애가 아니라 NO_MATCH로 본다")
    void recommend_noMatchWhenAiPicksNothing() {
        // given
        givenConditions(CONDITIONS);
        given(aiClient.recommendRecipes(anyString(), anyList())).willReturn(AiResult.success(List.of(), PICK_USAGE));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "통오징어찜 같은 찌개");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.NO_MATCH);
        assertThat(findLogs()).singleElement().satisfies(log -> {
            assertThat(log.isSuccess()).isTrue();
            assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.NO_MATCH);
            assertThat(log.getFailureReason()).isNull();
        });
    }

    @Test
    @DisplayName("멤버십이 없으면 AI를 부르기 전에 거절한다")
    void recommend_rejectsWithoutMembership() {
        // given
        given(membershipService.getSummary(eq(MEMBER_ID), any()))
            .willReturn(new MembershipSummary(false, "NONE", null));

        // when
        Throwable thrown = catchThrowable(() -> aiRecipeRecommendFacade.recommend(MEMBER_ID, "찌개 추천"));

        // then
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(MemberErrorCode.MEMBERSHIP_REQUIRED);
        verify(aiClient, never()).extractConditions(anyString());
    }
}
