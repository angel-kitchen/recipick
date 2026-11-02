package com.recipick.infra.portone.dto;

import com.recipick.global.common.TimeConstants;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Set;

// 멤버십 금액 지정 취소의 결과. 응답을 예외로 바꾸지 않고 분류해 돌려준다(outcome 분류는 PortOneCancelOutcome 재사용).
// 성공 응답은 취소 ID·상태·금액·사유·시각을 보존하고, 오류 응답은 HTTP 상태와 오류 type을 보존한다 — 400(요청 거절)과 401·403(인증)을
// 호출자가 구분해야 하고, 409 IDEMPOTENCY_OUTSTANDING_REQUEST(같은 멱등 요청 처리 중)는 잔액 불일치가 아니라 결과 불명이다.
// 상태만 있고 취소 ID가 없는 응답은 어느 취소인지 확정할 수 없어 결과 불명으로 본다
public record PortOneMembershipCancelResult(
    PortOneCancelOutcome outcome,
    Integer httpStatus,
    String errorType,
    String cancellationId,
    PortOneCancellationStatus cancellationStatus,
    Long totalAmount,
    String reason,
    LocalDateTime requestedAt,
    LocalDateTime canceledAt,
    String detail
) {
    public static final String TYPE_IDEMPOTENCY_OUTSTANDING = "IDEMPOTENCY_OUTSTANDING_REQUEST";

    // PAID로 알고 있는 결제를 PortOne이 다르게 보고 있거나 취소 가능 잔액이 어긋난다는 뜻의 409 — 새 키를 만들지 않고 GET으로 확인한다
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

    public static PortOneMembershipCancelResult from(int httpStatus, PortOneMembershipCancelResponse response) {
        if (response == null
            || response.cancellation() == null
            || response.cancellation().status() == null
            || response.cancellation().id() == null
            || response.cancellation().id().isBlank()) {
            return unconfirmed("필수 취소 정보가 없는 응답");
        }
        PortOneMembershipCancelResponse.Cancellation c = response.cancellation();
        PortOneCancelOutcome outcome = switch (c.status()) {
            case SUCCEEDED -> PortOneCancelOutcome.SUCCEEDED;
            case REQUESTED -> PortOneCancelOutcome.REQUESTED;
            case FAILED -> PortOneCancelOutcome.FAILED;
            case UNKNOWN -> PortOneCancelOutcome.UNCONFIRMED;
        };
        return new PortOneMembershipCancelResult(outcome, httpStatus, null, c.id(), c.status(), c.totalAmount(),
            c.reason(), toKst(c.requestedAt()), toKst(c.canceledAt()), "cancellation " + c.status());
    }

    public static PortOneMembershipCancelResult fromError(int httpStatus, String errorType) {
        return new PortOneMembershipCancelResult(classify(httpStatus, errorType), httpStatus, errorType, null, null,
            null, null, null, null, "HTTP " + httpStatus + " " + errorType);
    }

    public static PortOneMembershipCancelResult unconfirmed(String detail) {
        return new PortOneMembershipCancelResult(PortOneCancelOutcome.UNCONFIRMED, null, null, null, null, null, null,
            null, null, detail);
    }

    // 401·403 — 인증 설정 문제. 자동 POST를 멈추고 운영이 본다
    public boolean isAuthError() {
        return httpStatus != null && (httpStatus == 401 || httpStatus == 403);
    }

    // 400 — 우리 요청 본문 문제. 자동 POST를 멈추고 운영이 본다
    public boolean isRequestRejected() {
        return httpStatus != null && httpStatus == 400;
    }

    public boolean hasCancellationId() {
        return cancellationId != null && !cancellationId.isBlank();
    }

    // 목록에 있는 것만 REJECTED·MISMATCH로 본다. 처리 중인 동일 멱등 요청(409 IDEMPOTENCY_OUTSTANDING_REQUEST)과 나머지는 전부 "모름"
    private static PortOneCancelOutcome classify(int httpStatus, String errorType) {
        if (httpStatus == 400 || httpStatus == 401 || httpStatus == 403) {
            return PortOneCancelOutcome.REJECTED;
        }
        if (httpStatus == 404) {
            return PortOneCancelOutcome.MISMATCH;
        }
        if (httpStatus == 409 && errorType != null) {
            if (TYPE_IDEMPOTENCY_OUTSTANDING.equals(errorType)) {
                return PortOneCancelOutcome.UNCONFIRMED;
            }
            if (MISMATCH_TYPES.contains(errorType)) {
                return PortOneCancelOutcome.MISMATCH;
            }
        }
        return PortOneCancelOutcome.UNCONFIRMED;
    }

    private static LocalDateTime toKst(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(TimeConstants.ZONE_ID).toLocalDateTime();
    }
}
