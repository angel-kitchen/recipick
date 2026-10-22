package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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
    boolean existsByIngredientId(Long ingredientId);

    @Query("""
            SELECT ri.recipe.id AS recipeId, ri.ingredient.id AS ingredientId
            FROM RecipeIngredient ri
            WHERE ri.recipe.id IN :recipeIds
              AND ri.required = true
            """)
    List<RequiredIngredientRef> findRequiredIngredientRefs(@Param("recipeIds") Collection<Long> recipeIds);
}
