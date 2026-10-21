package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.Ingredient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Page<Ingredient> findByNameContaining(String keyword, Pageable pageable);
}
