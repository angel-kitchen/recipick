package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.CartAddHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartAddHistoryRepository extends JpaRepository<CartAddHistory, Long> {
}
