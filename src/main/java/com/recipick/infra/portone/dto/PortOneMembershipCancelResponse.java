package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// 금액 지정 취소 성공 응답(POST /payments/{paymentId}/cancel) 중 보존할 필드. 취소 금액·사유·시각까지 담아
// 응답 ID를 기록하기 전에 저장 환불액·요청 사유와 대조할 수 있게 한다. JSON 키는 cancelledAt, 필드명은 팀 철자(CANCELED)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneMembershipCancelResponse(
    Cancellation cancellation
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(
        PortOneCancellationStatus status,
        String id,
        Long totalAmount,
        String reason,
        OffsetDateTime requestedAt,
        @JsonProperty("cancelledAt") OffsetDateTime canceledAt
    ) {
    }
}
