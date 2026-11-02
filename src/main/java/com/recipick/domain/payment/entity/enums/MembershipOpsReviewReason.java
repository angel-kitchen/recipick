package com.recipick.domain.payment.entity.enums;

import java.util.Set;

// 시도에 남기는 운영 확인 표시의 사유. 운영 화면·로그용. 값은 이용권 생명주기 설계안의 이름을 그대로 쓴다.
// 세 종류로 나뉜다 — 단순 안내(정상 처리 유지), 환불 필요(미반영 수납, SYSTEM 환불 COMPLETED까지 C-17이 막는다),
// 강한 정합성 차단(BLOCKING: 자동 청구·자동 반영·정산 해제를 모두 멈추고 운영이 PortOne 콘솔과 대조한 뒤에만 조치한다).
// 차단 판정은 이 집합 하나로만 한다 — 운영 표시 시각이 있다는 것만으로 자동 처리를 멈추지 않는다
public enum MembershipOpsReviewReason {
    LATE_PAID_AFTER_DEADLINE,
    PAID_CONFLICT,
    PAID_ON_CLOSED_PAYMENT,
    FIRST_PAYMENT_PERIOD_ENDED,
    FIRST_PAYMENT_LONG_DELAY,
    RENEWAL_PERIOD_ENDED,
    RENEWAL_LONG_DELAY,
    SYSTEM_REFUND_FAILED,
    PAID_AMOUNT_MISMATCH,
    LATE_PAID_AFTER_FAILED,                 // 환불 필요 — 발송된 FAILED 시도에 늦게 확인된 수납(상태를 PAID로 정정하고 돌려준다)
    PAID_AFTER_WITHDRAWAL,                  // 환불 필요 — 탈퇴한 회원의 갱신 수납(혜택을 만들지 않는다)
    CONFLICTING_MEMBERSHIP,                 // 환불 필요 — 다른 이용권과 충돌하는 갱신 수납
    UNKNOWN_WINDOW_EXPIRED,                 // 결과 확인 대기 — 결과 불명 창(W)이 끝나 자동 반복 조회를 멈춤. 차단은 아니다
    APPLIED_STATE_INCONSISTENT,              // 강한 차단 — 이용권 반영 상태와 결제 기록이 맞지 않음
    UNDISPATCHED_PAID,                       // 강한 차단 — PG에 보낸 적 없는 시도의 결제 ID로 PG에 수납이 있음
    UNDISPATCHED_PG_ACTIVITY,                // 강한 차단 — PG에 보낸 적 없는 시도의 결제 ID로 PG에 수납 아닌 거래 기록(실패·진행 중·취소·신뢰 불가)이 있음
    PAID_EVIDENCE_CONFLICT,                  // 강한 차단 — 저장된 수납 근거(시각·금액)와 PG 재조회 결과가 다름
    PAID_AT_INVALID,                          // 강한 차단 — 실패로 확정한 시도에 늦게 확인된 수납의 결제 시각이 회차 기간 전이거나 미래
    REFUND_EVIDENCE_CONFLICT,                 // 강한 차단 — 이 시도의 환불 근거(취소 ID·금액·reason·귀속)와 PG 취소 내역이 모순
    REFUND_TARGET_PERIOD_MISMATCH;            // 강한 차단 — 환불 대상으로 고른 회차·시도가 이용권의 현재 기간과 맞지 않음

    public static final Set<MembershipOpsReviewReason> BLOCKING = Set.of(
        APPLIED_STATE_INCONSISTENT, UNDISPATCHED_PAID, UNDISPATCHED_PG_ACTIVITY, PAID_EVIDENCE_CONFLICT,
        PAID_AT_INVALID, REFUND_EVIDENCE_CONFLICT, REFUND_TARGET_PERIOD_MISMATCH);

    public boolean isBlocking() {
        return BLOCKING.contains(this);
    }
}
