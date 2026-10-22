package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    Page<StockAdjustment> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);
}

