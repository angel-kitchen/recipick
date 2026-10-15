package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
