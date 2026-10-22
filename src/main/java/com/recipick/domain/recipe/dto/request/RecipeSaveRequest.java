package com.recipick.domain.recipe.dto.request;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Difficulty;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecipeSaveRequest(
        @NotBlank
        @Size(max = 50)
        String name,

        @NotNull
        CuisineType cuisineType,

        @NotNull
        DishType dishType,

        @NotNull
        @Min(Recipe.MIN_SERVINGS)
        @Max(Recipe.MAX_SERVINGS)
        Integer baseServings,

        @NotNull
        @Min(1)
        Integer cookingTimeMinutes,

        @NotNull
        Difficulty difficulty,

        @NotNull
        @Min(0)
        @Max(3)
        Integer spicyLevel,

        String youtubeUrl,

        @NotEmpty
        List<@Valid RecipeStepRequest> steps
) {
}
