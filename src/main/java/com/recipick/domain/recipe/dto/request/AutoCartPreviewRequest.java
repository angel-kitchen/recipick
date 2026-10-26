package com.recipick.domain.recipe.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;


public record AutoCartPreviewRequest(
        @NotEmpty
        @Size(max = 5)
        List<@NotNull @Valid Menu> menus
) {

    public record Menu(
            @NotNull
            Long recipeId,

            @NotNull
            Integer servings,

            List<@NotNull Long> optionalIngredientIds
    ) {

    }
}
