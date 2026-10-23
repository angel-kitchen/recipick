package com.recipick.domain.order.entity.enums;

// 회원 주문 목록 탭(진행 중 / 배송완료 / 취소·환불)과 1:1로 맞춘 묶음. 탭 하나에 주문·마트 상태가 여러 개 걸려 서버가 조건으로 푼다
public enum OrderStatusGroup {
    ONGOING,
    DELIVERED,
    CANCELED_REFUNDED
}
