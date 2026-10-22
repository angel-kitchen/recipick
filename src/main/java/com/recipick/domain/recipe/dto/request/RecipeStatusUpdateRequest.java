package com.recipick.domain.recipe.dto.request;

import com.recipick.domain.recipe.entity.RecipeStatus;
import jakarta.validation.constraints.NotNull;

public record RecipeStatusUpdateRequest(
        @NotNull
        RecipeStatus status
) {
}
