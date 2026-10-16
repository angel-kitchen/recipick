package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByDeletedFalse();
    boolean existsByName(String name);
}
