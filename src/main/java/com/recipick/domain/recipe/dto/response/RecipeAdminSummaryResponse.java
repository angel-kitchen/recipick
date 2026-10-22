package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStatus;

import java.time.LocalDateTime;

public record RecipeAdminSummaryResponse(
        Long recipeId,
        String name,
        CuisineType cuisineType,
        DishType dishType,
        RecipeStatus status,
        boolean autoCartAvailable,
        int missingLinkedRequiredCount,
        LocalDateTime updatedAt
) {

    public static RecipeAdminSummaryResponse of(Recipe recipe, int missingLinkedRequiredCount) {
        return new RecipeAdminSummaryResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getCuisine(),
                recipe.getDishType(),
                recipe.getStatus(),
                recipe.isAutoCartEnabled(),
                missingLinkedRequiredCount,
                recipe.getUpdatedAt()
        );
    }
}
