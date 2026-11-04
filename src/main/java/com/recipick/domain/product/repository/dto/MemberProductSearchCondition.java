package com.recipick.domain.product.repository.dto;

import java.time.LocalDateTime;

public record MemberProductSearchCondition(
    Long martId,
    String keyword,
    Long categoryId,
    Long minPrice,
    Long maxPrice,
    LocalDateTime now,
    boolean onSale
) {

    public MemberProductSearchCondition(
        Long martId, String keyword, Long categoryId, Long minPrice, Long maxPrice, LocalDateTime now) {
        this(martId, keyword, categoryId, minPrice, maxPrice, now, false);
    }
}
