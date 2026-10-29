package com.recipick.domain.payment.entity.enums;

// POINTS_ONLY는 외부 결제금액이 0원이라 PG를 거치지 않고 주문 생성 트랜잭션 안에서 바로 결제 완료되는 결제다
public enum PaymentMethod {
    CARD,
    POINTS_ONLY
}
