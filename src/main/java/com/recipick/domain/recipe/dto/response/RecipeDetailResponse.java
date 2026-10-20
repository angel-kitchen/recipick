package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Difficulty;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.RecipeStep;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;
import java.util.List;

public record RecipeDetailResponse(
        Long recipeId,
        String name,
        CuisineType cuisineType,
        DishType dishType,
        int baseServings,
        int servings,
        Integer cookingTimeMinutes,
        Difficulty difficulty,
        Integer spicyLevel,
        String youtubeUrl,
        boolean autoCartAvailable,
        boolean bookmarked,
        List<IngredientResponse> ingredients,
        List<StepResponse> steps
) {

    public static RecipeDetailResponse of(
            Recipe recipe,
            int servings,
            List<RecipeIngredient> recipeIngredients,
            boolean bookmarked
    ) {
        return new RecipeDetailResponse(
            recipe.getId(),
            recipe.getName(),
            recipe.getCuisine(),
            recipe.getDishType(),
            recipe.getBaseServings(),
            servings,
            recipe.getCookTimeMin(),
            recipe.getDifficulty(),
            recipe.getSpicyLevel(),
            recipe.getYoutubeWatchUrl(),
            recipe.isAutoCartEnabled(),
            bookmarked,
            recipeIngredients.stream()
                .map(recipeIngredient -> IngredientResponse.of(recipeIngredient,
                    servings))
                .toList(),
            recipe.getSteps().stream()
                .map(StepResponse::from)
                .toList()
        );
    }

    public record IngredientResponse(
            Long ingredientId,
            String name,
            BigDecimal amountPerServing,
            BigDecimal requiredAmount,
            SaleUnit unit,
            boolean required
    ) {

        private static IngredientResponse of(RecipeIngredient recipeIngredient, int servings) {
            return new IngredientResponse(
                recipeIngredient.getIngredient().getId(),
                recipeIngredient.getIngredient().getName(),
                recipeIngredient.getAmountPerServing().stripTrailingZeros(),
                recipeIngredient.calculateRequiredAmount(servings).stripTrailingZeros(),
                recipeIngredient.getUnit(),
                recipeIngredient.isRequired()
            );
        }
    }

    public record StepResponse(
            int order,
            String description
    ) {
        private static StepResponse from(RecipeStep step) {
            return new StepResponse(step.order(),  step.description());
        }
    }
}
