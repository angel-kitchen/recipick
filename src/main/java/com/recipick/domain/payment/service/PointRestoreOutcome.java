package com.recipick.domain.payment.service;

// 외부 취소에서 포인트를 어떻게 마무리했는지. 예외 대신 결과로 돌려줘 PG에서 끝난 취소의 결제·주문 정합화가 롤백되지 않게 한다
public enum PointRestoreOutcome {
    NO_POINT,
    RESTORED,
    FINALIZED_EARNING_KEPT
}
