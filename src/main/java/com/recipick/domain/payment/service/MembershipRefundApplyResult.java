package com.recipick.domain.payment.service;

// TX2(결과 반영) 한 번의 결과. outcome은 대상 환불의 반영 결과, postAllowed는 이번 관측 기준으로 대상 환불에 새 POST(또는 같은 키 재POST)를
// 보내도 되는지(REQUESTED·식별 일치·누적 취소액 일치·매칭 없음·취소 ID 없음·설명되지 않는 취소 없음·PG 키 있음), signal은 토큰 보유자가
// 운영 표시로 바꿔야 하는 관측(불신뢰 취소 목록·발송 이력 불명). TX2 자체는 토큰 없이 잠금·상태·증거로 보호된다
public record MembershipRefundApplyResult(
    MembershipRefundExecutionResult outcome,
    boolean postAllowed,
    Signal signal
) {

    public enum Signal {
        NONE,
        UNTRUSTED_OBSERVATION,   // 취소 내역의 상태·금액·ID를 해석할 수 없음 → CANCELLATION_STATUS_UNKNOWN
        LEGACY_UNPROVEN          // PG 키 없는 기존 행 + 취소 ID 없음 → LEGACY_DISPATCH_UNPROVEN(자동 POST 금지)
    }

    public static MembershipRefundApplyResult of(MembershipRefundExecutionResult outcome) {
        return new MembershipRefundApplyResult(outcome, false, Signal.NONE);
    }

    public static MembershipRefundApplyResult pendingPostAllowed() {
        return new MembershipRefundApplyResult(MembershipRefundExecutionResult.PENDING, true, Signal.NONE);
    }

    public static MembershipRefundApplyResult signal(Signal signal) {
        return new MembershipRefundApplyResult(MembershipRefundExecutionResult.PENDING, false, signal);
    }
}
