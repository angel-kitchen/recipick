package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    @Query("""
            SELECT ri
            FROM RecipeIngredient ri
            JOIN FETCH ri.ingredient
            WHERE ri.recipe.id = :recipeId
            ORDER BY ri.required DESC, ri.id ASC
            """)
    List<RecipeIngredient> findAllWithIngredient(@Param("recipeId") Long recipeId);
}
