package com.recipick.domain.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record ProductDiscountBulkRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> productIds,
    @NotNull @Min(1) @Max(99) Integer discountRate,
    @NotNull LocalDateTime discountEndAt
) {
}
