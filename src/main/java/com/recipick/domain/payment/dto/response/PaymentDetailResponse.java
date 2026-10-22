package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;

import java.time.LocalDateTime;

// 실패 사유(failureReason)는 담지 않는다. 내부 사유 코드라 회원에게 보일 값이 아니고 관리자 상세 조회의 몫임.
public record PaymentDetailResponse(
    Long paymentId,
    Long orderId,
    String portonePaymentId,
    PaymentMethod paymentMethod,
    Long amount,
    PaymentStatus paymentStatus,
    LocalDateTime paidAt,
    LocalDateTime expiresAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        return new PaymentDetailResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getPortonePaymentId(),
            payment.getPaymentMethod(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getPaidAt(),
            payment.getExpiresAt()
        );
    }
}
