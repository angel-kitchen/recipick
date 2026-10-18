package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);
}
