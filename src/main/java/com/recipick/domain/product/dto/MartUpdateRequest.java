package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.MartStatus;

import java.math.BigDecimal;

public record MartUpdateRequest(
    BigDecimal latitude,
    BigDecimal longitude,
    Integer serviceRadiusM,
    MartStatus status
) {
}
