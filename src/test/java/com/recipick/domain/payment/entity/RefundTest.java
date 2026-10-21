package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundTest {

    @Test
    @DisplayName("SYSTEM 환불은 REQUESTED 상태로 생성되고 넘긴 멱등키가 저장된다")
    void requestedBySystem() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.FAILED)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        LocalDateTime requestedAt = LocalDateTime.of(2026, 10, 20, 17, 10);


        // when
        Refund refund = Refund.requestedBySystem(
            payment,
            23800L,
            "결제 금액 불일치",
            "key_1",
            requestedAt
        );

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(refund.getAmount()).isEqualTo(23800L);
        assertThat(refund.getReason()).isEqualTo("결제 금액 불일치");
        assertThat(refund.getIdempotencyKey()).isEqualTo("key_1");
        assertThat(refund.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(refund.getPayment()).isSameAs(payment);

    }
}
