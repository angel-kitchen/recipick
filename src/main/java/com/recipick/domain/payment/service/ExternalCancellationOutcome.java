package com.recipick.domain.payment.service;

// 외부 전액 취소 반영 결과. 웹훅 정합화가 이 값으로 PROCESSED·RETRY_PENDING·MANUAL_REVIEW를 정한다
public enum ExternalCancellationOutcome {
    RECONCILED,          // 결제·환불·주문·재고를 이번에 반영했다
    ALREADY_RECONCILED,  // 같은 취소가 이미 정상 상태로 반영돼 있다. 아무것도 바꾸지 않았다
    RETRY,               // 진행 중인 기존 환불로 마무리돼야 한다. 아무것도 바꾸지 않았고 다시 실행한다
    NOT_APPLICABLE       // 자동으로 반영하면 안 되는 상태다. 아무것도 바꾸지 않았고 운영 확인이 필요하다
}
