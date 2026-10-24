package com.recipick.domain.order.dto.response;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;

// 화면은 AVAILABLE만 선택·수량 변경을 허용하고, INSUFFICIENT_STOCK은 재고 이하로 줄이는 수량 변경만,
// 나머지는 삭제만 허용한다. 여러 조건이 겹치면 선언 순서가 앞선 상태로 판정한다
public enum CartItemStatus {
    PREVIOUS_MART,
    DELETED,
    STOPPED,
    SOLD_OUT,
    INSUFFICIENT_STOCK,
    AVAILABLE;

    // 이용 마트가 없는 회원은 담긴 상품이 모두 현재 이용 마트와 다르므로 이전 마트 상품으로 본다
    public static CartItemStatus of(ProductSummary product, Long martId, int quantity) {
        if (!product.martId().equals(martId)) {
            return PREVIOUS_MART;
        }
        if (product.deleted()) {
            return DELETED;
        }
        if (product.status() != ProductStatus.SELLING) {
            return STOPPED;
        }
        if (product.totalQuantity() == 0) {
            return SOLD_OUT;
        }
        if (product.totalQuantity() < quantity) {
            return INSUFFICIENT_STOCK;
        }
        return AVAILABLE;
    }
}
