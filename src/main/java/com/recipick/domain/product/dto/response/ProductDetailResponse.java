package com.recipick.domain.product.dto.response;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    boolean soldOut,
    Long salePrice,
    int discountRate,
    LocalDateTime discountEndAt
) {

    public static ProductDetailResponse from(Product product, LocalDateTime now) {
        boolean discounted = product.isDiscounted(now);
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
            product.getTotalQuantity() <= 0,
            product.getSalePrice(now),
            discounted ? product.getDiscountRate() : 0,
            discounted ? product.getDiscountEndAt() : null
        );
    }
}
