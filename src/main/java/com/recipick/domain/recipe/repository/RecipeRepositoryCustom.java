package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RecipeRepositoryCustom {

    Page<Recipe> search(RecipeSearchCondition condition, Pageable pageable);
}
