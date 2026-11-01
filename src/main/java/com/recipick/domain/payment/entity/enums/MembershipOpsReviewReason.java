package com.recipick.domain.payment.entity.enums;

// 시도가 MANUAL_REVIEW로 넘어간 이유. 운영 화면·로그용. 값은 이용권 생명주기 설계안의 이름을 그대로 쓴다
public enum MembershipOpsReviewReason {
    LATE_PAID_AFTER_DEADLINE,
    PAID_CONFLICT,
    PAID_ON_CLOSED_PAYMENT,
    FIRST_PAYMENT_PERIOD_ENDED,
    FIRST_PAYMENT_LONG_DELAY,
    RENEWAL_PERIOD_ENDED,
    RENEWAL_LONG_DELAY,
    SYSTEM_REFUND_FAILED,
    PAID_AMOUNT_MISMATCH
}
