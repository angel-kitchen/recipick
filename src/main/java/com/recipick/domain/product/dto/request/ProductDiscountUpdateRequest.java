package com.recipick.domain.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ProductDiscountUpdateRequest(
    @NotNull @Min(1) @Max(99) Integer discountRate,
    @NotNull LocalDateTime discountEndAt
) {
}
