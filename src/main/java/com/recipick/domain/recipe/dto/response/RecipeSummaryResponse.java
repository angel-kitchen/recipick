package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Difficulty;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;

public record RecipeSummaryResponse(
        Long recipeId,
        String name,
        CuisineType cuisineType,
        DishType dishType,
        int baseServings,
        Integer cookingTimeMinutes,
        Difficulty difficulty,
        Integer spicyLevel,
        String thumbnailUrl,
        boolean autoCartAvailable,
        boolean bookmarked
) {

    public static RecipeSummaryResponse of(Recipe recipe, boolean bookmarked) {
        return new RecipeSummaryResponse(
            recipe.getId(),
            recipe.getName(),
            recipe.getCuisine(),
            recipe.getDishType(),
            recipe.getBaseServings(),
            recipe.getCookTimeMin(),
            recipe.getDifficulty(),
            recipe.getSpicyLevel(),
            recipe.getThumbnailUrl(),
            recipe.isAutoCartEnabled(),
            bookmarked
        );
    }
}
