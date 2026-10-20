package com.recipick.domain.recipe.dto.request;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;

public record RecipeSearchRequest(
        String keyword,
        CuisineType cuisineType,
        DishType dishType,
        Boolean autoCartAvailable
) {
}
