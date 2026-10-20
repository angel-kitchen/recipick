package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import java.time.LocalDateTime;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PaymentTest {

    @Test
    @DisplayName("결제 대기 상태에서 결제 완료로 변경한다")
    void markPaid() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 20, 16, 55);


        // when
        payment.markPaid("tx_1", paidAt);


        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPortoneTransactionId()).isEqualTo("tx_1");
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
    }


    @Test
    @DisplayName("실패한 결제는 결제 완료 상태로 변경할 수 없다")
    void markPaidWhenFailed() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.FAILED)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 20, 16, 55);


        // when & then
        assertThatThrownBy(() -> payment.markPaid("tx_1", paidAt))
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException = (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
            });
    }


    @Test
    @DisplayName("이미 결제 완료된 결제는 다시 결제 완료 상태로 변경할 수 없다")
    void markPaidWhenAlreadyPaid() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.PAID)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 20, 16, 55);

        // when & then
        assertThatThrownBy(() -> payment.markPaid("tx_1", paidAt))
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException = (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
            });
    }


    @Test
    @DisplayName("결제 대기 상태에서 결제 실패로 변경한다")
    void fail() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        // when
        payment.fail(PaymentFailureReason.PG_FAILED, "tx_1");

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.PG_FAILED);
        assertThat(payment.getPortoneTransactionId()).isEqualTo("tx_1");
    }


    @Test
    @DisplayName("이미 결제 완료된 결제는 결제 실패 상태로 변경할 수 없다")
    void failWhenPaid() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.PAID)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        // when & then
        assertThatThrownBy(() ->
            payment.fail(PaymentFailureReason.PG_FAILED, "tx_1"))
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException = (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
            });
    }
}
