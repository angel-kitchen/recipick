package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;


// GET /payments/{paymentId}} 응답 중 결제 확정·취소 정합화에 필요한 필드만 담은 와이어 DTO.
// storeId·amount.cancelled·cancellations[].reason은 멤버십 환불 정합화가 식별·누적 취소액·취소 귀속을 검증하는 데 쓴다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
    String id,
    String transactionId,
    String storeId,
    PortOnePaymentStatus status,
    PortOnePaymentAmount amount,
    String currency,
    OffsetDateTime paidAt,
    String customData,
    PortOnePaymentFailure failure,
    List<Cancellation> cancellations
) {

    // 상점 ID가 필요 없는 결제 확정 경로용(기존 생성자 호환)
    public PortOnePaymentResponse(
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
        this(id, transactionId, null, status, amount, currency, paidAt, customData, failure, cancellations);
    }

    // 취소 내역. PortOne JSON 키는 cancelledAt이고 필드명은 CANCELED ㅡ PortOneCancelResponse와 같은 방식
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(
        String id,
        PortOneCancellationStatus status,
        Long totalAmount,
        String reason,
        OffsetDateTime requestedAt,
        @JsonProperty("cancelledAt") OffsetDateTime canceledAt,
        PortOneCancellationTrigger trigger
    ) {

        // 취소 사유가 필요 없는 상품 결제 정합화용(기존 생성자 호환)
        public Cancellation(
            String id,
            PortOneCancellationStatus status,
            Long totalAmount,
            OffsetDateTime requestedAt,
            OffsetDateTime canceledAt,
            PortOneCancellationTrigger trigger
        ) {
            this(id, status, totalAmount, null, requestedAt, canceledAt, trigger);
        }
    }

}
