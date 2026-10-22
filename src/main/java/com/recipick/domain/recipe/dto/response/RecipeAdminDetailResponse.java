package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Difficulty;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.entity.RecipeStep;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record RecipeAdminDetailResponse(
        Long recipeId,
        String name,
        CuisineType cuisineType,
        DishType dishType,
        int baseServings,
        Integer cookingTimeMinutes,
        Difficulty difficulty,
        Integer spicyLevel,
        String youtubeUrl,
        RecipeStatus status,
        boolean autoCartAvailable,
        List<IngredientResponse> ingredients,
        List<StepResponse> steps,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static RecipeAdminDetailResponse of(
        Recipe recipe,
        List<RecipeIngredient> recipeIngredients,
        Map<Long, Long> linkedProductCounts
    ) {
        return new RecipeAdminDetailResponse(
            recipe.getId(),
            recipe.getName(),
            recipe.getCuisine(),
            recipe.getDishType(),
            recipe.getBaseServings(),
            recipe.getCookTimeMin(),
            recipe.getDifficulty(),
            recipe.getSpicyLevel(),
            recipe.getYoutubeWatchUrl(),
            recipe.getStatus(),
            recipe.isAutoCartEnabled(),
            recipeIngredients.stream()
                .map(recipeIngredient -> IngredientResponse.of(
                    recipeIngredient,
                    linkedProductCounts.get(recipeIngredient.getIngredient().getId())))
                .toList(),
            recipe.getSteps().stream()
                .map(StepResponse::from)
                .toList(),
            recipe.getCreatedAt(),
            recipe.getUpdatedAt()
        );
    }

    public record IngredientResponse(
            Long ingredientId,
            String name,
            BigDecimal amountPerServing,
            SaleUnit unit,
            boolean required,
            long linkedProductCount
    ) {

        private static IngredientResponse of(RecipeIngredient recipeIngredient, long linkedProductCount) {
            return new IngredientResponse(
                    recipeIngredient.getIngredient().getId(),
                    recipeIngredient.getIngredient().getName(),
                    recipeIngredient.getAmountPerServing().stripTrailingZeros(),
                    recipeIngredient.getUnit(),
                    recipeIngredient.isRequired(),
                    linkedProductCount
            );
        }
    }

    public record StepResponse(
            int order,
            String description
    ) {

        private static StepResponse from(RecipeStep step) {
            return new StepResponse(step.order(), step.description());
        }
    }
}
