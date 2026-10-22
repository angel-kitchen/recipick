package com.recipick.domain.order.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.order.dto.request.CartItemAddRequest;
import com.recipick.domain.order.dto.response.CartItemAddResponse;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.service.ProductService;
import com.recipick.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CartFacade {

    private final MemberService memberService;
    private final ProductService productService;
    private final CartService cartService;

    @Transactional
    public CartItemAddResponse addCartItem(Long memberId, CartItemAddRequest request) {
        Long martId = memberService.getMartId(memberId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.MART_NOT_SELECTED));
        ProductSummary product = productService.findProducts(List.of(request.productId())).stream()
                .filter(summary -> !summary.deleted())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
        if (!product.martId().equals(martId)
                || product.status() != ProductStatus.SELLING
                || product.totalQuantity() == 0) {
            throw new BusinessException(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE);
        }
        boolean hasPreviousMartItems = productService.findProducts(cartService.findProductIds(memberId)).stream()
                .anyMatch(summary -> !summary.martId().equals(martId));
        if (hasPreviousMartItems) {
            throw new BusinessException(OrderErrorCode.CART_HAS_PREVIOUS_MART_ITEMS);
        }
        return cartService.addCartItem(memberId, product.productId(), request.quantity(), product.totalQuantity());
    }
}
