package com.recipick.domain.payment.entity.enums;

// 멤버십 결제 회차의 상태. PAID·FAILED는 확정이라 바뀌지 않는다. 전이 메서드는 4단계
public enum MembershipPaymentStatus {
    PENDING,                  // 접수됨. 시도가 진행 중이거나 곧 시작
    RETRY_WAITING,            // 갱신 실패 뒤 다음 시도 대기(3일 유예)
    PAYMENT_METHOD_REQUIRED,  // 활성 빌링키가 없어 카드 등록 대기
    UNKNOWN,                  // PG 결과 불명. 재조회로만 확정
    MANUAL_REVIEW,            // 자동으로 확정할 수 없어 운영 확인
    PAID,                     // 확정
    FAILED                    // 확정
}
