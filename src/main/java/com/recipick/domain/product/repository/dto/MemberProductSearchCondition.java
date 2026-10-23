package com.recipick.domain.product.repository.dto;

public record MemberProductSearchCondition(
    Long martId,
    String keyword,
    Long categoryId,
    Long minPrice,
    Long maxPrice
) {
}
