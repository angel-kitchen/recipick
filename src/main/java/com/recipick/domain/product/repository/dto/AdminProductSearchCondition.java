package com.recipick.domain.product.repository.dto;

import com.recipick.domain.product.entity.ProductStatus;

public record AdminProductSearchCondition(
    Long martId,
    String keyword,
    Long categoryId,
    Long ingredientId,
    ProductStatus status
) {
}
