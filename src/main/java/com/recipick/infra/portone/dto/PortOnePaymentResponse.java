package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

/**
 * {@code GET /payments/{paymentId}} 응답 중 결제 확정에 필요한 필드만 담은 와이어 DTO.
 * paidAt은 RFC 3339 문자열이라 OffsetDateTime으로 받고, customData는 PortOne이 JSON 문자열로 돌려준다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String id,
        String transactionId,
        PortOnePaymentStatus status,
        PortOnePaymentAmount amount,
        String currency,
        OffsetDateTime paidAt,
        String customData,
        PortOnePaymentFailure failure
) {
}
