package com.recipick.domain.recipe.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.FridgeRecipe;
import com.recipick.domain.recipe.dto.IngredientPrice;
import com.recipick.domain.recipe.dto.response.AiFridgeRecommendResponse;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.service.AiRequestLimiter;
import com.recipick.domain.recipe.service.AiRequestLogService;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiFridgeAnswer;
import com.recipick.infra.ai.dto.AiResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// AI 냉장고 파먹기의 진입점. AI가 Tool을 여러 번 부르는 동안 트랜잭션을 걸지 않고, Tool의 조회와 이력 저장만
// 각 Service의 짧은 트랜잭션으로 한다. 레시피 찾기(recipe)와 가격·재고(member·product)는 Tool 실행기에서 조립한다
@Component
@RequiredArgsConstructor
public class AiFridgeRecommendFacade {

    private final MembershipService membershipService;
    private final MemberService memberService;
    private final RecipeService recipeService;
    private final ProductIngredientService productIngredientService;
    private final AiRequestLimiter aiRequestLimiter;
    private final AiRequestLogService aiRequestLogService;
    private final AiClient aiClient;
    private final Clock clock;

    public AiFridgeRecommendResponse recommend(Long memberId, String text) {
        validateMembership(memberId);
        aiRequestLimiter.acquire(memberId);

        AiFridgeToolExecutor tools =
            new AiFridgeToolExecutor(memberId, recipeService, memberService, productIngredientService);
        AiResult<AiFridgeAnswer> result =
            aiClient.answerFridgeQuestion(text, recipeService.findIngredientNames(), tools);
        if (!result.isSuccess()) {
            saveLog(memberId, text, AiResultStatus.FALLBACK, result, List.of());
            return AiFridgeRecommendResponse.fallback(isMartSelected(memberId));
        }
        // 빈 목록은 AI가 재료를 알 수 없거나 만들 레시피가 없다고 판단한 것이라, 그 안내 문장과 함께 정상 결과로 준다
        if (result.content().recipeIds().isEmpty()) {
            saveLog(memberId, text, AiResultStatus.NO_MATCH, result, List.of());
            return AiFridgeRecommendResponse.noMatch(result.content().answer(), isMartSelected(memberId));
        }
        List<FridgeRecipe> recipes = tools.pick(result.content().recipeIds());
        // 레시피 찾기가 돌려주지 않은 레시피만 골랐으면 답변이 그 레시피를 설명하고 있어 쓸 수 없다
        if (recipes.isEmpty()) {
            saveLog(memberId, text, AiResultStatus.FALLBACK,
                new AiResult<>(result.content(), AiFailureType.INVALID_RESPONSE, result.usage()), List.of());
            return AiFridgeRecommendResponse.fallback(isMartSelected(memberId));
        }
        Optional<Map<Long, IngredientPrice>> prices = tools.findPricesForResponse(recipes);
        saveLog(memberId, text, AiResultStatus.ANSWERED, result,
            recipes.stream().map(FridgeRecipe::recipeId).toList());
        return AiFridgeRecommendResponse.answered(result.content().answer(), recipes, prices);
    }

    // 이용 마트가 없어도 레시피 추천은 할 수 있어 멤버십만 확인하고, 가격·재고는 Tool이 안내한다
    private void validateMembership(Long memberId) {
        if (!membershipService.getSummary(memberId, LocalDateTime.now(clock)).active()) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_REQUIRED);
        }
    }

    private boolean isMartSelected(Long memberId) {
        return memberService.getMartId(memberId).isPresent();
    }

    private void saveLog(Long memberId, String text, AiResultStatus status, AiResult<?> aiResult,
                         List<Long> recipeIds) {
        aiRequestLogService.save(memberId, AiRequestType.FRIDGE, text, status, aiResult, recipeIds, null);
    }
}
