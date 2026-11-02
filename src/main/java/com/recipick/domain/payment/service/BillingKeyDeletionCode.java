package com.recipick.domain.payment.service;

// 외부 응답 원문 대신 저장하고 기록하는 허용 목록이다.
public enum BillingKeyDeletionCode {
    ISSUED,
    DELETED,
    BILLING_KEY_NOT_FOUND,
    DELETE_ACCEPTED,
    STILL_ISSUED,
    HTTP_ERROR,
    TIMEOUT,
    INVALID_RESPONSE,
    AUTH_ERROR,
    REQUEST_REJECTED,
    IDENTITY_MISMATCH,
    STATE_CONFLICT,
    UNRESOLVED_ATTEMPT,
    WINDOW_EXPIRED,
    CHANNEL_NOT_TEST,
    SCOPE_UNPROVEN;

    public static BillingKeyDeletionCode fromPortOne(
        com.recipick.infra.portone.dto.PortOneBillingKeyDeletionCode code
    ) {
        return valueOf(code.name());
    }
}
