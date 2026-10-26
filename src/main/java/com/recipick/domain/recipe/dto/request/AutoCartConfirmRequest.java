package com.recipick.domain.recipe.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AutoCartConfirmRequest(
        List<@NotNull @Valid ProductSelection> productSelections
) {

    public record ProductSelection(
            @NotNull
            Long ingredientId,

            @NotNull
            Long productId
    ) {

    }
}
