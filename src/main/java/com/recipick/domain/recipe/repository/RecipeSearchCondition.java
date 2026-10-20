package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.RecipeStatus;

public record RecipeSearchCondition(
    String keyword,
    CuisineType cuisineType,
    DishType dishType,
    Boolean autoCartAvailable,
    RecipeStatus status
) {
}
