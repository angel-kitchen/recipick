package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 운영 상세. 목록 한 줄 + 회차 기간·재시도 시각·회차 실패 사유·반영 시각 + 그 시도의 SYSTEM 환불 행
public record MembershipPaymentAttemptDetailResponse(
    MembershipPaymentAttemptSummaryResponse attempt,
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    LocalDateTime nextRetryAt,
    String paymentFailureReason,
    LocalDateTime membershipAppliedAt,
    List<SystemRefund> systemRefunds
) {

    public record SystemRefund(Long refundId, Long amount, MembershipRefundStatus status, LocalDateTime requestedAt,
                               LocalDateTime completedAt) {

        public static SystemRefund of(MembershipRefund refund) {
            return new SystemRefund(refund.getId(), refund.getAmount(), refund.getStatus(), refund.getRequestedAt(),
                refund.getCompletedAt());
        }
    }

    public static MembershipPaymentAttemptDetailResponse of(MembershipPaymentAttemptSummaryResponse summary,
                                                            MembershipPayment payment, List<MembershipRefund> refunds) {
        return new MembershipPaymentAttemptDetailResponse(summary, payment.getPeriodStartDate(),
            payment.getPeriodEndDate(), payment.getNextRetryAt(), payment.getFailureReason(),
            payment.getMembershipAppliedAt(), refunds.stream().map(SystemRefund::of).toList());
    }
}
