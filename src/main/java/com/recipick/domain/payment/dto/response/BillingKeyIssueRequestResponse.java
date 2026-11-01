package com.recipick.domain.payment.dto.response;

import java.time.LocalDateTime;

// 프론트가 SDK requestIssueBillingKey에 그대로 넣는 값. issueId는 이 회원 세션에만 가는 일회성 토큰이라
// toString에서 뺀다(응답은 Cache-Control: no-store)
public record BillingKeyIssueRequestResponse(
    String issueId,
    String issueName,
    String storeId,
    String channelKey,
    String customerId,
    long displayAmount,
    String currency,
    LocalDateTime expiresAt
) {
    @Override
    public String toString() {
        return "BillingKeyIssueRequestResponse[REDACTED]";
    }
}
