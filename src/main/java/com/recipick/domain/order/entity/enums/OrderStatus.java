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
            case PENDING_PAYMENT -> next == PAID;
            default -> false;
        };
    }
}
