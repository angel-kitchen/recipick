package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.enums.BillingKeyDeletionReason;
import java.time.LocalDateTime;
import java.util.Objects;

// PG 호출에만 전달한다. 응답 DTO 또는 감사 details로 직렬화하지 않는다.
public record BillingKeyDeletionTicket(
    Long deletionId,
    Long billingKeyId,
    Long memberId,
    String billingKey,
    String storeId,
    BillingKeyDeletionReason reason,
    long token,
    LocalDateTime requestedAt,
    boolean observationOnly
) {
    public BillingKeyDeletionTicket {
        Objects.requireNonNull(deletionId, "deletionId");
        Objects.requireNonNull(billingKeyId, "billingKeyId");
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (billingKey == null || billingKey.isBlank() || storeId == null || storeId.isBlank() || token <= 0) {
            throw new IllegalArgumentException("빌링키 삭제 실행 값이 올바르지 않습니다");
        }
    }

    @Override
    public String toString() {
        return "BillingKeyDeletionTicket[deletionId=" + deletionId + ", billingKeyId=" + billingKeyId
            + ", memberId=" + memberId + ", token=" + token + ", observationOnly=" + observationOnly + "]";
    }
}
