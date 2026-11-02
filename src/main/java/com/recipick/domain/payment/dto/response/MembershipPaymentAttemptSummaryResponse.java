package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import java.time.Duration;
import java.time.LocalDateTime;

// 운영 목록 한 줄. 빌링키(원문·ID)·고객 정보·PG 응답 원문은 담지 않는다. unknownWindowEndAt = 발송 시각 + 결과 불명 창
public record MembershipPaymentAttemptSummaryResponse(
    Long attemptId,
    Long membershipPaymentId,
    Long membershipId,
    int attemptNo,
    String portonePaymentId,
    MembershipPaymentAttemptStatus status,
    MembershipPaymentStatus paymentStatus,
    LocalDateTime requestedAt,
    LocalDateTime dispatchedAt,
    LocalDateTime completedAt,
    LocalDateTime pgPaidAt,
    String failureReason,
    LocalDateTime opsReviewRequiredAt,
    MembershipOpsReviewReason opsReviewReason,
    boolean settlementBlocked,
    LocalDateTime refundRequiredAt,
    LocalDateTime graceDeadlineAt,
    LocalDateTime unknownWindowEndAt
) {

    public static MembershipPaymentAttemptSummaryResponse of(MembershipPaymentAttempt attempt, Duration unknownWindow) {
        MembershipPayment payment = attempt.getMembershipPayment();
        return new MembershipPaymentAttemptSummaryResponse(attempt.getId(), payment.getId(), payment.getMembershipId(),
            attempt.getAttemptNo(), attempt.getPortonePaymentId(), attempt.getStatus(), payment.getStatus(),
            attempt.getRequestedAt(), attempt.getDispatchedAt(), attempt.getCompletedAt(), attempt.getPgPaidAt(),
            attempt.getFailureReason(), attempt.getOpsReviewRequiredAt(), attempt.getOpsReviewReason(),
            attempt.isSettlementBlocked(), attempt.getRefundRequiredAt(), payment.getGraceDeadlineAt(),
            attempt.getDispatchedAt() == null ? null : attempt.getDispatchedAt().plus(unknownWindow));
    }
}
