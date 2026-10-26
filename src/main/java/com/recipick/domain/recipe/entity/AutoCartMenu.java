package com.recipick.domain.recipe.entity;

import java.util.List;

public record AutoCartMenu(
        Long recipeId,
        String recipeName,
        int servings,
        List<Long> optionalIngredientIds
) {
}
