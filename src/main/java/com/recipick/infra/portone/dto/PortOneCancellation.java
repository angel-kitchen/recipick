package com.recipick.infra.portone.dto;

import com.recipick.global.common.TimeConstants;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

// 결제 단건 조회 응답의 취소 내역(도메인용). 시각은 한국 시간 LocalDateTime으로 바꿔 둔다
public record PortOneCancellation(
    String id,
    PortOneCancellationStatus status,
    Long totalAmount,
    LocalDateTime requestedAt,
    LocalDateTime canceledAt,
    PortOneCancellationTrigger trigger
) {
    public static PortOneCancellation from(PortOnePaymentResponse.Cancellation cancellation) {
        return new PortOneCancellation(
            cancellation.id(),
            cancellation.status() == null ? PortOneCancellationStatus.UNKNOWN : cancellation.status(),
            cancellation.totalAmount(),
            toKst(cancellation.requestedAt()),
            toKst(cancellation.canceledAt()),
            cancellation.trigger() == null ? PortOneCancellationTrigger.UNKNOWN : cancellation.trigger()
        );
    }

    // 조회로 본 취소 내역을 취소 API 응답과 같은 형태로 바꾼다. 정합화가 PaymentCancelProcessor.apply를 그대로 쓰기 위해서다.
    // 취소 ID가 없으면 어느 환불인지 확정할 수 없으므로 상태가 있어도 결과 불명으로 본다 — {@link PortOneCancelResult#from}과 같은 기준
    public PortOneCancelResult toCancelResult() {
        if (id == null || id.isBlank()) {
            return PortOneCancelResult.unconfirmed("조회한 취소 내역에 ID가 없음");
        }
        PortOneCancelOutcome outcome = switch (status) {
            case SUCCEEDED -> PortOneCancelOutcome.SUCCEEDED;
            case REQUESTED -> PortOneCancelOutcome.REQUESTED;
            case FAILED -> PortOneCancelOutcome.FAILED;
            case UNKNOWN -> PortOneCancelOutcome.UNCONFIRMED;
        };
        return new PortOneCancelResult(outcome, id, canceledAt, "조회한 cancellation " + status);
    }

    private static LocalDateTime toKst(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(TimeConstants.ZONE_ID).toLocalDateTime();
    }
}
