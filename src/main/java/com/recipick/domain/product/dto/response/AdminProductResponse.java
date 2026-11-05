package com.recipick.domain.product.dto.response;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// discountRate·discountEndAt은 설정된 값 그대로이고, discounted·salePrice는 지금 시각 기준으로 계산한 값이다
public record AdminProductResponse(
    Long productId,
    Long martId,
    Long categoryId,
    String categoryName,
    Long ingredientId,
    String name,
    String brand,
    String description,
    BigDecimal saleUnitAmount,
    SaleUnit saleUnit,
    String imageUrl,
    Long price,
    ProductStatus status,
    int totalQuantity,
    LocalDateTime createdAt,
    int discountRate,
    LocalDateTime discountEndAt,
    boolean discounted,
    Long salePrice

) {

    public static AdminProductResponse from(Product product, LocalDateTime now) {
        return new AdminProductResponse(
            product.getId(),
            product.getMart().getId(),
            product.getCategory().getId(),
            product.getCategory().getName(),
            product.getIngredientId(),
            product.getName(),
            product.getBrand(),
            product.getDescription(),
            product.getSaleUnitAmount(),
            product.getSaleUnit(),
            product.getImageUrl(),
            product.getPrice(),
            product.getStatus(),
            product.getTotalQuantity(),
            product.getCreatedAt(),
            product.getDiscountRate(),
            product.getDiscountEndAt(),
            product.isDiscounted(now),
            product.getSalePrice(now)
        );
    }
}
