package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.RecipeIngredient;
import jakarta.persistence.criteria.From;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    @Query("""
            SELECT ri
            FROM RecipeIngredient ri
            JOIN FETCH ri.ingredient
            WHERE ri.recipe.id IN :recipeIds
            ORDER BY ri.recipe.id ASC, ri.required DESC , ri.id ASC
            """)
    List<RecipeIngredient> findAllWithIngredientByRecipeIds(@Param("recipeIds") Collection<Long> recipeIds);

    @Query("""
            SELECT ri
            FROM RecipeIngredient ri
            JOIN FETCH ri.ingredient
            WHERE ri.recipe.id = :recipeId
            ORDER BY ri.required DESC, ri.id ASC
            """)
    List<RecipeIngredient> findAllWithIngredient(@Param("recipeId") Long recipeId);
    boolean existsByIngredientId(Long ingredientId);

    // name은 LIKE 특수문자를 '!'로 이스케이프한 값이어야 한다. MySQL 문자열에서 백슬래시는 그 자체로 이스케이프 문자라 '!'를 쓴다
    @Query("""
            SELECT DISTINCT ri.recipe.id
            FROM RecipeIngredient ri
            WHERE ri.ingredient.name LIKE CONCAT('%', :name, '%') ESCAPE '!'
            """)
    List<Long> findRecipeIdsByIngredientNameContaining(@Param("name") String name);

    @Query("""
            SELECT ri.recipe.id AS recipeId, ri.ingredient.id AS ingredientId
            FROM RecipeIngredient ri
            WHERE ri.recipe.id IN :recipeIds
              AND ri.required = true
            """)
    List<RequiredIngredientRef> findRequiredIngredientRefs(@Param("recipeIds") Collection<Long> recipeIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RecipeIngredient ri WHERE ri.recipe.id = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);
}
