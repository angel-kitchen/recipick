package com.recipick.infra.portone.dto;

import com.recipick.global.common.TimeConstants;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Set;

// PortOne 취소 결과를 도메인에서 쓰기 쉽게 변환한 DTO.
// 취소 시각은 KST로 바꾸고, cancellationId·canceledAt이 없으면 null로 둔다.
// detail은 로그와 환불 실패 사유 기록에 사용한다.
public record PortOneCancelResult(
    PortOneCancelOutcome outcome,
    String cancellationId,
    LocalDateTime canceledAt,
    String detail
) {
    // PAID로 알고 있는 결제를 PortOne이 다르게 보고 있따는 뜻의 409
    private static final Set<String> MISMATCH_TYPES = Set.of(

        "PAYMENT_ALREADY_CANCELLED",
        "PAYMENT_NOT_PAID",
        "CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN",
        "CANCEL_AMOUNT_EXCEEDS_CANCELLABLE_AMOUNT",
        "SUM_OF_PARTS_EXCEEDS_CANCEL_AMOUNT",
        "CANCEL_TAX_FREE_AMOUNT_EXCEEDS_CANCELLABLE_TAX_FREE_AMOUNT",
        "CANCEL_TAX_AMOUNT_EXCEEDS_CANCELLABLE_TAX_AMOUNT",
        "MAX_CANCEL_COUNT_REACHED"
        );

    // 상태만 있고 취소 내역 ID가 없으면 믿지 않는다. FAILED를 믿고 PAID로 되돌렸는데 나중에 웹훅이 와도 어느 환불인지 찾을 수 없다
    public static PortOneCancelResult from(PortOneCancelResponse response) {
        if (response == null
            || response.cancellation() == null
            || response.cancellation().status() == null
            || response.cancellation().id() == null
            || response.cancellation().id().isBlank()) {
            return unconfirmed("필수 취소 정보가 없는 응답");
        }
        PortOneCancelResponse.Cancellation cancellation = response.cancellation();
        PortOneCancelOutcome outcome = switch (cancellation.status()) {
            case SUCCEEDED -> PortOneCancelOutcome.SUCCEEDED;
            case REQUESTED -> PortOneCancelOutcome.REQUESTED;
            case FAILED -> PortOneCancelOutcome.FAILED;
            case UNKNOWN -> PortOneCancelOutcome.UNCONFIRMED;
        };
        return new PortOneCancelResult(outcome, cancellation.id(), toKst(cancellation.canceledAt()),
            "cancellation " + cancellation.status());
    }

    public static PortOneCancelResult fromError(int httpStatus, String errorType) {
        return new PortOneCancelResult(classify(httpStatus, errorType), null, null,
            "HTTP " + httpStatus + " " + errorType);
    }

    public static PortOneCancelResult unconfirmed(String detail) {
        return new PortOneCancelResult(PortOneCancelOutcome.UNCONFIRMED, null, null, detail);
    }

    // 목록에 있는 것만 REJECTED·MISMATCH로 본다. 나머지는 전부 "모름"이다
    private static PortOneCancelOutcome classify(int httpStatus, String errorType) {
        if (httpStatus == 400 || httpStatus == 401 || httpStatus == 403) {
            return PortOneCancelOutcome.REJECTED;
        }
        // Set.of는 contains(null)에서 NPE를 던지므로 오류 본문을 못 읽은 경우를 먼저 거른다
        if (httpStatus == 404 || (httpStatus == 409 && errorType != null && MISMATCH_TYPES.contains(errorType))) {
            return PortOneCancelOutcome.MISMATCH;
        }
        return PortOneCancelOutcome.UNCONFIRMED;
    }

    private static LocalDateTime toKst(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(TimeConstants.ZONE_ID).toLocalDateTime();
    }
}
