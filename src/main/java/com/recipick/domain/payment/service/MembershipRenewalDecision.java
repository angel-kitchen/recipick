package com.recipick.domain.payment.service;

// 갱신 회차를 잠금 아래에서 판정한 결과. CHARGE_READY일 때만 attemptId가 있고, 호출자는 트랜잭션이 커밋된 뒤에만
// 공용 실행기에 그 시도를 넘긴다. 그 밖의 결과는 PG를 부르지 않는다
public record MembershipRenewalDecision(Kind kind, Long membershipPaymentId, Long attemptId) {

    public enum Kind {
        CHARGE_READY,            // 실행할 READY 시도(새로 만들었거나 접수 때 유효했던 기존 시도)
        PG_PENDING,              // PG에 갔을 수 있는 시도(DISPATCHING·UNKNOWN·MANUAL_REVIEW)가 남아 있다. 재조회가 맡는다
        IN_PROGRESS,             // 정리만 한 경우(정리 작업)나 유효 READY가 있어도 지금 실행하지 않는 경우
        WAITING_RETRY,           // 재시도 시각 전이거나 예약 없음(G에서 정리)
        WAITING_PAYMENT_METHOD,  // ACTIVE 카드 없음
        BLOCKED,                 // 플래그 OFF·미정산·동의 불일치로 이번에는 진행하지 않음. 회차는 그대로 두고 다시 본다
        CLOSED,                  // 이번 트랜잭션에서 회차 FAILED + 적용 가능한 C-4b
        ALREADY_RESOLVED,        // 회차가 이미 PAID·FAILED
        HALTED,                  // 데이터가 규칙과 맞지 않아 자동 처리를 멈춤(운영 확인, 변경 없음)
        SKIPPED                  // 갱신 대상이 아님(해지 예약·자동갱신 꺼짐·마감 밖 등). 행을 만들지 않음
    }

    public static MembershipRenewalDecision of(Kind kind, Long membershipPaymentId) {
        return new MembershipRenewalDecision(kind, membershipPaymentId, null);
    }

    public static MembershipRenewalDecision chargeReady(Long membershipPaymentId, Long attemptId) {
        return new MembershipRenewalDecision(Kind.CHARGE_READY, membershipPaymentId, attemptId);
    }
}
