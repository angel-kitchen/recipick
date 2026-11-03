package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmark;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

public interface RecipeBookmarkRepository extends JpaRepository<RecipeBookmark, RecipeBookmarkId> {

    @Query("""
            SELECT b.id.recipeId
            FROM RecipeBookmark b
            WHERE b.id.memberId = :memberId
            AND b.id.recipeId IN :recipeIds
            """)
    Set<Long> findBookmarkedRecipeIds(
            @Param("memberId") Long memberId,
            @Param("recipeIds") Collection<Long> recipeIds
    );

    @EntityGraph(attributePaths = "recipe")
    Page<RecipeBookmark> findByIdMemberIdAndRecipeStatus(Long memberId, RecipeStatus status, Pageable pageable);

    @Query(value = """
            SELECT b.recipe
            FROM RecipeBookmark b
            WHERE b.recipe.status = :status
            AND b.createdAt >= :from
            GROUP BY b.recipe
            ORDER BY COUNT(b) DESC, b.recipe.id DESC
            """,
        countQuery = """
            SELECT COUNT(DISTINCT b.recipe.id)
            FROM RecipeBookmark b
            WHERE b.recipe.status = :status
            AND b.createdAt >= :from
            """)
    Page<Recipe> findPopularRecipes(
        @Param("status") RecipeStatus status,
        @Param("from") LocalDateTime from,
        Pageable pageable
    );
}
