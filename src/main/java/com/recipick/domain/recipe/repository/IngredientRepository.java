package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
}
