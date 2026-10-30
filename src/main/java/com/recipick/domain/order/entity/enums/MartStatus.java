package com.recipick.domain.order.entity.enums;

public enum MartStatus {
    NONE,
    RECEIVED,
    PREPARING,
    SHIPPING,
    DELIVERED,
    REJECTED;

    // 허용 전이는 해당 기능을 구현할 때 추가하고, 목록에 없는 전이는 모두 막는다
    // 마트 처리는 접수 → 배송준비 → 배송중 → 배송완료 순서로만 나아가고, 거절은 접수 단계에서만 할 수 있다.
    // 같은 상태로의 전이는 막는다 — 같은 요청의 반복은 Service가 먼저 판정한다
    public boolean canTransitionTo(MartStatus next) {
        return switch (this) {
            case NONE -> next == RECEIVED;
            case RECEIVED -> next == PREPARING || next == REJECTED;
            case PREPARING -> next == SHIPPING;
            case SHIPPING -> next == DELIVERED;
            case DELIVERED, REJECTED -> false;
        };
    }
}
