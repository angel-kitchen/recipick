package com.recipick.domain.payment.service;

// 결제 결과 반영 TX(TX2)의 결정. 일반 결제 처리면 outcome이 있고, 수납 PAID 시도에 취소 계열 관측이 와서 환불 정합화로 넘길 때는
// outcome 없이 refundReconciliationRequired만 참이다 — 이 경우 TX2는 결제 상태를 바꾸지 않고, 실행기가 커밋 뒤 같은 PG 응답을
// 환불 정합화에 전달한다(추가 GET·취소 POST·수납 PAID 강제 생성 없음)
public record MembershipChargeApplyDecision(MembershipChargeOutcome outcome, boolean refundReconciliationRequired) {

    public MembershipChargeApplyDecision {
        if ((outcome == null) != refundReconciliationRequired) {
            throw new IllegalArgumentException("결정은 결제 결과 또는 환불 위임 중 하나다");
        }
    }

    public static MembershipChargeApplyDecision of(MembershipChargeOutcome outcome) {
        return new MembershipChargeApplyDecision(outcome, false);
    }

    public static MembershipChargeApplyDecision delegateToRefund() {
        return new MembershipChargeApplyDecision(null, true);
    }
}
