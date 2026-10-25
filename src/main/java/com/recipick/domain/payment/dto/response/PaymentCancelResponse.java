package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import java.time.LocalDateTime;

// 결제 전·후 취소가 같은 응답을 쓴다. refundAmount는 "이번 환불 작업의 금액"이라 REQUESTED여도 전액이 나간다 —
// 결제 조회의 refundedAmount(COMPLETED 합계)와 뜻이 다르다. Processor가 트랜잭션 안에서 같은 시점의 결제·환불로 만든다
public record PaymentCancelResponse(
    Long paymentId,
    Long orderId,
    PaymentStatus paymentStatus,
    PaymentFailureReason failureReason,
    LocalDateTime canceledAt,
    RefundStatus refundStatus,
    Long refundAmount
) {

    // 결제 전 취소는 PG를 거치지 않아 환불이 없다
    public static PaymentCancelResponse beforePay(Payment payment) {
        return new PaymentCancelResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getCanceledAt(),
            null,
            0L
        );
    }

    public static PaymentCancelResponse afterPay(Payment payment, Refund refund) {
        return new PaymentCancelResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getCanceledAt(),
            refund.getStatus(),
            refund.getAmount()
        );
    }
}
