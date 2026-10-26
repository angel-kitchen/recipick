package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.AutoCartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AutoCartItemRepository extends JpaRepository<AutoCartItem, Long> {

    @Query("""
            SELECT i
            FROM AutoCartItem i
            JOIN FETCH i.ingredient
            WHERE i.autoCartRequest.id = :autoCartRequestId
            ORDER BY i.id ASC
            """)
    List<AutoCartItem> findAllWithIngredient(@Param("autoCartRequestId") Long autoCartRequestId);
}
