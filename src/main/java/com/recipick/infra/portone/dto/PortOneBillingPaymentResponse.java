package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

// POST /payments/{paymentId}/billing-key 성공 응답 중 분류에 필요한 필드만 담은 와이어 DTO.
// 결제 확정은 이 응답이 아니라 같은 ID의 GET /payments/{id} 재조회로 하므로 더 읽지 않는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneBillingPaymentResponse(
    Payment payment
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(
        String pgTxId,
        OffsetDateTime paidAt
    ) {
    }
}
