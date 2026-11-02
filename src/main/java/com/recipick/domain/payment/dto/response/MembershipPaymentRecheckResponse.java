package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPgVerdict;
import java.time.LocalDateTime;

// 운영자 재조회 결과. 재조회 뒤의 시도·회차 상태와 이번 PG 조회의 정제된 관측값만 담는다 — PG 응답 원문·실패 문장·취소 내역·
// 빌링키·고객 정보는 없다. pgVerdictKind는 조회를 했을 때만, pgPaidAt·pgPaidAmount는 신뢰 가능한 수납일 때만 값이 있다.
// 차단 시도는 조회 결과를 담되 반영 없이 outcome = RECONCILE_HALTED다
public record MembershipPaymentRecheckResponse(
    Long attemptId,
    MembershipChargeOutcome outcome,
    MembershipPaymentAttemptStatus status,
    MembershipPaymentStatus paymentStatus,
    MembershipOpsReviewReason opsReviewReason,
    boolean settlementBlocked,
    MembershipOpsReviewReason blockedReason,
    MembershipPgVerdict.Kind pgVerdictKind,
    LocalDateTime pgPaidAt,
    Long pgPaidAmount
) {

    public static MembershipPaymentRecheckResponse of(MembershipPaymentAttemptSummaryResponse after,
                                                      MembershipChargeOutcome outcome, MembershipPgVerdict verdict) {
        boolean trustedPaid = verdict != null && verdict.isTrustedPaid();
        MembershipOpsReviewReason blockedReason = after.settlementBlocked() ? after.opsReviewReason() : null;
        return new MembershipPaymentRecheckResponse(after.attemptId(), outcome, after.status(), after.paymentStatus(),
            after.opsReviewReason(), after.settlementBlocked(), blockedReason, verdict == null ? null : verdict.kind(),
            trustedPaid ? verdict.pgPaidAt() : null, trustedPaid ? verdict.paidAmount() : null);
    }
}
