package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.response.MemberMeResponse.MembershipSummary;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.recipe.dto.response.AiAutoCartPreviewResponse;
import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.AutoCartStatus;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.exception.AiErrorCode;
import com.recipick.domain.recipe.repository.AiRequestLogRepository;
import com.recipick.domain.recipe.repository.AutoCartRequestRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.domain.recipe.service.AutoCartService;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AiAutoCartFacadeIntegrationTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9502L;
    private static final AiUsage USAGE = new AiUsage("claude-opus-5-5", 900, 120, 800, 1500);

    @Autowired
    AiAutoCartFacade aiAutoCartFacade;
    @Autowired
    AutoCartService autoCartService;
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
    @Autowired
    JdbcTemplate jdbcTemplate;

    // 실제 AI 대신 상황별 응답을 정해 주고, 회원·멤버십은 다른 도메인 흐름 없이 결과만 고정한다
    @MockitoBean
    AiClient aiClient;
    @MockitoBean
    MemberService memberService;
    @MockitoBean
    MembershipService membershipService;

    private Recipe kimchiStew;
    private Recipe soybeanStew;
    private Mart mart;

    @BeforeEach
    void setUp() {
        kimchiStew = saveAutoCartRecipe("AI장보기 김치찌개");
        soybeanStew = saveAutoCartRecipe("AI장보기 된장찌개");
        mart = martRepository.save(Mart.builder()
            .regionId(9502L)
            .name("AI 장보기 테스트 마트")
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
        recipeRepository.deleteAllById(List.of(kimchiStew.getId(), soybeanStew.getId()));
        redisTemplate.delete("ai:request:" + MEMBER_ID);
    }

    private Recipe saveAutoCartRecipe(String name) {
        Recipe recipe = Recipe.builder().name(name).baseServings(2).steps(List.of()).build();
        recipe.enableAutoCart();
        return recipeRepository.save(recipe);
    }

    private void givenAiResult(AiResult<List<AiExtractedMenu>> result) {
        given(aiClient.extractMenus(anyString(), anyList())).willReturn(result);
    }

    private List<AiRequestLog> findLogs() {
        return aiRequestLogRepository.findAll().stream()
            .filter(log -> log.getMemberId().equals(MEMBER_ID))
            .toList();
    }

    @Test
    @DisplayName("메뉴의 레시피가 하나로 정해지면 미리보기를 만들고 요청 이력에 연결한다")
    void preview_createsPreviewAndLinksLog() {
        // given
        givenAiResult(AiResult.success(
            List.of(new AiExtractedMenu("김치찌개", List.of(kimchiStew.getId()), 3)), USAGE));

        // when
        AiAutoCartPreviewResponse response = aiAutoCartFacade.preview(MEMBER_ID, "김치찌개 3인분 장보기");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.PREVIEWED);
        assertThat(response.preview().status()).isEqualTo(AutoCartStatus.PREVIEWED);
        assertThat(response.preview().menus()).singleElement()
            .satisfies(menu -> assertThat(menu.servings()).isEqualTo(3));

        AiRequestLog log = findLogs().get(0);
        assertThat(log.getRequestType()).isEqualTo(AiRequestType.AUTO_CART);
        assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.PREVIEWED);
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getAutoCartRequest().getId()).isEqualTo(response.preview().autoCartRequestId());
        assertThat(log.getMatchedRecipeIds()).isEqualTo(String.valueOf(kimchiStew.getId()));
        assertThat(log.getParsedResult()).contains("김치찌개");
        assertThat(log.getInputTokens()).isEqualTo(900);
        assertThat(log.getLatencyMs()).isEqualTo(1500);
    }

    @Test
    @DisplayName("후보가 여러 개면 미리보기를 만들지 않고 후보를 돌려준다")
    void preview_returnsCandidatesWhenAmbiguous() {
        // given
        givenAiResult(AiResult.success(List.of(
            new AiExtractedMenu("찌개", List.of(kimchiStew.getId(), soybeanStew.getId()), null)), USAGE));

        // when
        AiAutoCartPreviewResponse response = aiAutoCartFacade.preview(MEMBER_ID, "찌개 끓이고 싶어");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.NEEDS_SELECTION);
        assertThat(response.preview()).isNull();
        assertThat(response.menus().get(0).candidates())
            .extracting(AiAutoCartPreviewResponse.RecipeOption::recipeId)
            .containsExactly(kimchiStew.getId(), soybeanStew.getId());
        assertThat(autoCartRequestRepository.findAll())
            .noneMatch(request -> request.getMemberId().equals(MEMBER_ID));
        assertThat(findLogs()).singleElement()
            .satisfies(log -> assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.NEEDS_SELECTION));
    }

    @Test
    @DisplayName("AI가 실패하면 오류 대신 이름이 맞는 레시피를 FALLBACK으로 주고 실패 이력을 남긴다")
    void preview_fallsBackWhenAiFails() {
        // given
        givenAiResult(AiResult.failure(AiFailureType.TIMEOUT, AiUsage.failed("claude-opus-5-5", 15000)));

        // when
        AiAutoCartPreviewResponse response = aiAutoCartFacade.preview(MEMBER_ID, "AI장보기 된장찌개 먹고 싶어");

        // then
        assertThat(response.status()).isEqualTo(AiResultStatus.FALLBACK);
        assertThat(response.recipes())
            .extracting(AiAutoCartPreviewResponse.RecipeOption::recipeId)
            .containsExactly(soybeanStew.getId());

        AiRequestLog log = findLogs().get(0);
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getFailureReason()).isEqualTo("TIMEOUT");
        assertThat(log.getParsedResult()).isNull();
        assertThat(log.getInputTokens()).isNull();
        assertThat(log.getModel()).isEqualTo("claude-opus-5-5");
        assertThat(log.getLatencyMs()).isEqualTo(15000);
    }

    @Test
    @DisplayName("1분에 5번을 넘으면 AI를 부르지 않고 이력도 남기지 않은 채 AI-001로 거절한다")
    void preview_rejectsWhenLimitExceeded() {
        // given
        givenAiResult(AiResult.success(List.of(), USAGE));
        for (int i = 0; i < 5; i++) {
            aiAutoCartFacade.preview(MEMBER_ID, "아무거나");
        }

        // when
        Throwable thrown = catchThrowable(() -> aiAutoCartFacade.preview(MEMBER_ID, "아무거나"));

        // then
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AiErrorCode.AI_REQUEST_LIMIT_EXCEEDED);
        verify(aiClient, times(5)).extractMenus(anyString(), anyList());
        assertThat(findLogs()).hasSize(5)
            .allSatisfy(log -> assertThat(log.getResultStatus()).isEqualTo(AiResultStatus.NO_MATCH));
    }

    @Test
    @DisplayName("멤버십이 없으면 AI를 부르기 전에 거절한다")
    void preview_rejectsWithoutMembership() {
        // given
        given(membershipService.getSummary(eq(MEMBER_ID), any()))
            .willReturn(new MembershipSummary(false, "NONE", null));

        // when
        Throwable thrown = catchThrowable(() -> aiAutoCartFacade.preview(MEMBER_ID, "김치찌개"));

        // then
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(MemberErrorCode.MEMBERSHIP_REQUIRED);
        verify(aiClient, never()).extractMenus(anyString(), anyList());
    }

    @Test
    @DisplayName("정리 배치는 AI로 만든 미리보기를 지워도 요청 이력은 연결만 비우고 남긴다")
    void deleteExpiredPreviews_keepsAiLog() {
        // given
        givenAiResult(AiResult.success(
            List.of(new AiExtractedMenu("김치찌개", List.of(kimchiStew.getId()), null)), USAGE));
        Long autoCartRequestId = aiAutoCartFacade.preview(MEMBER_ID, "김치찌개").preview().autoCartRequestId();
        LocalDateTime before = LocalDateTime.now().minusDays(7);
        // created_at은 생성 뒤 바꿀 수 없는 컬럼이라 SQL로 직접 과거 시각을 넣는다
        jdbcTemplate.update("UPDATE auto_cart_request SET created_at = ? WHERE id = ?",
            before.minusDays(1), autoCartRequestId);

        // when
        autoCartService.deleteExpiredPreviews(before);

        // then
        assertThat(autoCartRequestRepository.findById(autoCartRequestId)).isEmpty();
        assertThat(findLogs()).singleElement()
            .satisfies(log -> assertThat(log.getAutoCartRequest()).isNull());
    }
}
