package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.response.MemberMeResponse.MembershipSummary;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.recipe.dto.response.AiMealPlanResponse;
import com.recipick.domain.recipe.dto.response.AiMealPlanResponse.Meal;
import com.recipick.domain.recipe.dto.response.AutoCartPreviewResponse;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.repository.AiRequestLogRepository;
import com.recipick.domain.recipe.repository.AutoCartRequestRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiPlannedMeal;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AiMealPlanFacadeIntegrationTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9505L;
    private static final AiUsage USAGE = new AiUsage("claude-sonnet-5-5", 8000, 300, 7600, 3500);

    @Autowired
    AiMealPlanFacade aiMealPlanFacade;
    @Autowired
    AiRequestLogRepository aiRequestLogRepository;
    @Autowired
    AutoCartRequestRepository autoCartRequestRepository;
    @Autowired
    RecipeRepository recipeRepository;
    @Autowired
    MartRepository martRepository;
    @Autowired
    StringRedisTemplate redisTemplate;

    // 실제 AI 대신 상황별 응답을 정해 주고, 회원·멤버십은 다른 도메인 흐름 없이 결과만 고정한다
    @MockitoBean
    AiClient aiClient;
    @MockitoBean
    MemberService memberService;
    @MockitoBean
    MembershipService membershipService;

    private Recipe kimchiStew;
    private Recipe soybeanStew;
    private Recipe notAutoCart;
    private Mart mart;

    @BeforeEach
    void setUp() {
        kimchiStew = saveRecipe("식단 김치찌개", true);
        soybeanStew = saveRecipe("식단 된장찌개", true);
        notAutoCart = saveRecipe("식단 자동 장보기 불가", false);
        mart = martRepository.save(Mart.builder()
            .regionId(9505L)
            .name("식단 테스트 마트")
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build());
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(mart.getId()));
        given(membershipService.getSummary(eq(MEMBER_ID), any()))
            .willReturn(new MembershipSummary(true, "TRIAL", null));
    }

    @AfterEach
    void cleanUp() {
        aiRequestLogRepository.deleteAll(findLogs());
        autoCartRequestRepository.deleteAll(autoCartRequestRepository.findAll().stream()
            .filter(request -> request.getMemberId().equals(MEMBER_ID))
            .toList());
        martRepository.deleteById(mart.getId());
        recipeRepository.deleteAllById(List.of(kimchiStew.getId(), soybeanStew.getId(), notAutoCart.getId()));
        redisTemplate.delete("ai:request:" + MEMBER_ID);
    }

    private Recipe saveRecipe(String name, boolean autoCart) {
        Recipe recipe = Recipe.builder().name(name).baseServings(2).steps(List.of()).build();
        if (autoCart) {
            recipe.enableAutoCart();
        }
        return recipeRepository.save(recipe);
    }

    private void givenAiResult(AiResult<List<AiPlannedMeal>> result) {
        given(aiClient.planMeals(anyString(), anyString(), anyList())).willReturn(result);
    }

    private List<AiRequestLog> findLogs() {
        return aiRequestLogRepository.findAll().stream()
            .filter(log -> log.getMemberId().equals(MEMBER_ID))
            .toList();
    }

    @Test
    @DisplayName("AI가 짠 식단에서 중복·목록 밖 레시피를 빼고 미리보기를 만들며, 인분이 없으면 기본 인분을 쓴다")
    void plan_createsPreviewFromValidMeals() {
        // given
        givenAiResult(AiResult.success(List.of(
            new AiPlannedMeal(kimchiStew.getId(), 3, "얼큰한 국물로 시작해요."),
            new AiPlannedMeal(soybeanStew.getId(), null, "맵지 않아 부담 없어요."),
            new AiPlannedMeal(kimchiStew.getId(), 3, "중복"),
            new AiPlannedMeal(notAutoCart.getId(), 2, "자동 장보기 불가"),
            new AiPlannedMeal(99_999L, 2, "없는 레시피")), USAGE));

        // when
        AiMealPlanResponse response = aiMealPlanFacade.plan(MEMBER_ID, "이번 주 저녁 3일치");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.PREVIEWED);
        assertThat(response.meals()).extracting(Meal::recipeId, Meal::servings, Meal::reason).containsExactly(
            tuple(kimchiStew.getId(), 3, "얼큰한 국물로 시작해요."),
            tuple(soybeanStew.getId(), 2, "맵지 않아 부담 없어요."));
        assertThat(response.preview().menus())
            .extracting(AutoCartPreviewResponse.Menu::recipeId, AutoCartPreviewResponse.Menu::servings)
            .containsExactly(tuple(kimchiStew.getId(), 3), tuple(soybeanStew.getId(), 2));

        AiRequestLog log = findLogs().get(0);
        assertThat(log.getRequestType()).isEqualTo(AiRequestType.MEAL_PLAN);
        assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.PREVIEWED);
        assertThat(log.getAutoCartRequest().getId()).isEqualTo(response.preview().autoCartRequestId());
        assertThat(log.getMatchedRecipeIds()).isEqualTo(kimchiStew.getId() + "," + soybeanStew.getId());
        assertThat(log.getPromptVersions()).isEqualToIgnoringWhitespace("{\"MEAL_PLAN\":null}");
        verify(aiClient).planMeals(eq(AiPromptType.MEAL_PLAN.defaultContent()), eq("이번 주 저녁 3일치"), anyList());
    }

    @Test
    @DisplayName("AI가 빈 식단을 주면 미리보기 없이 NO_MATCH로 답한다")
    void plan_returnsNoMatchWhenEmpty() {
        // given
        givenAiResult(AiResult.success(List.of(), USAGE));

        // when
        AiMealPlanResponse response = aiMealPlanFacade.plan(MEMBER_ID, "오늘 날씨 어때");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.NO_MATCH);
        assertThat(response.preview()).isNull();
        assertThat(findLogs()).singleElement()
            .satisfies(log -> assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.NO_MATCH));
    }

    @Test
    @DisplayName("AI 장애면 미리보기를 만들지 않고 FALLBACK으로 답하며 실패 사유를 남긴다")
    void plan_returnsFallbackWhenAiFails() {
        // given
        givenAiResult(AiResult.failure(AiFailureType.TIMEOUT, AiUsage.failed("claude-sonnet-5-5", 15000)));

        // when
        AiMealPlanResponse response = aiMealPlanFacade.plan(MEMBER_ID, "이번 주 저녁 3일치");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(response.meals()).isEmpty();
        assertThat(findLogs()).singleElement().satisfies(log -> {
            assertThat(log.isSuccess()).isFalse();
            assertThat(log.getFailureReason()).isEqualTo("TIMEOUT");
            assertThat(log.getAutoCartRequest()).isNull();
        });
    }

    @Test
    @DisplayName("AI가 목록 밖 레시피만 고르면 쓸 수 없는 응답으로 보고 FALLBACK으로 답한다")
    void plan_returnsFallbackWhenOnlyUnknownRecipes() {
        // given
        givenAiResult(AiResult.success(List.of(new AiPlannedMeal(notAutoCart.getId(), 2, "자동 장보기 불가")), USAGE));

        // when
        AiMealPlanResponse response = aiMealPlanFacade.plan(MEMBER_ID, "이번 주 저녁 1일치");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(findLogs()).singleElement()
            .satisfies(log -> assertThat(log.getFailureReason()).isEqualTo("INVALID_RESPONSE"));
    }

    @Test
    @DisplayName("이용 마트가 없으면 AI를 부르지 않고 거절한다")
    void plan_rejectsWithoutMartBeforeCallingAi() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> aiMealPlanFacade.plan(MEMBER_ID, "이번 주 저녁 3일치"));

        // then
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(OrderErrorCode.MART_NOT_SELECTED);
        verify(aiClient, never()).planMeals(anyString(), anyString(), anyList());
        assertThat(findLogs()).isEmpty();
    }
}
