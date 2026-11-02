package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 운영 상세. 목록 한 줄 + 회차 기간·재시도 시각·회차 실패 사유·반영 시각 + 그 시도의 환불 이력.
// systemRefunds는 기존 형태(SYSTEM만, 5필드)를 그대로 유지하고, refunds는 MEMBER·SYSTEM 전체와 확장 필드(취소 ID·발송·예약·운영 표시·
// 후속 연결·기간 일수)를 준다 — 둘은 같은 조회 결과로 조립한다. 환불 행이 하나도 없는 시도에 PG 취소가 관측되면 외부 취소다
public record MembershipPaymentAttemptDetailResponse(
    MembershipPaymentAttemptSummaryResponse attempt,
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    LocalDateTime nextRetryAt,
    String paymentFailureReason,
    LocalDateTime membershipAppliedAt,
    List<SystemRefund> systemRefunds,
    List<Refund> refunds
) {

    public record SystemRefund(Long refundId, Long amount, MembershipRefundStatus status, LocalDateTime requestedAt,
                               LocalDateTime completedAt) {

        public static SystemRefund of(MembershipRefund refund) {
            return new SystemRefund(refund.getId(), refund.getAmount(), refund.getStatus(), refund.getRequestedAt(),
                refund.getCompletedAt());
        }
    }

    public record Refund(Long refundId, RefundRequester requestedBy, Long amount, MembershipRefundStatus status,
                         String reason, LocalDateTime requestedAt, LocalDateTime completedAt, String portoneCancellationId,
                         LocalDateTime firstDispatchedAt, LocalDateTime nextCheckAt, boolean reviewRequired,
                         MembershipRefundOpsReason opsReviewReason, LocalDateTime opsReviewRequiredAt,
                         Long retryOfRefundId, Integer periodDays, Integer usedDays, Long paidAmountSnapshot) {

        public static Refund of(MembershipRefund refund) {
            return new Refund(refund.getId(), refund.getRequestedBy(), refund.getAmount(), refund.getStatus(),
                refund.getReason(), refund.getRequestedAt(), refund.getCompletedAt(), refund.getPortoneCancellationId(),
                refund.getFirstDispatchedAt(), refund.getNextCheckAt(), refund.isReviewRequired(),
                refund.getOpsReviewReason(), refund.getOpsReviewRequiredAt(), refund.getRetryOfRefundId(),
                refund.getPeriodDays(), refund.getRequestedBy() == RefundRequester.MEMBER ? refund.getUsedDays() : null,
                refund.getPaidAmountSnapshot());
        }
    }

    // refunds = 그 시도의 환불 행 전부(ID 순). systemRefunds는 여기서 SYSTEM만 걸러 기존 형태로 만든다
    public static MembershipPaymentAttemptDetailResponse of(MembershipPaymentAttemptSummaryResponse summary,
                                                            MembershipPayment payment, List<MembershipRefund> refunds) {
        return new MembershipPaymentAttemptDetailResponse(summary, payment.getPeriodStartDate(),
            payment.getPeriodEndDate(), payment.getNextRetryAt(), payment.getFailureReason(),
            payment.getMembershipAppliedAt(),
            refunds.stream().filter(r -> r.getRequestedBy() == RefundRequester.SYSTEM).map(SystemRefund::of).toList(),
            refunds.stream().map(Refund::of).toList());
    }
}
