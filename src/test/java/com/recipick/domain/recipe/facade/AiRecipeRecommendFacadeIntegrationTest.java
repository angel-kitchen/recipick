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
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.response.AiRecipeRecommendResponse;
import com.recipick.domain.recipe.dto.response.AiRecipeRecommendResponse.RecommendedRecipe;
import com.recipick.domain.recipe.entity.AiPrompt;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.repository.AiPromptRepository;
import com.recipick.domain.recipe.repository.AiRequestLogRepository;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeCandidate;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Long MART_ID = 9503L;
    private static final AiUsage CONDITION_USAGE = new AiUsage("claude-opus-5-5", 300, 60, 0, 700);
    private static final AiUsage PICK_USAGE = new AiUsage("claude-opus-5-5", 900, 200, 0, 1300);
    // 테스트용 레시피만 남도록 다른 데이터와 겹치지 않는 조건으로 고정한다
    private static final AiRecipeConditions CONDITIONS =
        new AiRecipeConditions("KOREAN", "STEW", 25, 1, List.of(), null, null);

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
    @Autowired
    AiPromptRepository aiPromptRepository;

    @MockitoBean
    AiClient aiClient;
    @MockitoBean
    MembershipService membershipService;
    @MockitoBean
    MemberService memberService;
    @MockitoBean
    ProductIngredientService productIngredientService;

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
        aiPromptRepository.deleteAll(aiPromptRepository.findAllByPromptTypeOrderByVersionDesc(AiPromptType.RECOMMEND));
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
        given(aiClient.extractConditions(anyString(), anyString()))
            .willReturn(AiResult.success(conditions, CONDITION_USAGE));
    }

    private List<Long> recipeIds(AiRecipeRecommendResponse response) {
        return response.recipes().stream().map(RecommendedRecipe::recipeId).toList();
    }

    @Test
    @DisplayName("조건으로 거른 후보 안에서 AI가 고른 레시피를 이유와 함께 추천하고, 두 호출의 사용량을 합쳐 남긴다")
    void recommend_returnsPicksWithReason() {
        // given
        givenConditions(CONDITIONS);
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(List.of(
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

    @Test
    @DisplayName("활성화한 프롬프트 버전을 AI 호출에 쓰고, 이력에 종류별 버전을 남긴다")
    void recommend_usesActivePromptAndRecordsVersions() {
        // given
        AiPrompt prompt = AiPrompt.builder()
            .promptType(AiPromptType.RECOMMEND)
            .version(1)
            .content("관리자가 고친 추천 지시문\n")
            .createdByAdminId(1L)
            .build();
        prompt.activate();
        aiPromptRepository.save(prompt);
        givenConditions(CONDITIONS);
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(soybeanStew.getId(), "맵지 않아요.")), PICK_USAGE));

        // when
        aiRecipeRecommendFacade.recommend(MEMBER_ID, "안 매운 찌개");

        // then
        verify(aiClient).extractConditions(eq(AiPromptType.CONDITION.defaultContent()), eq("안 매운 찌개"));
        verify(aiClient).recommendRecipes(eq("관리자가 고친 추천 지시문\n"), eq("안 매운 찌개"), anyList());
        assertThat(findLogs().get(0).getPromptVersions())
            .isEqualToIgnoringWhitespace("{\"CONDITION\":null,\"RECOMMEND\":1}");
    }

    @Test
    @DisplayName("조건 분석에서 실패하면 쓴 프롬프트인 조건 분석 버전만 남긴다")
    void recommend_recordsOnlyConditionPromptWhenConditionFails() {
        // given
        given(aiClient.extractConditions(anyString(), anyString()))
            .willReturn(AiResult.failure(AiFailureType.TIMEOUT, AiUsage.failed("claude-sonnet-5-5", 15000)));

        // when
        aiRecipeRecommendFacade.recommend(MEMBER_ID, "안 매운 찌개");

        // then
        assertThat(findLogs().get(0).getPromptVersions()).isEqualToIgnoringWhitespace("{\"CONDITION\":null}");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("예산이 있으면 이용 마트 가격으로 인분만큼 예상 금액을 계산해, 예산 이하인 자동 장보기 레시피만 AI에게 넘긴다")
    void recommend_filtersCandidatesWithinBudget() {
        // given
        kimchiStew.enableAutoCart();
        soybeanStew.enableAutoCart();
        recipeRepository.saveAll(List.of(kimchiStew, soybeanStew));
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        // 오이 1개 2,000원 × 3인분 = 6,000원으로 김치찌개는 예산(5,000원)을 넘고, 재료가 없는 된장찌개는 0원이다
        given(productIngredientService.findCandidates(MART_ID, List.of(cucumber.getId()))).willReturn(Map.of(
            cucumber.getId(), List.of(new ProductSummary(1L, MART_ID, "오이 1개", null, 2000L, 2000L, 0, BigDecimal.ONE,
                SaleUnit.EA, ProductStatus.SELLING, 10, false))));
        givenConditions(new AiRecipeConditions("KOREAN", "STEW", null, null, List.of(), 5000, 3));
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(soybeanStew.getId(), "예산 안이에요.")), PICK_USAGE));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "5천 원 안으로 3인분 찌개");

        // then
        ArgumentCaptor<List<AiRecipeCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(aiClient).recommendRecipes(anyString(), anyString(), candidates.capture());
        assertThat(candidates.getValue()).extracting(AiRecipeCandidate::id).containsExactly(soybeanStew.getId());
        assertThat(response.maxBudget()).isEqualTo(5000);
        assertThat(response.budgetApplied()).isTrue();
        assertThat(response.recipes()).extracting(RecommendedRecipe::estimatedPrice).containsExactly(0L);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("이용 마트가 없으면 예산 없이 추천하고 예산 미적용으로 알린다")
    void recommend_skipsBudgetWithoutMart() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.empty());
        givenConditions(new AiRecipeConditions("KOREAN", "STEW", null, null, List.of(), 5000, 3));
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(kimchiStew.getId(), "얼큰해요.")), PICK_USAGE));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "5천 원 안으로 3인분 찌개");

        // then
        ArgumentCaptor<List<AiRecipeCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(aiClient).recommendRecipes(anyString(), anyString(), candidates.capture());
        assertThat(candidates.getValue()).extracting(AiRecipeCandidate::id)
            .contains(kimchiStew.getId(), soybeanStew.getId());
        assertThat(response.maxBudget()).isEqualTo(5000);
        assertThat(response.budgetApplied()).isFalse();
        assertThat(response.recipes()).extracting(RecommendedRecipe::estimatedPrice).containsOnlyNulls();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("제외 재료가 들어간 레시피는 AI에게 후보로 보내지 않는다")
    void recommend_excludesRecipesWithExcludedIngredient() {
        // given
        givenConditions(new AiRecipeConditions("KOREAN", "STEW", 25, 1, List.of("AI추천테스트오이"), null, null));
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(soybeanStew.getId(), "오이가 없어요.")), PICK_USAGE));

        // when
        aiRecipeRecommendFacade.recommend(MEMBER_ID, "오이 빼고 찌개");

        // then
        ArgumentCaptor<List<AiRecipeCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(aiClient).recommendRecipes(anyString(), anyString(), candidates.capture());
        assertThat(candidates.getValue()).extracting(AiRecipeCandidate::id)
            .contains(soybeanStew.getId())
            .doesNotContain(kimchiStew.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("제외 재료 이름의 %·_는 와일드카드가 아니라 글자로 찾아, 그 글자가 없는 재료의 레시피는 빼지 않는다")
    void recommend_treatsLikeWildcardsAsLiteral() {
        // given
        givenConditions(new AiRecipeConditions("KOREAN", "STEW", 25, 1, List.of("%", "AI추천테스트_이"), null, null));
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(kimchiStew.getId(), "추천 이유")), PICK_USAGE));

        // when
        aiRecipeRecommendFacade.recommend(MEMBER_ID, "% 빼고 찌개");

        // then
        ArgumentCaptor<List<AiRecipeCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(aiClient).recommendRecipes(anyString(), anyString(), candidates.capture());
        assertThat(candidates.getValue()).extracting(AiRecipeCandidate::id)
            .contains(kimchiStew.getId(), soybeanStew.getId());
    }

    @Test
    @DisplayName("조건 분석이 실패하면 추천 없이 FALLBACK을 주고, 두 번째 AI는 부르지 않는다")
    void recommend_fallsBackWithoutRecipesWhenConditionFails() {
        // given
        given(aiClient.extractConditions(anyString(), anyString()))
            .willReturn(AiResult.failure(AiFailureType.TIMEOUT, AiUsage.failed("claude-opus-5-5", 15000)));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "찌개 추천");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(response.recipes()).isEmpty();
        verify(aiClient, never()).recommendRecipes(anyString(), anyString(), anyList());

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
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList()))
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
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(
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
        givenConditions(new AiRecipeConditions("KOREAN", "STEW", 1, 0, List.of(), null, null));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "1분 안에 되는 찌개");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.NO_MATCH);
        verify(aiClient, never()).recommendRecipes(anyString(), anyString(), anyList());
        assertThat(findLogs()).singleElement().satisfies(log -> {
            assertThat(log.isSuccess()).isTrue();
            assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.NO_MATCH);
            assertThat(log.getMatchedRecipeIds()).isNull();
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("분석한 조건이 하나도 없으면 전체 레시피를 후보로 AI가 고른다")
    void recommend_picksFromAllRecipesWhenNoConditionRead() {
        // given
        givenConditions(new AiRecipeConditions(null, null, null, null, List.of(), null, null));
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(kimchiStew.getId(), "해장에 좋은 국물이에요.")), PICK_USAGE));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "해장용");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.RECOMMENDED);
        assertThat(recipeIds(response)).containsExactly(kimchiStew.getId());
        ArgumentCaptor<List<AiRecipeCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(aiClient).recommendRecipes(anyString(), anyString(), candidates.capture());
        assertThat(candidates.getValue()).extracting(AiRecipeCandidate::id)
            .contains(kimchiStew.getId(), soybeanStew.getId());
    }

    @Test
    @DisplayName("조건 없이 전체에서 고르다 실패하면 서버가 거른 후보가 없으므로 레시피 없이 FALLBACK을 준다")
    void recommend_fallsBackWithoutRecipesWhenPickFailsWithoutCondition() {
        // given
        givenConditions(new AiRecipeConditions(null, null, null, null, List.of(), null, null));
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList()))
            .willReturn(AiResult.failure(AiFailureType.TIMEOUT, AiUsage.failed("claude-opus-5-5", 15000)));

        // when
        AiRecipeRecommendResponse response = aiRecipeRecommendFacade.recommend(MEMBER_ID, "해장용");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(response.recipes()).isEmpty();
        assertThat(findLogs().get(0).getFailureReason()).isEqualTo("TIMEOUT");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("후보마다 종류·요리 구분·조리 시간·맵기·필수 재료를 함께 AI에게 넘긴다")
    void recommend_sendsCandidateAttributes() {
        // given
        givenConditions(CONDITIONS);
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList())).willReturn(AiResult.success(
            List.of(new AiRecommendedRecipe(kimchiStew.getId(), "추천 이유")), PICK_USAGE));

        // when
        aiRecipeRecommendFacade.recommend(MEMBER_ID, "오이 들어간 찌개");

        // then
        ArgumentCaptor<List<AiRecipeCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(aiClient).recommendRecipes(anyString(), anyString(), candidates.capture());
        assertThat(candidates.getValue())
            .filteredOn(candidate -> candidate.id().equals(kimchiStew.getId()))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.cuisineType()).isEqualTo("KOREAN");
                assertThat(candidate.dishType()).isEqualTo("STEW");
                assertThat(candidate.cookingMinutes()).isEqualTo(21);
                assertThat(candidate.spicyLevel()).isEqualTo(1);
                assertThat(candidate.requiredIngredients()).containsExactly("AI추천테스트오이");
            });
    }

    @Test
    @DisplayName("AI가 후보 중 맞는 레시피가 없다고 빈 목록을 주면 장애가 아니라 NO_MATCH로 본다")
    void recommend_noMatchWhenAiPicksNothing() {
        // given
        givenConditions(CONDITIONS);
        given(aiClient.recommendRecipes(anyString(), anyString(), anyList()))
            .willReturn(AiResult.success(List.of(), PICK_USAGE));

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
        verify(aiClient, never()).extractConditions(anyString(), anyString());
    }
}
