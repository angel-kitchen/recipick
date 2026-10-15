package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.AutoCartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoCartItemRepository extends JpaRepository<AutoCartItem, Long> {
}
