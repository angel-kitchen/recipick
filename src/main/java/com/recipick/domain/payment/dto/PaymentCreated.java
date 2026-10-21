package com.recipick.domain.payment.dto;

import com.recipick.domain.payment.entity.Payment;

import java.time.LocalDateTime;

public record PaymentCreated(
    Long paymentId,
    String portonePaymentId,
    Long amount,
    LocalDateTime expiresAt
) {
    public static PaymentCreated from(Payment payment) {
        return new PaymentCreated(
            payment.getId(),
            payment.getPortonePaymentId(),
            payment.getAmount(),
            payment.getExpiresAt()
        );
    }
}
