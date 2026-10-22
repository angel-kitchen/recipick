package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.entity.CartItem;

public record CartItemAddResponse(Long cartItemId, Long productId, int quantity) {

    public static CartItemAddResponse from(CartItem cartItem) {
        return new CartItemAddResponse(cartItem.getId(), cartItem.getProductId(), cartItem.getQuantity());
    }
}
