package com.recipick.domain.recipe.dto.request;

import com.recipick.global.common.SaleUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IngredientSaveRequest(
        @NotBlank
        @Size(max = 50)
        String name,

        @NotNull
        SaleUnit unit
) {
}
