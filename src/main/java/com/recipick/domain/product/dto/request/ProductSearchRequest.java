package com.recipick.domain.product.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record ProductSearchRequest(
    String keyword,
    Long categoryId,

    @PositiveOrZero
    Long minPrice,

    @PositiveOrZero
    Long maxPrice,

    // true면 지금 할인 중인 상품만 조회한다
    Boolean onSale
    ) {
}
