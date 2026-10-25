package com.recipick.domain.payment.entity.enums;

// 환불 실패 종류. 같은 Idempotency-Key 재요청이 PortOne을 다시 부르지 않고 DB만 보고 원래 응답을 재현하는 데 쓴다
public enum RefundFailureType {

    // PortOne이 cancellation.status = FAILED로 명시한 경우. 같은 키 재요청은 409 PAYMENT-009
    PG_FAILED,

    // PortOne이 요청을 처리하기 전에 400·401·403으로 거절한 경우(우리 요청·인증 설정 문제). 같은 키 재요청은 500 COMMON-008
    REQUEST_REJECTED
}
