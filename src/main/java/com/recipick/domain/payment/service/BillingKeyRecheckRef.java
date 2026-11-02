package com.recipick.domain.payment.service;

// 빌링키 재검사 후보(잠금 전 일반 조회 투영). 키 원문은 들어 있지 않다 — 조회 직전에 키 행에서 읽는다
public record BillingKeyRecheckRef(Long billingKeyId, Long memberId) {
}
