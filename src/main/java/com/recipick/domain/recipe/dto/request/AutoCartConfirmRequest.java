package com.recipick.domain.recipe.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AutoCartConfirmRequest(
        List<@NotNull @Valid ProductSelection> productSelections,
        List<@NotNull Long> excludedIngredientIds
) {

    public AutoCartConfirmRequest(List<ProductSelection> productSelections) {
        this(productSelections, List.of());
    }

    public record ProductSelection(
            @NotNull
            Long ingredientId,

            @NotNull
            Long productId
    ) {

    }
}
