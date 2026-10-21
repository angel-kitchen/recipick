package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentConfirmResponse(
    Long paymentId,
    Long orderId,
    PaymentMethod paymentMethod,
    Long amount,
    PaymentStatus paymentStatus,
    LocalDateTime paidAt
) {
    public static PaymentConfirmResponse from(Payment payment) {
        return new PaymentConfirmResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getPaymentMethod(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getPaidAt()
        );
    }
}
