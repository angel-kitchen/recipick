package com.recipick.domain.product.dto;

public record StockAdjustmentResponse(

    int beforeQuantity,
    int afterQuantity
) {
}
