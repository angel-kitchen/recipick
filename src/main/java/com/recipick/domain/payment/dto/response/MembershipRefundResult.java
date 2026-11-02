package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import java.time.LocalDateTime;

// 회원 즉시 해지 환불 응답. 회원 도메인의 terminate Controller가 그대로 data에 담는다. HTTP 200이고 업무 상태는 refundStatus로 구분한다.
// membershipTerminated: MEMBER 환불 완료와 이용권 종료는 같은 트랜잭션이라 COMPLETED면 종료된 것이다(0원 완료 포함).
// reviewRequired: 강한 차단이거나 REQUESTED에 자동 중단 사유가 있을 때만 true — 정상 확정된 행의 과거 표시는 false.
// SYSTEM 행은 기간 값이 없으므로 이 응답으로 만들지 않는다
public record MembershipRefundResult(
    Long membershipId,
    Long membershipPaymentId,
    Long refundId,
    MembershipRefundStatus refundStatus,
    long paidAmount,
    int periodDays,
    int usedDays,
    long usedDayFee,
    long penaltyAmount,
    long refundAmount,
    LocalDateTime requestedAt,
    LocalDateTime completedAt,
    boolean membershipTerminated,
    boolean reviewRequired
) {

    // 영속성 컨텍스트 안(짧은 조회 트랜잭션)에서만 부른다 — 회차 연관을 읽는다
    public static MembershipRefundResult of(MembershipRefund refund) {
        if (refund.getRequestedBy() != RefundRequester.MEMBER || refund.getPeriodDays() == null
            || refund.getPaidAmountSnapshot() == null || refund.getUsedDayFee() == null
            || refund.getPenaltyAmount() == null || refund.getAmount() == null) {
            throw new IllegalStateException("MEMBER 환불 응답에 필요한 값이 없음: refundId=" + refund.getId());
        }
        return new MembershipRefundResult(
            refund.getMembershipPayment().getMembershipId(),
            refund.getMembershipPayment().getId(),
            refund.getId(),
            refund.getStatus(),
            refund.getPaidAmountSnapshot(),
            refund.getPeriodDays(),
            refund.getUsedDays(),
            refund.getUsedDayFee(),
            refund.getPenaltyAmount(),
            refund.getAmount(),
            refund.getRequestedAt(),
            refund.getCompletedAt(),
            refund.getStatus() == MembershipRefundStatus.COMPLETED,
            refund.isReviewRequired()
        );
    }
}
