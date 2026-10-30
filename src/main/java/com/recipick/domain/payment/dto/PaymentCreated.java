package com.recipick.domain.payment.dto;

import com.recipick.domain.payment.entity.Payment;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentCreated(
    Long paymentId,
    String portonePaymentId,
    Long amount,
    LocalDateTime expiresAt,
    LocalDateTime paidAt               // paidAt은 0원 결제처럼 만들 때 이미 결제가 끝난 경우에만 있다. 카드 결제는 생성 점에 null이다
) {
    public PaymentCreated(Long paymentId, String portonePaymentId, Long amount, LocalDateTime expiresAt) {
        this(paymentId, portonePaymentId, amount, expiresAt, null);
    }

    public static PaymentCreated from(Payment payment) {
        return new PaymentCreated(
            payment.getId(),
            payment.getPortonePaymentId(),
            payment.getAmount(),
            payment.getExpiresAt(),
            payment.getPaidAt()
        );
    }
}
