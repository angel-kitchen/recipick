package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.StockAdjustmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(

    @NotNull
    StockAdjustmentType type,

    @NotNull
    Integer quantity,

    @NotBlank
    @Size(max = 500)
    String reason

) {
}
