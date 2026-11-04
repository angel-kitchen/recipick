package com.recipick.domain.recipe.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.ActiveAiPrompt;
import com.recipick.domain.recipe.dto.RecipeBudget;
import com.recipick.domain.recipe.dto.response.AiRecipeRecommendResponse;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.AiRecipeRecommendResolver;
import com.recipick.domain.recipe.service.AiRecipeRecommendResolver.Recommendation;
import com.recipick.domain.recipe.service.AiPromptService;
import com.recipick.domain.recipe.service.AiRequestLimiter;
import com.recipick.domain.recipe.service.AiRequestLogService;
import com.recipick.domain.recipe.service.AutoCartCalculator;
import com.recipick.domain.recipe.service.AutoCartLine;
import com.recipick.domain.recipe.service.AutoCartService;
import com.recipick.domain.recipe.service.RecipeRecommendCondition;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.domain.recipe.service.RequiredIngredient;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiRecipeCandidate;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// AI 조건 추천의 진입점. AI를 두 번 부르므로 트랜잭션을 걸지 않고, 후보 조회와 이력 저장만 각 Service의 짧은 트랜잭션으로 한다.
// ① AI가 조건 정리 → ② 서버가 조건 검증·후보 조회(예산이 있으면 이용 마트 가격으로 거르기) → ③ AI가 후보 안에서 고르기
// → 서버가 다시 확인 순이다. 예산 계산은 레시피(recipe)·이용 마트(member)·상품 가격(product)을 여기서 조립한다.
@Component
@RequiredArgsConstructor
public class AiRecipeRecommendFacade {

    private final MembershipService membershipService;
    private final MemberService memberService;
    private final RecipeService recipeService;
    private final AutoCartService autoCartService;
    private final ProductIngredientService productIngredientService;
    private final AiRequestLimiter aiRequestLimiter;
    private final AiRequestLogService aiRequestLogService;
    private final AiPromptService aiPromptService;
    private final AiClient aiClient;
    private final Clock clock;

    public AiRecipeRecommendResponse recommend(Long memberId, String text) {
        validateMembership(memberId);
        aiRequestLimiter.acquire(memberId);

        ActiveAiPrompt conditionPrompt = aiPromptService.getActive(AiPromptType.CONDITION);
        AiResult<AiRecipeConditions> conditionResult = aiClient.extractConditions(conditionPrompt.content(), text);
        if (!conditionResult.isSuccess()) {
            saveLog(memberId, text, AiResultStatus.FALLBACK, conditionResult, List.of(), List.of(conditionPrompt));
            return AiRecipeRecommendResponse.fallback(List.of(), RecipeBudget.notRequested());
        }

        RecipeRecommendCondition condition = RecipeRecommendCondition.from(conditionResult.content());
        // "해장용", "두부"처럼 조건으로 옮길 수 없는 문장도 추천하려고, 조건이 없으면 전체 레시피를 후보로 AI가 고른다.
        // 레시피와 관계없는 문장은 AI가 빈 목록을 줘 NO_MATCH가 된다
        List<Recipe> filtered = recipeService.findRecommendCandidates(condition);
        RecipeBudget budget = applyBudget(memberId, condition, filtered);
        List<Recipe> candidates = budget.applied()
            ? filtered.stream().filter(recipe -> budget.estimatedPrices().containsKey(recipe.getId())).toList()
            : filtered;
        if (candidates.isEmpty()) {
            saveLog(memberId, text, AiResultStatus.NO_MATCH, conditionResult, List.of(), List.of(conditionPrompt));
            return AiRecipeRecommendResponse.noMatch(budget);
        }

        ActiveAiPrompt recommendPrompt = aiPromptService.getActive(AiPromptType.RECOMMEND);
        List<ActiveAiPrompt> prompts = List.of(conditionPrompt, recommendPrompt);
        AiResult<List<AiRecommendedRecipe>> pickResult =
            aiClient.recommendRecipes(recommendPrompt.content(), text, toCandidates(candidates));
        // 빈 목록은 AI가 후보 중 맞는 레시피가 없다고 판단한 것이라 장애가 아닌 정상 결과다
        if (pickResult.isSuccess() && pickResult.content().isEmpty()) {
            saveLog(memberId, text, AiResultStatus.NO_MATCH,
                combine(conditionResult, pickResult, null), List.of(), prompts);
            return AiRecipeRecommendResponse.noMatch(budget);
        }
        List<Recommendation> recommendations = pickResult.isSuccess()
            ? AiRecipeRecommendResolver.select(pickResult.content(), candidates)
            : List.of();
        // AI가 답했어도 후보 안의 레시피를 하나도 고르지 못했으면 쓸 수 없는 응답이라 실패로 본다
        if (recommendations.isEmpty()) {
            AiFailureType failureType = pickResult.isSuccess()
                ? AiFailureType.INVALID_RESPONSE
                : pickResult.failureType();
            // 조건 없이 전체를 후보로 삼았을 때는 서버가 거른 후보가 아니라서 아무 레시피나 대신 줄 수 없다
            List<Recommendation> fallback = condition.isEmpty() && !budget.applied()
                ? List.of()
                : AiRecipeRecommendResolver.fallback(candidates);
            saveLog(memberId, text, AiResultStatus.FALLBACK,
                combine(conditionResult, pickResult, failureType), toRecipeIds(fallback), prompts);
            return AiRecipeRecommendResponse.fallback(fallback, budget);
        }
        saveLog(memberId, text, AiResultStatus.RECOMMENDED,
            combine(conditionResult, pickResult, null), toRecipeIds(recommendations), prompts);
        return AiRecipeRecommendResponse.recommended(recommendations, budget);
    }

