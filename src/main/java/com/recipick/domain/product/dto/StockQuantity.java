package com.recipick.domain.product.dto;

public record StockQuantity(
    Long productId,
    int quantity
) {
}
