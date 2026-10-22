package com.recipick.domain.recipe.dto.response;

public record RecipeAutoCartResponse(
        Long recipeId,
        boolean autoCartAvailable
) {
}
