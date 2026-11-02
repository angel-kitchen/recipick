package com.recipick.infra.portone.dto;

public enum PortOneBillingKeyDeletionCode {
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
    CHANNEL_NOT_TEST
}
