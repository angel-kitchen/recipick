package com.recipick.domain.product.dto;

import jakarta.validation.constraints.NotNull;

public record ProductPriceUpdateRequest(
    @NotNull
    Long price
) {
}
