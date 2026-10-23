package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import java.time.LocalDateTime;

// 결제 내역 목록 전용. portonePaymentId·expiresAt은 결제창 재진입용이라 상세 조회에만 둔다
public record PaymentHistoryResponse(
    Long paymentId,
    Long orderId,
    PaymentMethod paymentMethod,
    Long amount,
    PaymentStatus paymentStatus,
    PaymentFailureReason failureReason,
    LocalDateTime paidAt,
    LocalDateTime canceledAt,
    LocalDateTime createdAt,
    Long completedRefundAmount,
    RefundStatus latestRefundStatus
) {
    public static PaymentHistoryResponse of(Payment payment, RefundSummary refundSummary) {
        return new PaymentHistoryResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getPaymentMethod(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getPaidAt(),
            payment.getCanceledAt(),
            payment.getCreatedAt(),
            refundSummary.completedAmount(),
            refundSummary.latestStatus()
        );
    }
}
