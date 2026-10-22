package com.recipick.domain.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemAddRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(99) Integer quantity
) {
}
