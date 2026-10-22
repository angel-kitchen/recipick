package com.recipick.domain.order.controller;

import com.recipick.domain.order.dto.request.CartItemAddRequest;
import com.recipick.domain.order.dto.request.CartItemBulkDeleteRequest;
import com.recipick.domain.order.dto.response.CartItemAddResponse;
import com.recipick.domain.order.facade.CartFacade;
import com.recipick.domain.order.service.CartService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CartFacade cartFacade;

    @PostMapping("/api/cart-items")
    public ResponseEntity<ApiResponse<CartItemAddResponse>> addCartItem(
            @LoginMember Long memberId,
            @Valid @RequestBody CartItemAddRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(cartFacade.addCartItem(memberId, request)));
    }

    @DeleteMapping("/api/cart-items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(
            @LoginMember Long memberId,
            @PathVariable Long cartItemId
    ) {
        cartService.deleteCartItem(memberId, cartItemId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // DELETE 본문은 일부 클라이언트·프록시가 버리므로, ID 목록을 본문으로 받는 일괄 삭제는 POST 하위 경로로 둔다.
    @PostMapping("/api/cart-items/bulk-delete")
    public ResponseEntity<ApiResponse<Void>> deleteCartItems(
            @LoginMember Long memberId,
            @Valid @RequestBody CartItemBulkDeleteRequest request
    ) {
        cartService.deleteCartItems(memberId, request.cartItemIds());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
