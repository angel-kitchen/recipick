package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.RecipeBookmark;

import java.time.LocalDateTime;

public record RecipeBookmarkCreateResponse(
        Long recipeId,
        LocalDateTime bookmarkedAt
) {
    public static RecipeBookmarkCreateResponse from(RecipeBookmark bookmark) {
        return new RecipeBookmarkCreateResponse(bookmark.getId().getRecipeId(), bookmark.getCreatedAt());
    }
}
