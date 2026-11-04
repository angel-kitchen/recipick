package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.service.AiMealPlanResolver.PlannedMeal;
import java.util.List;

// PREVIEWED만 meals·preview를 채운다. reason은 AI가 쓴 고른 이유이고, 재료·금액은 preview의 서버 계산 값이다
public record AiMealPlanResponse(
    AiResultStatus status,
    List<Meal> meals,
    AutoCartPreviewResponse preview
) {

    public static AiMealPlanResponse previewed(List<PlannedMeal> meals, AutoCartPreviewResponse preview) {
        return new AiMealPlanResponse(AiResultStatus.PREVIEWED, meals.stream().map(Meal::from).toList(), preview);
    }

    public static AiMealPlanResponse noMatch() {
        return new AiMealPlanResponse(AiResultStatus.NO_MATCH, List.of(), null);
    }

    public static AiMealPlanResponse fallback() {
        return new AiMealPlanResponse(AiResultStatus.FALLBACK, List.of(), null);
    }

    public record Meal(Long recipeId, String name, String thumbnailUrl, int servings, String reason) {

        static Meal from(PlannedMeal meal) {
            return new Meal(meal.recipe().getId(), meal.recipe().getName(), meal.recipe().getThumbnailUrl(),
                meal.servings(), meal.reason());
        }
    }
}
