package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;


 // GET /payments/{paymentId}} 응답 중 결제 확정·취소 정합화에 필요한 필드만 담은 와이어 DTO.
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
     String id,
     String transactionId,
     PortOnePaymentStatus status,
     PortOnePaymentAmount amount,
     String currency,
     OffsetDateTime paidAt,
     String customData,
     PortOnePaymentFailure failure,
     List<Cancellation> cancellations
) {
    // 취소 내역. PortOne JSON 키는 cancelledAt이고 필드명은 CANCELED ㅡ PortOneCancelResponse와 같은 방식
     @JsonIgnoreProperties(ignoreUnknown = true)
     public record Cancellation(
         String id,
         PortOneCancellationStatus status,
         Long totalAmount,
         OffsetDateTime requestedAt,
         @JsonProperty("cancelledAt") OffsetDateTime canceledAt,
         PortOneCancellationTrigger trigger
    ) {

    }

}
