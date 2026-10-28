package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import java.time.LocalDateTime;

// 마트 거절 API의 응답. Processor가 트랜잭션 안에서 같은 시점의 결제·환불로 만든다 — 커밋 뒤 따로 읽으면 다른 경로가 바꾼 값이 섞인다.
// 거절 접수 뒤 주문은 항상 CANCEL_REQUESTED·REJECTED라 주문·마트 상태는 넣지 않는다
public record MartRejectionResponse(
    Long orderId,
    Long paymentId,
    PaymentStatus paymentStatus,
    LocalDateTime canceledAt,
    Long refundId,
    RefundStatus refundStatus,
    Long refundAmount
) {

    public static MartRejectionResponse of(Payment payment, Refund refund) {
        return new MartRejectionResponse(
            payment.getOrderId(),
            payment.getId(),
            payment.getStatus(),
            payment.getCanceledAt(),
            refund.getId(),
            refund.getStatus(),
            refund.getAmount()
        );
    }
}
