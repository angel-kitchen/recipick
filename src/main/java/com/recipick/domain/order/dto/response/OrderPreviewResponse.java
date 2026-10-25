package com.recipick.domain.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;

// 미리보기는 판정 결과를 오류가 아니라 상태 필드로 내려준다. 같은 조건의 최종 거절은 주문 생성이 한다.
// 금액은 AVAILABLE 상품만으로 계산하고, 제외 상품과 이전 마트 상품은 items의 status로 알린다
public record OrderPreviewResponse(
        boolean orderable,
        List<UnorderableReason> unorderableReasons,
        Delivery delivery,
        List<CartItemResponse> items,
        long productAmount,
        long deliveryFee,
        long payAmount,
        PendingOrder pendingOrder
) {

    // 선언 순서대로 내려준다
    public enum UnorderableReason {
        MART_NOT_SELECTED,
        MART_NOT_OPEN,
        DELIVERY_UNAVAILABLE,
        HAS_PREVIOUS_MART_ITEMS,
        NO_PURCHASABLE_ITEMS,
        PENDING_ORDER_EXISTS
    }

    public enum DeliveryStatus {
        AVAILABLE,
        OTHER_MART_AREA,
        OUTSIDE_SERVICE_AREA
    }

    // martName은 AVAILABLE이면 이용 마트, OTHER_MART_AREA면 그 지역의 운영 중인 마트, 서비스 지역 밖이면 null이다
    public record Delivery(DeliveryStatus status, String martName) {
    }

    // 결제 대기 주문은 결제가 항상 IN_PROGRESS라(주문·결제가 같은 트랜잭션에서 생성·종료된다) 만료 시각만으로 이어서 결제 가능 여부를 판단한다
    public record PendingOrder(
            Long orderId,
            String orderNumber,
            Long paymentId,
            String orderName,
            Long amount,
            LocalDateTime paymentExpiresAt,
            boolean payable
    ) {
    }
}
