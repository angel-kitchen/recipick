package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import java.time.LocalDateTime;

// 환불 행의 ID·값 투영(잠금 전 일반 조회용). 실행기·웹훅·관리자가 회원·이용권·회차·시도 ID와 결제 ID를 얻는 데 쓰고,
// 상태 변경은 잠금 조회로 다시 읽은 엔티티에만 한다
public record MembershipRefundRef(
    Long refundId,
    Long attemptId,
    Long membershipPaymentId,
    Long membershipId,
    String portonePaymentId,
    MembershipRefundStatus status,
    RefundRequester requestedBy,
    Long amount,
    String portoneCancellationId,
    String pgRequestReason,
    LocalDateTime leaseExpiresAt,
    LocalDateTime nextCheckAt,
    MembershipRefundOpsReason opsReviewReason,
    Long retryOfRefundId
) {

    public boolean isSettlementBlocked() {
        return opsReviewReason != null && opsReviewReason.isBlocking();
    }
}
