package com.recipick.domain.recipe.service;

import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;

public record RequiredIngredient(
        Long ingredientId,
        String name,
        SaleUnit unit,
        BigDecimal totalRequiredAmount
) {

    public RequiredIngredient add(RequiredIngredient other) {
        return new RequiredIngredient(ingredientId, name, unit, totalRequiredAmount.add(other.totalRequiredAmount));
    }
}
