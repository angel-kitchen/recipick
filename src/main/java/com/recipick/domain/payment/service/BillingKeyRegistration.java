package com.recipick.domain.payment.service;

// 등록 트랜잭션의 결과. 키 원문은 담지 않는다
public record BillingKeyRegistration(
    Long billingKeyId,
    BillingKeyRegistrationOutcome outcome
) {
}
