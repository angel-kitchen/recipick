package com.recipick.domain.payment.service;

// 환불 실행·재조회 한 번의 결과(DB 상태와 별개).
public enum MembershipRefundExecutionResult {
    COMPLETED,        // 취소 성공을 확인해 환불 COMPLETED(MEMBER면 이용권 종료까지 같은 TX)
    FAILED,           // 식별·금액이 맞는 FAILED 취소를 확인해 환불 FAILED
    PENDING,          // 결과 불명·처리 중·일시적 불일치. REQUESTED 유지, 다음 확인 예약
    REVIEW_REQUIRED,  // 운영 확인 표시(자동 중단 또는 강한 차단). REQUESTED 유지
    SKIPPED           // 실행권을 잡지 못함(이미 확정·다른 실행자 임대 중·운영 확인 표시)
}
