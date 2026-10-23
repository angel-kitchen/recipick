package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Product;

public record ProductPriceResponse(
    Long productId,
    Long price
) {

    public static ProductPriceResponse from(Product product){
        return new ProductPriceResponse(product.getId(), product.getPrice());
    }
}
