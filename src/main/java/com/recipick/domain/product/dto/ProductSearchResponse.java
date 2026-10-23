package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;

public record ProductSearchResponse(
    Long productId,
    String name,
    String brand,
    String imageUrl,
    BigDecimal saleUnitAmount,
    SaleUnit saleUnit,
    Long price,
    ProductStatus status,
    boolean soldOut
) {

    public static ProductSearchResponse from(Product product){
        return new ProductSearchResponse(
            product.getId(),
            product.getName(),
            product.getBrand(),
            product.getImageUrl(),
            product.getSaleUnitAmount(),
            product.getSaleUnit(),
            product.getPrice(),
            product.getStatus(),
            product.getTotalQuantity() <= 0
        );
    }
}
