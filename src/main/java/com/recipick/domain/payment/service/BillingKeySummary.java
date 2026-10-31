package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import java.time.LocalDateTime;

// 회원 화면용 빌링키 요약. 키 원문은 담지 않는다
public record BillingKeySummary(
    Long billingKeyId,
    BillingKeyStatus status,
    LocalDateTime issuedAt
) {

    public static BillingKeySummary from(BillingKey billingKey) {
        return new BillingKeySummary(billingKey.getId(), billingKey.getStatus(), billingKey.getIssuedAt());
    }
}
