package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.enums.RefundRequester;
import java.time.LocalDateTime;

// 환불 실행권 티켓. 실행권 TX가 잠근 행에서 읽어 돌려주고, 실행기는 이 값으로만 PG를 부른다.
// claimToken은 실행권 갱신·POST 준비·응답 기록·예약 변경을 지키는 토큰이고, 확정 결과 반영(TX2)은 토큰과 무관하게 증거로 지킨다.
// pgStoreId·pgRequestReason·pgIdempotencyKey는 행에 고정된 값(설정이 바뀌어도 바뀌지 않는다)
public record MembershipRefundTicket(
    Long refundId,
    Long attemptId,
    Long membershipPaymentId,
    Long membershipId,
    Long memberId,
    String portonePaymentId,
    long claimToken,
    LocalDateTime leaseExpiresAt,
    String pgIdempotencyKey,
    String pgStoreId,
    String pgRequestReason,
    long amount,
    long paidAmountSnapshot,
    RefundRequester requester,
    LocalDateTime requestedAt,
    LocalDateTime firstDispatchedAt,
    String portoneCancellationId
) {

    // 결과 확인 창·재조회 간격의 기준(최초 발송 시각, 없으면 접수 시각)
    public LocalDateTime checkBaseAt() {
        return firstDispatchedAt != null ? firstDispatchedAt : requestedAt;
    }

    public MembershipRefundTicket withFirstDispatchedAt(LocalDateTime at) {
        return new MembershipRefundTicket(refundId, attemptId, membershipPaymentId, membershipId, memberId,
            portonePaymentId, claimToken, leaseExpiresAt, pgIdempotencyKey, pgStoreId, pgRequestReason, amount,
            paidAmountSnapshot, requester, requestedAt, at, portoneCancellationId);
    }
}
