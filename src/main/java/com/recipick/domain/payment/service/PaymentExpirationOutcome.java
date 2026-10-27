package com.recipick.domain.payment.service;

// 결제 5분 만료 처리 한 번의 결과. 결제 행에는 PaymentStatus만 남고, 이 값은 Worker 로그와 테스트에서만 쓴다
public enum PaymentExpirationOutcome {

    EXPIRED,          // 이번 실행으로 결제를 FAILED(EXPIRED)로 닫고 주문 종료·재고 복구까지 반영했다
    SKIPPED           // 잠금 아래에서 다시 보니 이미 다른 경로가 결제를 끝냈거나 아직 만료 시각 전이라 바꾸지 않았다
}
