package com.recipick.domain.payment.entity.enums;

public enum PaymentFailureReason {
    EXPIRED,
    CANCELED_BEFORE_PAY,
    AMOUNT_MISMATCH,
    PG_FAILED
}
