package com.recipick.domain.payment.service;

// 회원 환불 재확인·SYSTEM 환불 실행·마트 거절 환불 실행 한 번의 결과. 환불 행에는 RefundStatus만 남고, 이 값은 Worker 로그와 테스트에서만 쓴다.
// STALLED·MANUAL_REVIEW는 웹훅의 MANUAL_REVIEW와 달리 영구 종료가 아니다 — 환불이 REQUESTED로 남는 한 Fallback이 1시간마다 다시 넣는다
public enum RefundReconcileOutcome {

    SKIPPED,        // 스냅샷에서 환불이 없거나 이미 끝났거나 대상 종류(회원·SYSTEM·ADMIN)가 아니라 확인하지 않았다
    RESOLVED,       // 이번 실행의 apply로 완료·실패가 확정됐다
                    // 스냅샷 뒤 다른 경로가 먼저 확정해 apply가 그 상태를 돌려준 경우도 포함한다
    RESCHEDULED,    // 아직 미확정이라 RECHECK_DELAY 뒤 재확인이 필요해 큐 등록을 시도했다
                    // Redis 예약이 실패해도 환불이 REQUESTED로 남아 Fallback이 다시 등록한다
    STALLED,        // 고빈도 재확인 시간(RECHECK_WINDOW)이 지나도 미확정이다. 10분 재예약을 멈추고 ERROR를 남긴다
                    // 재확인은 Fallback의 1시간 주기로 계속된다
    MANUAL_REVIEW   // 자동으로 고를 수 없는 불일치나 내부 오류다. 10분 재예약을 멈추고 ERROR로 운영 확인을 요청한다
                     // 환불은 REQUESTED로 두므로 Fallback의 1시간 재확인은 계속된다
}
