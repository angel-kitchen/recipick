package com.recipick.domain.recipe.dto.request;

import com.recipick.domain.recipe.entity.RecipeStep;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecipeStepRequest(
        @NotNull
        @Min(1)
        Integer order,

        @NotBlank
        String description
) {

    public RecipeStep toStep() {
        return new RecipeStep(order, description);
    }
}
