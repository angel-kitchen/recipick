package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.MartStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MartUpdateRequest(
    @DecimalMin("-90") @DecimalMax("90")
    BigDecimal latitude,
    @DecimalMin("-180") @DecimalMax("180")
    BigDecimal longitude,
    @Positive
    Integer serviceRadiusM,
    MartStatus status
) {
}
