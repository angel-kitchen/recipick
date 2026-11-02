package com.recipick.domain.payment.entity.enums;

import java.util.Set;

// 멤버십 환불 행에 남기는 운영 확인 코드. 두 종류다 —
// 자동 중단(상태는 REQUESTED 유지, 자동 복구·재POST만 멈추고 관리자 재조회·검증된 웹훅으로 정확한 결과가 확인되면 정상 확정으로 이어 간다)과
// 강한 정합성 차단(실제 모순이 확인된 것. 같은 코드의 차단을 결제 시도에도 함께 남겨 회차 전체의 자동 처리를 멈춘다).
// 운영 사유는 이력으로 보존하고, 응답의 reviewRequired는 현재 상태와 함께 계산한다 — 과거 표시가 있다는 것만으로 영구 true가 되지 않는다
public enum MembershipRefundOpsReason {
    // 결과 확인 창(24시간)이 끝날 때까지 취소 결과를 확정하지 못함
    UNKNOWN_WINDOW_EXPIRED,
    // 서버의 PortOne 상점 설정이 비어 있어 취소 요청을 만들 수 없음(응답의 상점이 다른 것과 구분한다)
    PG_CONFIG_ERROR,
    // 취소 요청에 401·403
    PG_AUTH_ERROR,
    // 취소 요청에 400
    PG_REQUEST_REJECTED,
    // 취소 내역의 상태·금액·ID를 해석할 수 없어 합계를 신뢰할 수 없음
    CANCELLATION_STATUS_UNKNOWN,
    // 환불 실행 기능이 없던 때 만들어져 PG 발송 이력을 증명할 수 없는 행. 자동 POST 금지
    LEGACY_DISPATCH_UNPROVEN,
    // 강한 차단 — 저장된 근거(취소 ID·금액·reason·귀속)와 PG 관측이 모순
    EVIDENCE_CONFLICT,
    // 강한 차단 — 환불 대상 회차·시도가 현재 이용권 기간과 맞지 않음
    TARGET_PERIOD_MISMATCH,
    // 강한 차단 — PG 응답의 결제 식별값(상점·통화·원수납액)이 저장 근거와 명백히 다름
    PG_IDENTITY_MISMATCH;

    public static final Set<MembershipRefundOpsReason> BLOCKING = Set.of(
        EVIDENCE_CONFLICT, TARGET_PERIOD_MISMATCH, PG_IDENTITY_MISMATCH);

    // 강한 정합성 차단. 관리자 GET 관측만 허용하고 자동 반영·차단 해제는 하지 않는다
    public boolean isBlocking() {
        return BLOCKING.contains(this);
    }

    // 모든 사유가 자동 실행(복구 조회·재POST)을 멈춘다
    public boolean autoHalt() {
        return true;
    }
}
