package com.recipick.infra.portone.dto;

import java.util.Objects;

// 키 원문과 PG 원문을 보유하지 않는 검증 결과다.
public record PortOneBillingKeyDeletionObservation(Kind kind, PortOneBillingKeyDeletionCode code) {
    public enum Kind {
        ISSUED, ABSENT, UNKNOWN, REVIEW_REQUIRED
    }

    public PortOneBillingKeyDeletionObservation {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(code, "code");
        if (kind == Kind.ABSENT && code != PortOneBillingKeyDeletionCode.DELETED
            && code != PortOneBillingKeyDeletionCode.BILLING_KEY_NOT_FOUND) {
            throw new IllegalArgumentException("삭제 확정 근거가 올바르지 않습니다");
        }
    }

    public static PortOneBillingKeyDeletionObservation found(
        String expectedKey, String expectedStoreId, String status, String key, String storeId
    ) {
        if (expectedKey == null || expectedKey.isBlank() || expectedStoreId == null || expectedStoreId.isBlank()) {
            throw new IllegalArgumentException("조회 대상이 올바르지 않습니다");
        }
        if (status == null || key == null || storeId == null) {
            return unknown(PortOneBillingKeyDeletionCode.INVALID_RESPONSE);
        }
        if (!expectedKey.equals(key) || !expectedStoreId.equals(storeId)) {
            return new PortOneBillingKeyDeletionObservation(Kind.REVIEW_REQUIRED,
                PortOneBillingKeyDeletionCode.IDENTITY_MISMATCH);
        }
        return switch (status) {
            case "ISSUED" -> new PortOneBillingKeyDeletionObservation(Kind.ISSUED, PortOneBillingKeyDeletionCode.ISSUED);
            case "DELETED" -> new PortOneBillingKeyDeletionObservation(Kind.ABSENT, PortOneBillingKeyDeletionCode.DELETED);
            default -> unknown(PortOneBillingKeyDeletionCode.INVALID_RESPONSE);
        };
    }

    public static PortOneBillingKeyDeletionObservation error(int httpStatus, String type) {
        if (httpStatus == 404 && "BILLING_KEY_NOT_FOUND".equals(type)) {
            return new PortOneBillingKeyDeletionObservation(Kind.ABSENT,
                PortOneBillingKeyDeletionCode.BILLING_KEY_NOT_FOUND);
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return new PortOneBillingKeyDeletionObservation(Kind.REVIEW_REQUIRED, PortOneBillingKeyDeletionCode.AUTH_ERROR);
        }
        if (httpStatus == 400) {
            return new PortOneBillingKeyDeletionObservation(Kind.REVIEW_REQUIRED,
                PortOneBillingKeyDeletionCode.REQUEST_REJECTED);
        }
        return unknown(PortOneBillingKeyDeletionCode.HTTP_ERROR);
    }

    public static PortOneBillingKeyDeletionObservation unknown(PortOneBillingKeyDeletionCode code) {
        return new PortOneBillingKeyDeletionObservation(Kind.UNKNOWN, code);
    }
}
