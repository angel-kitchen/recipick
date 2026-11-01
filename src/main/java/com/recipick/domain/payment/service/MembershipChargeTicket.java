package com.recipick.domain.payment.service;

import java.time.LocalDateTime;

// 선점 결과. 실행기가 PG 호출에 필요한 값을 담아 간다. dispatchedAt은 releaseUnsent의 "같은 선점" 확인용,
// acceptUntil은 POST 직전 접수 유효 창 재확인용(첫 시도 requestedAt + 창). 빌링키 원문이 들어 있어 toString은 가린다
public record MembershipChargeTicket(
    Long attemptId,
    Long membershipPaymentId,
    Long membershipId,
    String portonePaymentId,
    String billingKey,
    long amount,
    LocalDateTime dispatchedAt,
    LocalDateTime acceptUntil
) {

    @Override
    public String toString() {
        return "MembershipChargeTicket[attemptId=" + attemptId + ", membershipPaymentId=" + membershipPaymentId
            + ", membershipId=" + membershipId + ", portonePaymentId=" + portonePaymentId + ", amount=" + amount
            + ", dispatchedAt=" + dispatchedAt + ", acceptUntil=" + acceptUntil + "]";
    }
}
