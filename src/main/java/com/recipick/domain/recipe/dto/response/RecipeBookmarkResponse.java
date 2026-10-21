package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmark;

import java.time.LocalDateTime;

public record RecipeBookmarkResponse(
        Long recipeId,
        String name,
        CuisineType cuisineType,
        String thumbnailUrl,
        boolean autoCartAvailable,
        LocalDateTime bookmarkedAt
) {

    public static RecipeBookmarkResponse from(RecipeBookmark bookmark) {
        Recipe recipe = bookmark.getRecipe();
        return new RecipeBookmarkResponse(
            recipe.getId(),
            recipe.getName(),
            recipe.getCuisine(),
            recipe.getThumbnailUrl(),
            recipe.isAutoCartEnabled(),
            bookmark.getCreatedAt()
        );
    }
}
