package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.service.BillingKeyRegistrationOutcome;

// 등록 결과. 키 원문은 어떤 응답에도 넣지 않는다
public record BillingKeyRegisterResponse(
    Long billingKeyId,
    BillingKeyStatus status,
    BillingKeyRegistrationOutcome outcome
) {
}
