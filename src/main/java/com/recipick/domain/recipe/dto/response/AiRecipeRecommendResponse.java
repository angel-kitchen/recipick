package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.AiRecipeRecommendResolver.Recommendation;
import java.util.List;

// FALLBACK이고 recipes가 비어 있으면 조건 분석부터 실패한 것이라, 화면에서 조건으로 직접 검색하도록 안내한다
public record AiRecipeRecommendResponse(
    AiResultStatus status,
    List<RecommendedRecipe> recipes
) {

    public static AiRecipeRecommendResponse recommended(List<Recommendation> recommendations) {
        return new AiRecipeRecommendResponse(AiResultStatus.RECOMMENDED, toRecipes(recommendations));
    }

    public static AiRecipeRecommendResponse noMatch() {
        return new AiRecipeRecommendResponse(AiResultStatus.NO_MATCH, List.of());
    }

    public static AiRecipeRecommendResponse fallback(List<Recommendation> recommendations) {
        return new AiRecipeRecommendResponse(AiResultStatus.FALLBACK, toRecipes(recommendations));
    }

    private static List<RecommendedRecipe> toRecipes(List<Recommendation> recommendations) {
        return recommendations.stream()
            .map(recommendation -> RecommendedRecipe.of(recommendation.recipe(), recommendation.reason()))
            .toList();
    }

    // reason은 AI가 쓴 추천 이유이며, AI 없이 후보를 보여 준 FALLBACK에서는 null이다
    public record RecommendedRecipe(
        Long recipeId,
        String name,
        CuisineType cuisineType,
        DishType dishType,
        Integer cookingTimeMinutes,
        Integer spicyLevel,
        String thumbnailUrl,
        boolean autoCartAvailable,
        String reason
    ) {

        static RecommendedRecipe of(Recipe recipe, String reason) {
            return new RecommendedRecipe(
                recipe.getId(),
                recipe.getName(),
                recipe.getCuisine(),
                recipe.getDishType(),
                recipe.getCookTimeMin(),
                recipe.getSpicyLevel(),
                recipe.getThumbnailUrl(),
                recipe.isAutoCartEnabled(),
                reason);
        }
    }
}
