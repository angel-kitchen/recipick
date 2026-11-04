package com.recipick.domain.product.dto.response;

import com.recipick.domain.product.entity.Product;

import java.time.LocalDateTime;

public record ProductDiscountResponse(
    Long productId,
    Long price,
    Long salePrice,
    int discountRate,
    LocalDateTime discountEndAt
) {

    public static ProductDiscountResponse from(Product product, LocalDateTime now) {
        boolean discounted = product.isDiscounted(now);
        return new ProductDiscountResponse(
            product.getId(),
            product.getPrice(),
            product.getSalePrice(now),
            discounted ? product.getDiscountRate() : 0,
            discounted ? product.getDiscountEndAt() : null
        );
    }
}
