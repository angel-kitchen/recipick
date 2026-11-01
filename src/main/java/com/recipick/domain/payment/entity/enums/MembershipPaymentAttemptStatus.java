package com.recipick.domain.payment.entity.enums;

// 결제 시도(PortOne 결제 ID 하나)의 상태. PAID·FAILED·CANCELED는 확정. 전이 메서드는 첫 결제·갱신
public enum MembershipPaymentAttemptStatus {
    READY,          // 접수됨(TX1). 아직 PortOne을 부르지 않음
    DISPATCHING,    // 선점됨. PortOne 호출 중
    UNKNOWN,        // 호출 결과 불명. 같은 결제 ID로 재조회
    MANUAL_REVIEW,  // 재조회로도 확정 불가. 운영 확인
    PAID,
    FAILED,
    CANCELED        // 호출 전에 접은 시도(회차 종료 등)
}
