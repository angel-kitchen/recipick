package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import com.recipick.domain.product.repository.dto.MemberProductSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductRepositoryCustom {
    Page<Product> searchAdminProducts(AdminProductSearchCondition condition, Pageable pageable);

    Page<Product> searchMemberProducts(MemberProductSearchCondition condition, Pageable pageable);

    List<Product> findSuggestions(Long martId, String query, int limit);
}
