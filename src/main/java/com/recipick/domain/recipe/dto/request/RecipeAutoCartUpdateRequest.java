package com.recipick.domain.recipe.dto.request;

import jakarta.validation.constraints.NotNull;

public record RecipeAutoCartUpdateRequest(
        @NotNull
        Boolean autoCartAvailable
) {
}
