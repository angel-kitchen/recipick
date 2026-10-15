package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
