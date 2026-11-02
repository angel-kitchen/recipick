package com.recipick.domain.payment.service;

// 관리자 재조회 한 번의 결과 — 반영 결과와 이번 GET의 관측값을 함께 돌려준다(관측값을 잃거나 GET을 한 번 더 하지 않게)
public record MembershipRefundCheckResult(
    MembershipRefundExecutionResult outcome,
    RefundPgObservation observation
) {
}
