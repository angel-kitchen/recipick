package com.recipick.domain.product.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record ProductSearchRequest(
    String keyword,
    Long categoryId,

    @PositiveOrZero
    Long minPrice,

    @PositiveOrZero
    Long maxPrice
    ) {
}
