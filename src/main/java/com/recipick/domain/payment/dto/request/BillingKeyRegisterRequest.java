package com.recipick.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// issueId는 POST /api/billing-keys/issue-requests가 준 토큰, billingKey는 SDK 발급 결과다.
// 둘 다 원문을 로그에 남기지 않는다
public record BillingKeyRegisterRequest(
    @NotBlank @Size(max = 64) String issueId,
    @NotBlank @Size(max = 255) String billingKey
) {
    @Override
    public String toString() {
        return "BillingKeyRegisterRequest[REDACTED]";
    }
}
