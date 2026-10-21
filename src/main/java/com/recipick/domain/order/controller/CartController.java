package com.recipick.domain.order.controller;

import com.recipick.domain.order.service.CartService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @DeleteMapping("/api/cart-items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(
            @LoginMember Long memberId,
            @PathVariable Long cartItemId
    ) {
        cartService.deleteCartItem(memberId, cartItemId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
