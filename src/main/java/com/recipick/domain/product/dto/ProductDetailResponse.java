package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;

public record ProductDetailResponse(
    Long productId,
    String name,
    String brand,
    String description,
    String categoryName,
    String imageUrl,
    BigDecimal saleUnitAmount,
    SaleUnit saleUnit,
    Long price,
    ProductStatus status,
    boolean soldOut
) {

    public static ProductDetailResponse from(Product product){
        return new ProductDetailResponse(
            product.getId(),
            product.getName(),
            product.getBrand(),
            product.getDescription(),
            product.getCategory().getName(),
            product.getImageUrl(),
            product.getSaleUnitAmount(),
            product.getSaleUnit(),
            product.getPrice(),
            product.getStatus(),
            product.getTotalQuantity() <= 0
        );
    }
}
