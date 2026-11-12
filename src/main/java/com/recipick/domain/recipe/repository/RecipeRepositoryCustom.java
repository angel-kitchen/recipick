package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.Recipe;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RecipeRepositoryCustom {

    Page<Recipe> search(RecipeSearchCondition condition, Pageable pageable);

    List<Recipe> findSuggestions(String query, int limit);
}
