package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;

// 취소 매칭·식별 검증에 필요한 환불 행의 값(순수 함수 입력). expectedStoreId는 행에 고정된 상점 ID, 아직 없으면 호출자가 넘긴
// 서버 설정값이다 — PG 응답의 상점을 기대값으로 쓰지 않는다
public record RefundMatchSnapshot(
    Long refundId,
    String portonePaymentId,
    String expectedStoreId,
    Long paidAmountSnapshot,
    long refundAmount,
    String pgRequestReason,
    String portoneCancellationId,
    MembershipRefundStatus status
) {

    public static RefundMatchSnapshot of(MembershipRefund refund, String portonePaymentId, String configuredStoreId) {
        return new RefundMatchSnapshot(refund.getId(), portonePaymentId,
            refund.getPgStoreId() != null ? refund.getPgStoreId() : configuredStoreId,
            refund.getPaidAmountSnapshot(), refund.getAmount(), refund.getPgRequestReason(),
            refund.getPortoneCancellationId(), refund.getStatus());
    }
}
