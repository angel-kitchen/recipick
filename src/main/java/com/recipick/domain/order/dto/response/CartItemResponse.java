package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.global.common.SaleUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    // 장바구니 조회와 주문 미리보기가 같은 항목 구조·상태 판정을 내려준다.
    // 상품은 소프트 삭제만 하므로 담긴 상품은 삭제 표시가 된 채로라도 products에 항상 들어 있다
    public static List<CartItemResponse> listOf(List<CartItem> cartItems, List<ProductSummary> products, Long martId) {
        Map<Long, ProductSummary> productsById = products.stream()
                .collect(Collectors.toMap(ProductSummary::productId, Function.identity()));
        return cartItems.stream()
                .map(cartItem -> of(cartItem, productsById.get(cartItem.getProductId()), martId))
                .toList();
    }

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
