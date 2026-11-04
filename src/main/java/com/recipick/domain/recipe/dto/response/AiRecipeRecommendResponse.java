package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.dto.RecipeBudget;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.AiRecipeRecommendResolver.Recommendation;
import java.util.List;

// FALLBACK이고 recipes가 비어 있으면 조건 분석부터 실패한 것이라, 화면에서 조건으로 직접 검색하도록 안내한다.
// maxBudget은 분석한 예산이고, 예산을 말했는데 budgetApplied가 false면 이용 마트가 없어 예산 없이 추천한 것이다
public record AiRecipeRecommendResponse(
    AiResultStatus status,
    Integer maxBudget,
    boolean budgetApplied,
    List<RecommendedRecipe> recipes
) {

    public static AiRecipeRecommendResponse recommended(List<Recommendation> recommendations, RecipeBudget budget) {
        return of(AiResultStatus.RECOMMENDED, recommendations, budget);
    }

    public static AiRecipeRecommendResponse noMatch(RecipeBudget budget) {
        return of(AiResultStatus.NO_MATCH, List.of(), budget);
    }

    public static AiRecipeRecommendResponse fallback(List<Recommendation> recommendations, RecipeBudget budget) {
        return of(AiResultStatus.FALLBACK, recommendations, budget);
    }

    private static AiRecipeRecommendResponse of(AiResultStatus status, List<Recommendation> recommendations,
                                                RecipeBudget budget) {
        return new AiRecipeRecommendResponse(status, budget.maxBudget(), budget.applied(), recommendations.stream()
            .map(recommendation -> RecommendedRecipe.of(recommendation.recipe(), recommendation.reason(),
                budget.estimatedPrice(recommendation.recipe().getId())))
            .toList());
    }

    // reason은 AI가 쓴 추천 이유이며, AI 없이 후보를 보여 준 FALLBACK에서는 null이다.
    // estimatedPrice는 예산을 적용했을 때 이용 마트 기준 재료 예상 금액이다
    public record RecommendedRecipe(
        Long recipeId,
        String name,
        CuisineType cuisineType,
        DishType dishType,
        Integer cookingTimeMinutes,
        Integer spicyLevel,
        String thumbnailUrl,
        boolean autoCartAvailable,
        String reason,
        Long estimatedPrice
    ) {

        static RecommendedRecipe of(Recipe recipe, String reason, Long estimatedPrice) {
            return new RecommendedRecipe(
                recipe.getId(),
                recipe.getName(),
                recipe.getCuisine(),
                recipe.getDishType(),
                recipe.getCookTimeMin(),
                recipe.getSpicyLevel(),
                recipe.getThumbnailUrl(),
                recipe.isAutoCartEnabled(),
                reason,
                estimatedPrice);
        }
    }
}
