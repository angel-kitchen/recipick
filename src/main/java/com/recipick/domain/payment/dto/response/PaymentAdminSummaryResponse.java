package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;

import java.time.LocalDateTime;

// 관리자 결제 목록의 한 줄.
// 환불은 완료 금액 합계와 가장 최근 시도의 상태만 요약하고, 전체 시도 이력은 상세에서 준다
public record PaymentAdminSummaryResponse(
    Long paymentId,
    Long orderId,
    Long memberId,
    PaymentMethod paymentMethod,
    Long amount,
    PaymentStatus paymentStatus,
    PaymentFailureReason failureReason,
    LocalDateTime paidAt,
    LocalDateTime canceledAt,
    Long refundedAmount,
    RefundStatus latestRefundStatus,
    LocalDateTime createdAt
) {
    public static PaymentAdminSummaryResponse of(Payment payment, Long memberId, RefundSummary refundSummary) {
        return new PaymentAdminSummaryResponse(
            payment.getId(),
            payment.getOrderId(),
            memberId,
            payment.getPaymentMethod(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getPaidAt(),
            payment.getCanceledAt(),
            refundSummary.completedAmount(),
            refundSummary.latestStatus(),
            payment.getCreatedAt()
        );
    }
}
