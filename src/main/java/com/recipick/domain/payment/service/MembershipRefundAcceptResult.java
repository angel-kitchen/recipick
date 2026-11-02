package com.recipick.domain.payment.service;

// 환불 접수 TX의 결과. Entity를 TX 밖으로 내보내지 않고 ID와 분류만 돌려준다.
// 정상 접수·기존 결과: refundId 있음, rejectReason 없음 / 거절: refundId 없음, rejectReason 있음 / completedWithoutPg: 신규 0원 완료
public record MembershipRefundAcceptResult(
    Long refundId,
    boolean existing,
    boolean completedWithoutPg,
    RejectReason rejectReason
) {

    public enum RejectReason {
        NOT_TERMINABLE,        // 즉시 해지 자격 없음 → MEMBERSHIP-008
        PAYMENT_IN_PROGRESS,   // 미정산 결제·진행 중 환불 → MEMBERSHIP-010
        SETTLEMENT_BLOCKED,    // 환불 대상 회차·시도를 확정할 수 없어 차단 표시를 남김 → BILLING-022
        RETRY_NOT_ALLOWED      // SYSTEM 재실행 조건 불충족 → BILLING-021
    }

    public MembershipRefundAcceptResult {
        if ((refundId == null) == (rejectReason == null)) {
            throw new IllegalArgumentException("접수 결과는 환불 ID와 거절 사유 중 하나만 가진다");
        }
        if (rejectReason != null && (existing || completedWithoutPg)) {
            throw new IllegalArgumentException("거절 결과에는 기존·완료 표시가 없다");
        }
        if (existing && completedWithoutPg) {
            throw new IllegalArgumentException("기존 결과와 신규 0원 완료는 함께 표시하지 않는다");
        }
    }

    public static MembershipRefundAcceptResult accepted(Long refundId) {
        return new MembershipRefundAcceptResult(refundId, false, false, null);
    }

    public static MembershipRefundAcceptResult existing(Long refundId) {
        return new MembershipRefundAcceptResult(refundId, true, false, null);
    }

    public static MembershipRefundAcceptResult completedWithoutPg(Long refundId) {
        return new MembershipRefundAcceptResult(refundId, false, true, null);
    }

    public static MembershipRefundAcceptResult rejected(RejectReason reason) {
        return new MembershipRefundAcceptResult(null, false, false, reason);
    }

    public boolean isRejected() {
        return rejectReason != null;
    }

    // 실행기가 PG 취소를 시도해야 하는 접수(신규 양수 REQUESTED)
    public boolean requiresExecution() {
        return rejectReason == null && !existing && !completedWithoutPg;
    }
}
