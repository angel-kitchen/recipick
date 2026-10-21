package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.RecipeBookmark;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
