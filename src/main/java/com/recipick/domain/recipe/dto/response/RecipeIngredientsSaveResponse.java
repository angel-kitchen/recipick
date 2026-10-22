package com.recipick.domain.recipe.dto.response;

public record RecipeIngredientsSaveResponse(
        Long recipeId,
        int ingredientCount,
        boolean autoCartAvailable,
        boolean autoCartReleased
) {
}
