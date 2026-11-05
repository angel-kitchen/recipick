package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.Recipe;

public record RecipeSuggestionResponse(
    Long recipeId,
    String name,
    String thumbnailUrl
) {

    public static RecipeSuggestionResponse from(Recipe recipe) {
        return new RecipeSuggestionResponse(recipe.getId(), recipe.getName(), recipe.getThumbnailUrl());
    }
}
