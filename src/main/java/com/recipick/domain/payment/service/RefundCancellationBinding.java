package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;

// 같은 결제 시도에 걸린 환불 행 하나가 PG 취소 내역과 어떻게 연결돼 있는지(취소 ID·서버 생성 reason). 매칭 함수가
// 다른 환불에 귀속된 취소·모순된 성공·설명되지 않는 취소를 가려내는 데 쓴다. 호출자가 잠금 아래에서 읽어 값으로 넘긴다
public record RefundCancellationBinding(
    Long refundId,
    MembershipRefundStatus status,
    String portoneCancellationId,
    String pgRequestReason
) {

    public static RefundCancellationBinding of(MembershipRefund refund) {
        return new RefundCancellationBinding(refund.getId(), refund.getStatus(), refund.getPortoneCancellationId(),
            refund.getPgRequestReason());
    }
}
