package com.recipick.domain.payment.service;

// 결제 지연 큐에 들어가는 작업 종류.  member 문자열의 접두사가 됌
public enum PaymentDelayTaskType {
    WEBHOOK_PROCESS,       // 웹훅 inbox 행(WEBHOOK_EVENT.id)을 처리한다
    REFUND_RECONCILE,      // PortOne 결과를 못 받은 회원 환불(REFUND.id)을 재조회로 확정한다
    SYSTEM_REFUND_EXECUTE  // SYSTEM 환불(REFUND.id)을 PortOne 조회 뒤 필요하면 취소까지 실행해 확정한다
}
