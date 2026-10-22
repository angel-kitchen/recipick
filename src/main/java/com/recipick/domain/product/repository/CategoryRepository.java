package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.keyvalue.core.KeyValueTemplate;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByDeletedFalse();
    boolean existsByName(String name);
    Optional<Category> findByName(String name);
}
