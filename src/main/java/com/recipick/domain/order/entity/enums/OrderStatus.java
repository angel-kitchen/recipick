package com.recipick.domain.order.entity.enums;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELED,
    CANCEL_REQUESTED,
    REFUNDED;

    // 허용 전이는 해당 기능을 구현할 때 추가하고, 목록에 없는 전이는 모두 막는다
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING_PAYMENT -> next == PAID || next == CANCELED;
            // PAID → REFUNDED는 PG가 이미 취소를 끝낸 외부 취소 전용이다. 다른 환불은 CANCEL_REQUESTED를 거친다
            case PAID -> next == CANCEL_REQUESTED || next == REFUNDED;
            case CANCEL_REQUESTED -> next == PAID || next == REFUNDED;
            default -> false;
        };
    }
}
