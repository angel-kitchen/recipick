package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.service.MembershipRefundCheckResult;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;

// 운영자 환불 재조회 결과. 저장된 환불의 최신 상태(status·completedAt·reviewRequired·opsReviewReason)와 이번 GET의 관측값
// (pgLookedUp·pgPaymentStatus·관측 취소 ID·상태·금액·실패 코드)을 분리해 담는다 — GET이 실패했으면 저장 상태가 COMPLETED여도
// pgLookedUp=false다. MEMBER·SYSTEM 행 모두 이 응답을 쓴다(기간 값은 넣지 않는다). PG 원문·고객 정보는 없다
public record MembershipRefundRecheckResponse(
    Long refundId,
    Long attemptId,
    RefundRequester requestedBy,
    MembershipRefundStatus status,
    Long amount,
    MembershipRefundExecutionResult outcome,
    boolean reviewRequired,
    MembershipRefundOpsReason opsReviewReason,
    String portoneCancellationId,
    LocalDateTime completedAt,
    Long retryOfRefundId,
    boolean pgLookedUp,
    PortOnePaymentStatus pgPaymentStatus,
    String pgCancellationId,
    PortOneCancellationStatus pgCancellationStatus,
    Long pgCancellationAmount,
    String pgLookupCode
) {

    public static MembershipRefundRecheckResponse of(MembershipRefund after, MembershipRefundCheckResult check) {
        return new MembershipRefundRecheckResponse(after.getId(), after.getMembershipPaymentAttempt().getId(),
            after.getRequestedBy(), after.getStatus(), after.getAmount(), check.outcome(), after.isReviewRequired(),
            after.getOpsReviewReason(), after.getPortoneCancellationId(), after.getCompletedAt(),
            after.getRetryOfRefundId(), check.observation().lookedUp(), check.observation().paymentStatus(),
            check.observation().cancellationId(), check.observation().cancellationStatus(),
            check.observation().cancellationAmount(), check.observation().code());
    }
}
