package com.recipick.domain.product.dto.request;

import jakarta.validation.constraints.NotNull;

public record ProductPriceUpdateRequest(
    @NotNull
    Long price
) {
}
