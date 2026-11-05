package com.recipick.domain.product.dto.response;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.global.common.SaleUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// price는 정상가, salePrice는 지금 적용되는 판매가다. 할인이 없거나 끝났으면 둘이 같고 discountRate는 0이다
public record ProductSearchResponse(
    Long productId,
    String name,
    String brand,
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

    public static ProductSearchResponse from(Product product, LocalDateTime now) {
        boolean discounted = product.isDiscounted(now);
        return new ProductSearchResponse(
            product.getId(),
            product.getName(),
            product.getBrand(),
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
