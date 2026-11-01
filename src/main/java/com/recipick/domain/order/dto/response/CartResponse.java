package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.service.OrderAmount;
import java.util.List;

// 선택 항목은 서버에 저장하지 않아 결제 금액 패널은 프론트가 계산한다. 무료배송 기준은 서버만 알므로 함께 내려준다.
// 실제 금액은 미리보기·주문 생성에서 서버가 다시 계산한다
public record CartResponse(
        List<CartItemResponse> items,
        boolean hasPreviousMartItems,
        DeliveryPolicy deliveryPolicy
) {

    public static CartResponse of(List<CartItemResponse> items, boolean membershipActive) {
        boolean hasPreviousMartItems = items.stream()
                .anyMatch(item -> item.status() == CartItemStatus.PREVIOUS_MART);
        return new CartResponse(items, hasPreviousMartItems,
                new DeliveryPolicy(OrderAmount.freeDeliveryThreshold(membershipActive), OrderAmount.DELIVERY_FEE));
    }

    public record DeliveryPolicy(long freeDeliveryThreshold, long deliveryFee) {
    }
}
