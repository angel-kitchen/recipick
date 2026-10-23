package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import java.time.LocalDateTime;

// 결제 전·후 취소가 같은 응답을 쓴다. 결제 전 취소는 RefundSummary.NONE을 받아 환불 금액 0, 환불 상태 null이 된다
public record PaymentCancelResponse(
    Long paymentId,
    Long orderId,
    PaymentStatus paymentStatus,
    PaymentFailureReason failureReason,
    LocalDateTime canceledAt,
    RefundStatus refundStatus,
    Long refundAmount
) {
    public static PaymentCancelResponse of(Payment payment, RefundSummary refundSummary) {
        return new PaymentCancelResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getCanceledAt(),
            refundSummary.latestStatus(),
            refundSummary.completedAmount()
        );
    }
}
