package com.recipick.domain.product.dto.response;

public record StockAdjustmentResponse(

    int beforeQuantity,
    int afterQuantity
) {
}
