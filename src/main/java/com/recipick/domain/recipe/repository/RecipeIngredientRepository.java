package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {
}
