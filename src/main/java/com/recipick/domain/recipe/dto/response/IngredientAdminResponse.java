package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.global.common.SaleUnit;

public record IngredientAdminResponse(
        Long ingredientId,
        String name,
        SaleUnit unit,
        long linkedProductCount
) {

    public static IngredientAdminResponse of(Ingredient ingredient, long linkedProductCount) {
        return new IngredientAdminResponse(
            ingredient.getId(),
            ingredient.getName(),
            ingredient.getUnit(),
            linkedProductCount
        );
    }
}
