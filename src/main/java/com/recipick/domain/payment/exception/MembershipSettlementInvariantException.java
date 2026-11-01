package com.recipick.domain.payment.exception;

// 결제 결과와 이용권 상태가 서로 맞지 않는다고 TX2가 명시적으로 판정한 정합성 오류.
// 이 예외로 TX2 전체가 롤백되고, 복구는 이 예외만 잡아 별도 TX에서 원인을 재검증한 뒤 운영 표시를 남긴다.
// 다른 IllegalStateException(연결 불일치·잘못된 전이 등)은 자동 정합화 중단 대상이 아니다.
// 메시지에는 ID와 종류만 넣는다 — PG 원문·고객 정보·빌링키는 넣지 않는다
public class MembershipSettlementInvariantException extends RuntimeException {

    public enum Kind {
        // 첫 결제 회차가 미반영인데 이용권은 이미 활성이고 근거가 된 반영 회차가 없다(I-22·J-1 위반 데이터).
        // C-3a ALREADY_APPLIED인데 회차가 미반영인 경우도 같은 종류다
        ACTIVATED_WITHOUT_APPLIED_PAYMENT,
        // C-3a CONFLICT인데 종료·상태·소유 중 어느 사유도 확인되지 않음
        UNEXPLAINED_FIRST_PAYMENT_CONFLICT
    }

    private final Kind kind;
    private final Long membershipPaymentId;
    private final Long attemptId;

    public MembershipSettlementInvariantException(Kind kind, Long membershipPaymentId, Long attemptId) {
        super("멤버십 결제 정합성 오류: kind=" + kind + ", membershipPaymentId=" + membershipPaymentId
            + ", attemptId=" + attemptId);
        this.kind = kind;
        this.membershipPaymentId = membershipPaymentId;
        this.attemptId = attemptId;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getMembershipPaymentId() {
        return membershipPaymentId;
    }

    public Long getAttemptId() {
        return attemptId;
    }
}
