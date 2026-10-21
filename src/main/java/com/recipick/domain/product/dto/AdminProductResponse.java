package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminProductResponse(
    Long productId,
    Long martId,
    Long categoryId,
    String categoryName,
    Long ingredientId,
    String name,
    String brand,
    BigDecimal saleUnitAmount,
    SaleUnit saleUnit,
    String imageUrl,
    Long price,
    ProductStatus status,
    int totalQuantity,
    LocalDateTime createdAt

) {

    public static AdminProductResponse from(Product product) {
        return new AdminProductResponse(
            product.getId(),
            product.getMart().getId(),
            product.getCategory().getId(),
            product.getCategory().getName(),
            product.getIngredientId(),
            product.getName(),
            product.getBrand(),
            product.getSaleUnitAmount(),
            product.getSaleUnit(),
            product.getImageUrl(),
            product.getPrice(),
            product.getStatus(),
            product.getTotalQuantity(),
            product.getCreatedAt()
        );
    }
}

