package com.recipick.domain.recipe.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record RecipeIngredientsSaveRequest(
        @NotNull
        List<@Valid Item> ingredients
) {

    public record Item(
            @NotNull
            Long ingredientId,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 8, fraction = 2)
            BigDecimal amountPerServing,

            @NotNull
            Boolean required
    ) {
    }
}
