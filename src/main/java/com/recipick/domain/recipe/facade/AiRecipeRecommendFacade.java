package com.recipick.domain.recipe.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.recipe.dto.response.AiRecipeRecommendResponse;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.AiRecipeRecommendResolver;
import com.recipick.domain.recipe.service.AiRecipeRecommendResolver.Recommendation;
import com.recipick.domain.recipe.service.AiRequestLimiter;
import com.recipick.domain.recipe.service.AiRequestLogService;
import com.recipick.domain.recipe.service.RecipeRecommendCondition;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// AI 조건 추천의 진입점. AI를 두 번 부르므로 트랜잭션을 걸지 않고, 후보 조회와 이력 저장만 각 Service의 짧은 트랜잭션으로 한다.
// ① AI가 조건 정리 → ② 서버가 조건 검증·후보 조회 → ③ AI가 후보 안에서 고르기 → 서버가 다시 확인 순이다.
@Component
@RequiredArgsConstructor
public class AiRecipeRecommendFacade {

    private final MembershipService membershipService;
    private final RecipeService recipeService;
    private final AiRequestLimiter aiRequestLimiter;
    private final AiRequestLogService aiRequestLogService;
    private final AiClient aiClient;
    private final Clock clock;

    public AiRecipeRecommendResponse recommend(Long memberId, String text) {
        validateMembership(memberId);
        aiRequestLimiter.acquire(memberId);

        AiResult<AiRecipeConditions> conditionResult = aiClient.extractConditions(text);
        if (!conditionResult.isSuccess()) {
            saveLog(memberId, text, AiResultStatus.FALLBACK, conditionResult, List.of());
            return AiRecipeRecommendResponse.fallback(List.of());
        }

        RecipeRecommendCondition condition = RecipeRecommendCondition.from(conditionResult.content());
        List<Recipe> candidates = recipeService.findRecommendCandidates(condition);
        if (candidates.isEmpty()) {
            saveLog(memberId, text, AiResultStatus.NO_MATCH, conditionResult, List.of());
            return AiRecipeRecommendResponse.noMatch();
        }

        AiResult<List<AiRecommendedRecipe>> pickResult = aiClient.recommendRecipes(text, toOptions(candidates));
        List<Recommendation> recommendations = pickResult.isSuccess()
            ? AiRecipeRecommendResolver.select(pickResult.content(), candidates)
            : List.of();
        // AI가 답했어도 후보 안의 레시피를 하나도 고르지 못했으면 쓸 수 없는 응답이라 실패로 본다
        if (recommendations.isEmpty()) {
            AiFailureType failureType = pickResult.isSuccess()
                ? AiFailureType.INVALID_RESPONSE
                : pickResult.failureType();
            List<Recommendation> fallback = AiRecipeRecommendResolver.fallback(candidates);
            saveLog(memberId, text, AiResultStatus.FALLBACK,
                combine(conditionResult, pickResult, failureType), toRecipeIds(fallback));
            return AiRecipeRecommendResponse.fallback(fallback);
        }
        saveLog(memberId, text, AiResultStatus.RECOMMENDED,
            combine(conditionResult, pickResult, null), toRecipeIds(recommendations));
        return AiRecipeRecommendResponse.recommended(recommendations);
    }

    // 추천은 장보기와 달리 이용 마트가 필요 없어 멤버십만 확인한다
    private void validateMembership(Long memberId) {
        if (!membershipService.getSummary(memberId, LocalDateTime.now(clock)).active()) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_REQUIRED);
        }
    }

    private List<AiRecipeOption> toOptions(List<Recipe> candidates) {
        return candidates.stream()
            .map(recipe -> new AiRecipeOption(recipe.getId(), recipe.getName()))
            .toList();
    }

    // 두 번의 호출을 이력 한 건으로 남기려고, 분석 결과는 함께 묶고 토큰·응답 시간은 합친다
    private AiResult<ParsedResult> combine(AiResult<AiRecipeConditions> conditionResult,
                                           AiResult<List<AiRecommendedRecipe>> pickResult, AiFailureType failureType) {
        return new AiResult<>(
            new ParsedResult(conditionResult.content(), pickResult.content()),
            failureType,
            conditionResult.usage().plus(pickResult.usage()));
    }

    private List<Long> toRecipeIds(List<Recommendation> recommendations) {
        return recommendations.stream()
            .map(recommendation -> recommendation.recipe().getId())
            .toList();
    }

    private void saveLog(Long memberId, String text, AiResultStatus status, AiResult<?> aiResult,
                         List<Long> recipeIds) {
        aiRequestLogService.save(memberId, AiRequestType.RECOMMENDATION, text, status, aiResult, recipeIds, null);
    }

    // 고르기에서 실패하면 recommendations는 null이라, 이력에는 받은 조건 분석 결과만 남는다
    record ParsedResult(AiRecipeConditions conditions, List<AiRecommendedRecipe> recommendations) {
    }
}