    // 추천은 장보기와 달리 이용 마트가 필요 없어 멤버십만 확인한다
    private void validateMembership(Long memberId) {
        if (!membershipService.getSummary(memberId, LocalDateTime.now(clock)).active()) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_REQUIRED);
        }
    }

    // 예상 금액은 이용 마트 가격으로만 계산할 수 있어, 마트가 없으면 예산 없이 추천하고 미적용으로 알린다.
    // 재료 정보가 갖춰진 자동 장보기 가능 레시피만 금액을 믿을 수 있어 그 레시피만 남기고, 예산 이하만 금액을 담는다
    private RecipeBudget applyBudget(Long memberId, RecipeRecommendCondition condition, List<Recipe> candidates) {
        if (condition.maxBudget() == null) {
            return RecipeBudget.notRequested();
        }
        Optional<Long> martId = memberService.getMartId(memberId);
        if (martId.isEmpty()) {
            return RecipeBudget.notApplied(condition.maxBudget());
        }
        List<Recipe> priceable = candidates.stream().filter(Recipe::isAutoCartEnabled).toList();
        Map<Long, Long> withinBudget = estimatePrices(martId.get(), priceable, condition.servings()).entrySet().stream()
            .filter(entry -> entry.getValue() <= condition.maxBudget())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new RecipeBudget(condition.maxBudget(), true, withinBudget);
    }

    // 미리보기와 같은 계산(판매 단위 올림, 재료마다 살 수 있는 가장 싼 상품)을 레시피마다 따로 해, 화면의 금액이 미리보기와 같게 한다.
    // 상품은 모든 후보의 재료를 한 번에 조회해 후보 수만큼 쿼리가 늘지 않게 한다
    private Map<Long, Long> estimatePrices(Long martId, List<Recipe> recipes, Integer servings) {
        Map<Long, Integer> servingsByRecipeId = recipes.stream()
            .collect(Collectors.toMap(Recipe::getId, recipe -> servings != null ? servings : recipe.getBaseServings()));
        Map<Long, List<RequiredIngredient>> ingredientsByRecipeId =
            autoCartService.sumRequiredIngredientsByRecipe(servingsByRecipeId);
        Map<Long, List<ProductSummary>> products = productIngredientService.findCandidates(martId,
            ingredientsByRecipeId.values().stream()
                .flatMap(List::stream)
                .map(RequiredIngredient::ingredientId)
                .distinct()
                .toList());
        return recipes.stream().collect(Collectors.toMap(Recipe::getId, recipe -> AutoCartCalculator.calculate(
                ingredientsByRecipeId.getOrDefault(recipe.getId(), List.of()), products, Map.of(), Map.of())
            .stream()
            .mapToLong(AutoCartLine::linePrice)
            .sum()));
    }

    private List<AiRecipeCandidate> toCandidates(List<Recipe> candidates) {
        return AiRecipeCandidates.of(candidates,
            recipeService.findRequiredIngredientNames(candidates.stream().map(Recipe::getId).toList()));
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
                         List<Long> recipeIds, List<ActiveAiPrompt> prompts) {
        aiRequestLogService.save(memberId, AiRequestType.RECOMMENDATION, text, status, aiResult, recipeIds, null,
            prompts);
    }

    // 고르기에서 실패하면 recommendations는 null이라, 이력에는 받은 조건 분석 결과만 남는다
    record ParsedResult(AiRecipeConditions conditions, List<AiRecommendedRecipe> recommendations) {
    }
}
