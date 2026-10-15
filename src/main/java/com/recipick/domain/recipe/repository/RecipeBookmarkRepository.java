package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.RecipeBookmark;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeBookmarkRepository extends JpaRepository<RecipeBookmark, RecipeBookmarkId> {
}
