package com.recipick.domain.recipe.service;

import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;

public record RequiredIngredient(
        Long ingredientId,
        String name,
        SaleUnit unit,
        BigDecimal totalRequiredAMount
) {

    public RequiredIngredient add(RequiredIngredient other) {
        return new RequiredIngredient(ingredientId, name, unit, totalRequiredAMount.add(other.totalRequiredAMount));
    }
}
