package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {
    Page<Product> searchAdminProducts(AdminProductSearchCondition condition, Pageable pageable);
}
