package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductSummary(
    Long productId,
    Long martId,
    String name,
    String imageUrl,
    Long price,
    Long originalPrice,
    int discountRate,
    BigDecimal saleUnitAmount,
    SaleUnit saleUnit,
    ProductStatus status,
    int totalQuantity,
    boolean deleted
) {

    public static ProductSummary from(Product product, LocalDateTime now) {
        boolean discounted = product.isDiscounted(now);
        return new ProductSummary(
            product.getId(),
            product.getMart().getId(),
            product.getName(),
            product.getImageUrl(),
            product.getSalePrice(now),
            product.getPrice(),
            discounted ? product.getDiscountRate() : 0,
            product.getSaleUnitAmount(),
            product.getSaleUnit(),
            product.getStatus(),
            product.getTotalQuantity(),
            product.isDeleted()
        );
    }
}
