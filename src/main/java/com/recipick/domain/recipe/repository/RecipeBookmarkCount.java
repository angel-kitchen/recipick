package com.recipick.domain.recipe.repository;

// 레시피별 찜 수 집계 결과
public interface RecipeBookmarkCount {

    Long getRecipeId();

    long getBookmarkCount();
}
