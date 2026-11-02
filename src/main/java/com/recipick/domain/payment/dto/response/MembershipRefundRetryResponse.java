package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;

// 운영자 SYSTEM 환불 재실행 결과. 후속 환불(새로 만든 것 또는 이미 있던 것)의 ID·상태와 원래 실패 환불 ID, 동기 실행 1회의 결과.
// MEMBER 응답(기간·이용일수)과 섞지 않는다
public record MembershipRefundRetryResponse(
    Long refundId,
    Long retryOfRefundId,
    boolean existing,
    MembershipRefundStatus status,
    Long amount,
    MembershipRefundExecutionResult executionResult,
    boolean reviewRequired,
    MembershipRefundOpsReason opsReviewReason,
    String portoneCancellationId
) {

    public static MembershipRefundRetryResponse of(MembershipRefund followUp, boolean existing,
                                                   MembershipRefundExecutionResult executionResult) {
        return new MembershipRefundRetryResponse(followUp.getId(), followUp.getRetryOfRefundId(), existing,
            followUp.getStatus(), followUp.getAmount(), executionResult, followUp.isReviewRequired(),
            followUp.getOpsReviewReason(), followUp.getPortoneCancellationId());
    }
}
