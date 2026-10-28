package com.recipick.domain.payment.dto;

import com.recipick.domain.payment.service.RefundReconcileOutcome;
import java.util.Objects;
import java.util.Optional;

// 마트 거절 환불 실행 한 번의 결과. outcome은 Worker 로그·테스트가 쓰고, applied는 거절 API가 응답에 쓴다.
// applied는 이번 실행에서 TX2(MartRejectionProcessor.apply)가 실제로 돌았을 때만 있다
public record AdminRefundExecution(
    RefundReconcileOutcome outcome,
    MartRejectionPaymentResult applied
) {
    // RESOLVED는 TX2가 확정했다는 뜻이라 결과가 있어야 하고, SKIPPED는 TX2까지 가지 않았다는 뜻이라 결과가 없어야 한다.
    // MANUAL_REVIEW도 결과를 가질 수 없다 — PG는 취소에 성공했는데 TX2가 실패를 돌려준 경합에서 그 실패가 거절 API 응답(409)으로 나가면 안 된다
    public AdminRefundExecution {
        Objects.requireNonNull(outcome, "outcome은 필수입니다.");
        if (outcome == RefundReconcileOutcome.RESOLVED && applied == null) {
            throw new IllegalArgumentException("RESOLVED는 반영 결과가 필요합니다.");
        }
        if (outcome == RefundReconcileOutcome.SKIPPED && applied != null) {
            throw new IllegalArgumentException("SKIPPED는 반영 결과를 가질 수 없습니다.");
        }
        if (outcome == RefundReconcileOutcome.MANUAL_REVIEW && applied != null) {
            throw new IllegalArgumentException("MANUAL_REVIEW는 반영 결과를 가질 수 없습니다.");
        }
    }

    public static AdminRefundExecution notApplied(RefundReconcileOutcome outcome) {
        return new AdminRefundExecution(outcome, null);
    }

    public static AdminRefundExecution applied(RefundReconcileOutcome outcome, MartRejectionPaymentResult applied) {
        Objects.requireNonNull(applied, "applied는 필수입니다.");
        return new AdminRefundExecution(outcome, applied);
    }

    public Optional<MartRejectionPaymentResult> appliedResult() {
        return Optional.ofNullable(applied);
    }
}
