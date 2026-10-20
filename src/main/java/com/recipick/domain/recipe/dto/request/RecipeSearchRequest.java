package com.recipick.domain.recipe.dto.request;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import jakarta.validation.constraints.Size;

public record RecipeSearchRequest(
        // 레시피명(recipe.name)이 varchar(50)이라 그보다 긴 검색어는 맞을 수 없다
        @Size(max = 50)
        String keyword,
        CuisineType cuisineType,
        DishType dishType,
        Boolean autoCartAvailable
) {
}
