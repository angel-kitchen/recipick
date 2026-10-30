package com.recipick.domain.recipe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRecipeRecommendRequest(
        @NotBlank
        @Size(max = 200)
        String text
) {
}
