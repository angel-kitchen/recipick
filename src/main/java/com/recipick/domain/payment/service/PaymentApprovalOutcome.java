package com.recipick.domain.payment.service;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.ErrorCode;
import java.util.Optional;

public enum PaymentApprovalOutcome {

    APPROVED(null),
    ALREADY_PAID(null),

    NOT_PAID(PaymentErrorCode.PAYMENT_NOT_PAID),
    PG_FAILED(PaymentErrorCode.PAYMENT_FAILED),
    ORDER_MISMATCH(PaymentErrorCode.PAYMENT_NOT_FOUND),
    AMOUNT_MISMATCH(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH),
    EXPIRED(PaymentErrorCode.PAYMENT_EXPIRED),
    ALREADY_CLOSED(PaymentErrorCode.PAYMENT_ALREADY_CLOSED),
    INVALID_STATUS(PaymentErrorCode.INVALID_PAYMENT_STATUS);

    private final ErrorCode errorCode;

    PaymentApprovalOutcome(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public Optional<ErrorCode> errorCode() {
        return Optional.ofNullable(errorCode);
    }
}
