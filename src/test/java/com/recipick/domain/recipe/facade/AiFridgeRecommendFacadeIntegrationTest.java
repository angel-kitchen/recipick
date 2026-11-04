package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.response.MemberMeResponse.MembershipSummary;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.response.AiFridgeRecommendResponse;
import com.recipick.domain.recipe.dto.response.AiFridgeRecommendResponse.MissingIngredient;
import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.UnavailableReason;
import com.recipick.domain.recipe.repository.AiRequestLogRepository;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.AiFridgeTools;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiFridgeAnswer;
import com.recipick.infra.ai.dto.AiFridgeRecipeSearch;
import com.recipick.infra.ai.dto.AiIngredientPrice;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiToolResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AiFridgeRecommendFacadeIntegrationTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9504L;
    private static final Long MART_ID = 31L;
    private static final AiUsage USAGE = new AiUsage("claude-sonnet-5-5", 2400, 300, 1800, 4200);

    @Autowired
    AiFridgeRecommendFacade aiFridgeRecommendFacade;
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
    @MockitoBean
    MemberService memberService;
    @MockitoBean
    ProductIngredientService productIngredientService;

    private Ingredient tofu;
    private Ingredient kimchi;
    private Ingredient pork;
    private Recipe kimchiStew;

    @BeforeEach
    void setUp() {
        tofu = saveIngredient("냉장고테스트두부");
        kimchi = saveIngredient("냉장고테스트김치");
        pork = saveIngredient("냉장고테스트돼지고기");
        kimchiStew = recipeRepository.save(Recipe.builder()
            .name("냉장고테스트 김치찌개")
            .baseServings(2)
            .steps(List.of())
            .build());
        List.of(tofu, kimchi, pork).forEach(ingredient -> recipeIngredientRepository.save(RecipeIngredient.builder()
            .recipe(kimchiStew).ingredient(ingredient).amountPerServing(BigDecimal.ONE).required(true).build()));
        given(membershipService.getSummary(eq(MEMBER_ID), any()))
            .willReturn(new MembershipSummary(true, "TRIAL", null));
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productIngredientService.findCandidates(eq(MART_ID), anyList())).willReturn(Map.of(
            pork.getId(), List.of(
                product(1L, "앞다리살 600g", 9900L, 0),
                product(2L, "앞다리살 300g", 5900L, 4))));
    }

    @AfterEach
    void cleanUp() {
        aiRequestLogRepository.deleteAll(findLogs());
        recipeIngredientRepository.deleteAll(recipeIngredientRepository.findAllWithIngredient(kimchiStew.getId()));
        recipeRepository.deleteById(kimchiStew.getId());
        ingredientRepository.deleteAllById(List.of(tofu.getId(), kimchi.getId(), pork.getId()));
        redisTemplate.delete("ai:request:" + MEMBER_ID);
    }

    private Ingredient saveIngredient(String name) {
        return ingredientRepository.save(Ingredient.builder().name(name).unit(SaleUnit.G).build());
    }

    private ProductSummary product(Long id, String name, Long price, int quantity) {
        return new ProductSummary(id, MART_ID, name, null, price, new BigDecimal("300"), SaleUnit.G,
            ProductStatus.SELLING, quantity, false);
    }

    private List<AiRequestLog> findLogs() {
        return aiRequestLogRepository.findAll().stream()
            .filter(log -> log.getMemberId().equals(MEMBER_ID))
            .toList();
    }

    // AI 대신 Tool을 직접 불러 보고, 받은 Tool 결과로 답변을 정한다
    private void givenAiCallsTools(Function<AiFridgeTools, AiResult<AiFridgeAnswer>> behavior) {
        given(aiClient.answerFridgeQuestion(anyString(), anyString(), anyList(), any()))
            .willAnswer(invocation -> behavior.apply(invocation.getArgument(3)));
    }

    @Test
    @DisplayName("Tool로 찾은 레시피와 부족한 재료의 가격·재고를 서버가 조회한 값으로 담아 답변한다")
    void recommend_answersWithRecipesFoundByTools() {
        // given
        AtomicReference<AiToolResult<List<AiIngredientPrice>>> priceResult = new AtomicReference<>();
        givenAiCallsTools(tools -> {
            AiFridgeRecipeSearch search = tools.findRecipes(List.of("냉장고테스트 두부", "냉장고테스트김치")).content();
            Long missingId = search.recipes().get(0).missingIngredients().get(0).ingredientId();
            priceResult.set(tools.findIngredientPrices(List.of(missingId)));
            return AiResult.success(new AiFridgeAnswer("김치찌개를 만들 수 있어요.",
                List.of(kimchiStew.getId(), 99_999L)), USAGE);
        });

        // when
        AiFridgeRecommendResponse response = aiFridgeRecommendFacade.recommend(MEMBER_ID, "두부랑 김치 있어");

        // then
        assertThat(priceResult.get().content()).containsExactly(
            new AiIngredientPrice(pork.getId(), "앞다리살 300g", 5900L, "300G", null));
        assertThat(response.status()).isEqualTo(AiResultStatus.ANSWERED);
        assertThat(response.answer()).isEqualTo("김치찌개를 만들 수 있어요.");
        assertThat(response.martSelected()).isTrue();
        assertThat(response.recipes()).hasSize(1);
        assertThat(response.recipes().get(0).ownedIngredients())
            .containsExactly("냉장고테스트두부", "냉장고테스트김치");
        assertThat(response.recipes().get(0).missingIngredients()).containsExactly(new MissingIngredient(
            pork.getId(), "냉장고테스트돼지고기", 2L, "앞다리살 300g", 5900L, null));

        AiRequestLog log = findLogs().get(0);
        assertThat(log.getRequestType()).isEqualTo(AiRequestType.FRIDGE);
        assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.ANSWERED);
        assertThat(log.getMatchedRecipeIds()).isEqualTo(String.valueOf(kimchiStew.getId()));
        assertThat(log.getInputTokens()).isEqualTo(2400);
    }

    @Test
    @DisplayName("이용 마트가 없으면 가격·재고 Tool이 사유를 돌려주고, 응답에는 상품 정보 없이 레시피만 담는다")
    void recommend_tellsAiWhenMartNotSelected() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.empty());
        AtomicReference<AiToolResult<List<AiIngredientPrice>>> priceResult = new AtomicReference<>();
        givenAiCallsTools(tools -> {
            tools.findRecipes(List.of("냉장고테스트두부"));
            priceResult.set(tools.findIngredientPrices(List.of(pork.getId())));
            return AiResult.success(new AiFridgeAnswer("이용 마트를 먼저 설정해 주세요.",
                List.of(kimchiStew.getId())), USAGE);
        });

        // when
        AiFridgeRecommendResponse response = aiFridgeRecommendFacade.recommend(MEMBER_ID, "두부 있어");

        // then
        assertThat(priceResult.get().isError()).isTrue();
        assertThat(priceResult.get().errorReason()).contains("이용 마트");
        assertThat(response.status()).isEqualTo(AiResultStatus.ANSWERED);
        assertThat(response.martSelected()).isFalse();
        assertThat(response.recipes().get(0).missingIngredients())
            .extracting(MissingIngredient::productId, MissingIngredient::unavailableReason)
            .containsOnly(tuple(null, null));
    }

    @Test
    @DisplayName("레시피 찾기가 돌려주지 않은 재료 ID로는 가격·재고를 조회하지 않는다")
    void recommend_rejectsIngredientIdsNotFoundByRecipeSearch() {
        // given
        AtomicReference<AiToolResult<List<AiIngredientPrice>>> priceResult = new AtomicReference<>();
        givenAiCallsTools(tools -> {
            priceResult.set(tools.findIngredientPrices(List.of(pork.getId())));
            return AiResult.success(new AiFridgeAnswer("재료를 알려 주세요.", List.of()), USAGE);
        });

        // when
        AiFridgeRecommendResponse response = aiFridgeRecommendFacade.recommend(MEMBER_ID, "돼지고기 얼마야");

        // then
        assertThat(priceResult.get().isError()).isTrue();
        assertThat(response.status()).isEqualTo(AiResultStatus.NO_MATCH);
        assertThat(response.answer()).isEqualTo("재료를 알려 주세요.");
    }

    @Test
    @DisplayName("살 수 없는 상품만 있으면 그 사유를, 판매 상품이 없으면 NOT_SOLD_IN_MART를 응답에 담는다")
    void recommend_showsUnavailableReasons() {
        // given
        given(productIngredientService.findCandidates(eq(MART_ID), anyList())).willReturn(Map.of(
            pork.getId(), List.of(product(1L, "앞다리살 600g", 9900L, 0))));
        givenAiCallsTools(tools -> {
            tools.findRecipes(List.of("냉장고테스트김치"));
            return AiResult.success(new AiFridgeAnswer("돼지고기가 품절이에요.", List.of(kimchiStew.getId())), USAGE);
        });

        // when
        AiFridgeRecommendResponse response = aiFridgeRecommendFacade.recommend(MEMBER_ID, "김치 있어");

        // then
        assertThat(response.recipes().get(0).missingIngredients())
            .extracting(MissingIngredient::name, MissingIngredient::unavailableReason)
            .containsExactly(
                tuple("냉장고테스트두부", UnavailableReason.NOT_SOLD_IN_MART),
                tuple("냉장고테스트돼지고기", UnavailableReason.SOLD_OUT));
    }

    @Test
    @DisplayName("AI가 레시피 찾기 결과에 없는 레시피만 고르면 쓸 수 없는 응답으로 보고 FALLBACK을 준다")
    void recommend_fallsBackWhenAiPicksUnknownRecipes() {
        // given
        givenAiCallsTools(tools -> {
            tools.findRecipes(List.of("냉장고테스트두부"));
            return AiResult.success(new AiFridgeAnswer("없는 레시피예요.", List.of(99_999L)), USAGE);
        });

        // when
        AiFridgeRecommendResponse response = aiFridgeRecommendFacade.recommend(MEMBER_ID, "두부 있어");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(response.answer()).isNull();
        AiRequestLog log = findLogs().get(0);
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getFailureReason()).contains(AiFailureType.INVALID_RESPONSE.name());
    }

    @Test
    @DisplayName("AI 호출이 실패하면 결과 없이 FALLBACK을 주고 실패 사유를 남긴다")
    void recommend_fallsBackWhenAiFails() {
        // given
        given(aiClient.answerFridgeQuestion(anyString(), anyString(), anyList(), any())).willReturn(
            AiResult.failure(AiFailureType.TOOL_LIMIT_EXCEEDED, USAGE));

        // when
        AiFridgeRecommendResponse response = aiFridgeRecommendFacade.recommend(MEMBER_ID, "두부 있어");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(response.recipes()).isEmpty();
        assertThat(findLogs().get(0).getFailureReason()).contains(AiFailureType.TOOL_LIMIT_EXCEEDED.name());
    }
}
