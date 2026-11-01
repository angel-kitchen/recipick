package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import java.time.LocalDateTime;

// 시도의 ID·값 투영(잠금 전 일반 조회용). 실행기·복구가 회차·이용권 ID와 결제 ID를 얻는 데 쓰고, 상태 변경은 잠금 조회로
// 다시 읽은 엔티티에만 한다. 빌링키는 들어 있지 않다
public record MembershipPaymentAttemptRef(
    Long attemptId,
    Long membershipPaymentId,
    Long membershipId,
    String portonePaymentId,
    Long amount,
    MembershipPaymentAttemptStatus status,
    LocalDateTime dispatchedAt,
    LocalDateTime leaseExpiresAt,
    String failureReason,
    LocalDateTime opsReviewRequiredAt
) {

    // 자동 정합화 중단(엔티티와 같은 판정). reconcile이 잠금 전에 미리 읽은 값으로 걸러내고, TX2 진입이 잠금 아래에서 다시 본다
    public boolean isAutoReconcileHalted() {
        return MembershipPaymentAttempt.isAutoReconcileHalted(failureReason, opsReviewRequiredAt);
    }

    public boolean isFinal() {
        return status == MembershipPaymentAttemptStatus.PAID
            || status == MembershipPaymentAttemptStatus.FAILED
            || status == MembershipPaymentAttemptStatus.CANCELED;
    }
}
