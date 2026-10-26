package com.recipick.infra.portone.dto;

import com.recipick.global.common.TimeConstants;
import com.recipick.infra.portone.PortOneCustomDataParser;
import java.time.LocalDateTime;
import java.util.List;

// 도메인이 쓰는 PortOne 결제 조회 결과. 시각은 한국 시간 LocalDateTime으로, customData는 주문 ID로 바꿔 두어 domain Service가 Jackson 없이 값만 비교한다
// @param paidAt            PortOne이 확인한 결제 완료 시각(KST). 미결제면 null
// @param customDataOrderId customData에서 읽은 주문 ID. 없거나 해석 불가면 null (잠정 구현)
// @param failureSummary    PG 실패 사유 한 줄 요약. 실패 정보가 없으면 null
// @param cancellations     취소 내역. 없으면 빈 목록 (null 아님)
public record PortOnePayment(
        String id,
        String transactionId,
        PortOnePaymentStatus status,
        Long totalAmount,
        String currency,
        LocalDateTime paidAt,
        Long customDataOrderId,
        String failureSummary,
        List<PortOneCancellation> cancellations
) {

    public PortOnePayment {
        cancellations = cancellations == null ? List.of() : List.copyOf(cancellations);
    }

    // 취소 내역이 필요 없는 결제 확정 경로용. 기존 승인 테스트들이 이 생성자로 만든다
    public PortOnePayment(
        String id,
        String transactionId,
        PortOnePaymentStatus status,
        Long totalAmount,
        String currency,
        LocalDateTime paidAt,
        Long customDataOrderId,
        String failureSummary
    ) {
        this(id, transactionId, status, totalAmount, currency, paidAt, customDataOrderId, failureSummary, List.of());
    }

    public static PortOnePayment from(PortOnePaymentResponse response) {
        return new PortOnePayment(
            response.id(),
            response.transactionId(),
            response.status(),
            response.amount() == null ? null : response.amount().total(),
            response.currency(),
            response.paidAt() == null
                ? null
                : response.paidAt().atZoneSameInstant(TimeConstants.ZONE_ID).toLocalDateTime(),
            PortOneCustomDataParser.orderIdOf(response.customData()),
            response.failure() == null ? null : response.failure().summary(),
            response.cancellations() == null
                ? List.of()
                : response.cancellations().stream().map(PortOneCancellation::from).toList()
        );
    }
}
