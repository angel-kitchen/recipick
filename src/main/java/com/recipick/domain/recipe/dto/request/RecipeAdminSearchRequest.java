package com.recipick.domain.recipe.dto.request;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.RecipeStatus;
import jakarta.validation.constraints.Size;

public record RecipeAdminSearchRequest(
        @Size(max = 50)
        String keyword,
        CuisineType cuisineType,
        DishType dishType,
        Boolean autoCartAvailable,
        RecipeStatus status
) {
}
