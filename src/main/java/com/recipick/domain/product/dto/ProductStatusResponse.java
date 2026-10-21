package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;

public record ProductStatusResponse(
    Long productId,
    ProductStatus status
) {

    public static ProductStatusResponse from(Product product){
        return new ProductStatusResponse(product.getId(), product.getStatus());
    }
}
