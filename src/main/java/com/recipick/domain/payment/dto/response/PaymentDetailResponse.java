package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import java.time.LocalDateTime;

// 요구사항(결제 내역 조회): 실패 사유와, 취소가 발생한 결제의 완료 환불 금액·최근 환불 상태를 함께 보여준다
public record PaymentDetailResponse(
    Long paymentId,
    Long orderId,
    String portonePaymentId,
    PaymentMethod paymentMethod,
    Long amount,
    PaymentStatus paymentStatus,
    PaymentFailureReason failureReason,
    LocalDateTime paidAt,
    LocalDateTime expiresAt,
    Long completedRefundAmount,
    RefundStatus latestRefundStatus
) {
    public static PaymentDetailResponse of(Payment payment, RefundSummary refundSummary) {
        return new PaymentDetailResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getPortonePaymentId(),
            payment.getPaymentMethod(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getPaidAt(),
            payment.getExpiresAt(),
            refundSummary.completedAmount(),
            refundSummary.latestStatus()
        );
    }
}
