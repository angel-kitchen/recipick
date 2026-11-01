package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.service.BillingKeySummary;
import java.time.LocalDateTime;

// 내 카드 등록 여부. 카드사·끝 4자리는 저장하지 않으므로 등록됨·등록일만 준다
public record BillingKeyMeResponse(
    boolean registered,
    Long billingKeyId,
    BillingKeyStatus status,
    LocalDateTime issuedAt
) {

    public static BillingKeyMeResponse of(BillingKeySummary summary) {
        return new BillingKeyMeResponse(true, summary.billingKeyId(), summary.status(), summary.issuedAt());
    }

    public static BillingKeyMeResponse none() {
        return new BillingKeyMeResponse(false, null, null, null);
    }
}
