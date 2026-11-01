package com.recipick.domain.payment.service;

// C-11 응답 상태. 회원 도메인 Facade가 HTTP 응답으로 매핑한다
public enum MembershipSubscribeStatus {
    IN_PROGRESS,  // 접수됨·처리 중(READY·DISPATCHING). 결과는 다시 조회
    PAID,         // 첫 결제 확정, 이용권 활성
    FAILED,       // 확정 실패·반영 불가(환불 요청). 같은 키로 재청구 없음 — 새 구독은 새 키로
    UNKNOWN       // 결과 불명·운영 확인. 재조회가 정한다
}
