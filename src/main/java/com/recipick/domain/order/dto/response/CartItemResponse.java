package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.global.common.SaleUnit;
import java.math.BigDecimal;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String imageUrl,
        Long price,
        BigDecimal saleUnitAmount,
        SaleUnit saleUnit,
        int quantity,
        Long lineAmount,
        int stock,
        CartItemStatus status
) {

    public static CartItemResponse of(CartItem cartItem, ProductSummary product, Long martId) {
        return new CartItemResponse(
                cartItem.getId(),
                product.productId(),
                product.name(),
                product.imageUrl(),
                product.price(),
                product.saleUnitAmount(),
                product.saleUnit(),
                cartItem.getQuantity(),
                product.price() * cartItem.getQuantity(),
                product.totalQuantity(),
                CartItemStatus.of(product, martId, cartItem.getQuantity())
        );
    }
}
