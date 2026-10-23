package com.recipick.domain.order.entity.enums;

public enum MartStatus {
    NONE,
    RECEIVED,
    PREPARING,
    SHIPPING,
    DELIVERED,
    REJECTED;

    // 허용 전이는 해당 기능을 구현할 때 추가하고, 목록에 없는 전이는 모두 막는다
    public boolean canTransitionTo(MartStatus next) {
        return switch (this) {
            case NONE -> next == RECEIVED;
            default -> false;
        };
    }
}
