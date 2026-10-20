package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long>,  RecipeRepositoryCustom {

    Optional<Recipe> findByIdAndStatus(Long id, RecipeStatus status);
}
