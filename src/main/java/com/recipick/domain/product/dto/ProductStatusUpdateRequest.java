package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusUpdateRequest(
    @NotNull
    ProductStatus status
) {
}
