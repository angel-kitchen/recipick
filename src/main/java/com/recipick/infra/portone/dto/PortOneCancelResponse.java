package com.recipick.infra.portone.dto;

// {@code POST /payments/{paymentId}/cancel} 성공 응답 중 환불 기록에 필요한 필드만 담은 와이어 DTO
// 취소 시각은 PortOne 스키마 상 선택값이라 SUCCEEDED여도 비어 있을 수 있다.
// PortOne JSON 키는 cancelledAt이고, 필드명은 팀 철자 (CANCELED) ㅡ PortOnePaymentStatus와 같은 방식

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneCancelResponse(
    Cancellation cancellation
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(
        PortOneCancellationStatus status,
        String id,
        @JsonProperty("cancelledAt")OffsetDateTime canceledAt
        ) {

    }
}
