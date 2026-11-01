package com.recipick.domain.payment.service;

// 첫 결제 실행·결과 반영의 결과.
public enum MembershipChargeOutcome {
    ACTIVATED,                   // 이용권 활성화
    CLOSED_UNPAID,               // 조회로 확정된 실패 -> 대기 이용권 종료
    UNAPPLIED_REFUND_REQUESTED,  // 결제는 됐지만 반영하지 않음 -> SYSTEM 환불 REQUESTED
    STILL_UNKNOWN,               // 결과 불명 유지. 복구가 같은 ID로 재조회
    MANUAL_REVIEW,               // 신뢰할 수 없는 결과. 운영 확인
    ALREADY_APPLIED,             // 이미 확정된 시도(멱등). 아무것도 바꾸지 않음
    NOT_DISPATCHED,              // 플래그 OFF·프로필 실패·READY 유지. PG 미호출
    IN_PROGRESS,                 // 다른 Worker가 선점해 PG 호출 중(임대 안). 재조회하지 않고 기다린다
    RETRY_READY,                 // READY 만료 -> CANCELED -> 재검사 통화 -> 새 시도 READY
    RECONCILE_HALTED             // 정합성 오류로 자동 정합화가 멈춘 시도(운영 확인 대상). 아무것도 바꾸지 않음. 미해결 그대로 C-17이 막는다
}
