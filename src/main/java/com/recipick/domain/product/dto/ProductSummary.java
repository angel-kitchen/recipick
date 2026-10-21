package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;

public record ProductSummary(
    Long productId,
    Long martId,
    String name,
    String imageUrl,
    Long price,
    BigDecimal saleUnitAmount,
    SaleUnit saleUnit,
    ProductStatus status,
    int totalQuantity,
    boolean deleted
) {

    public static ProductSummary from(Product product) {
        return new ProductSummary(
            product.getId(),
            product.getMart().getId(),
            product.getName(),
            product.getImageUrl(),
            product.getPrice(),
            product.getSaleUnitAmount(),
            product.getSaleUnit(),
            product.getStatus(),
            product.getTotalQuantity(),
            product.isDeleted()
        );
    }
}
