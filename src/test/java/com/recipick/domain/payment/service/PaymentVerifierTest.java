package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentVerifierTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 21, 10, 5);

    private final PaymentVerifier verifier = new PaymentVerifier();

    @Test
    @DisplayName("IN_PROGRESS 결제에 PortOne이 PAID·같은 주문·같은 금액·만료 시각과 같은 paidAt이면 승인한다")
    void approved() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.APPROVED);
    }

    @Test
    @DisplayName("이미 PAID인 결제는 PortOne 결과와 상관없이 ALREADY_PAID다")
    void alreadyPaid() {
        // given
        Payment payment = payment(PaymentStatus.PAID);
        PortOnePayment portOne = portOne(PortOnePaymentStatus.FAILED, 1L, 23800L, null);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_PAID);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELED"})
    @DisplayName("FAILED·CANCELED 결제는 ALREADY_CLOSED다")
    void alreadyClosed(PaymentStatus status) {
        // given
        Payment payment = payment(status);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment, paid());

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
    }

    @Test
    @DisplayName("CANCEL_REQUESTED 결제는 INVALID_STATUS다")
    void invalidStatus() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment, paid());

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.INVALID_STATUS);
    }

    @Test
    @DisplayName("PortOne이 FAILED면 PG_FAILED다")
    void pgFailed() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.FAILED, 1L, 23800L, null);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.PG_FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = PortOnePaymentStatus.class, names = {"CANCELED", "PARTIAL_CANCELED"})
    @DisplayName("PortOne 쪽에서 취소된 결제는 상태를 바꾸지 않는 INVALID_STATUS다 (테스트 채널 확인 전 임시)")
    void pgCanceled(PortOnePaymentStatus status) {
        // given
        PortOnePayment portOne = portOne(status, 1L, 23800L, null);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.INVALID_STATUS);
    }

    @ParameterizedTest
    @EnumSource(value = PortOnePaymentStatus.class,
        names = {"READY", "PENDING", "VIRTUAL_ACCOUNT_ISSUED", "UNKNOWN"})
    @DisplayName("PortOne이 아직 결제 완료가 아니면 NOT_PAID다")
    void notPaid(PortOnePaymentStatus status) {
        // given
        PortOnePayment portOne = portOne(status, 1L, 23800L, null);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.NOT_PAID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {2L})
    @DisplayName("customData 주문 ID가 없거나 다르면 ORDER_MISMATCH다")
    void orderMismatch(Long customDataOrderId) {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, customDataOrderId, 23800L, EXPIRES_AT);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ORDER_MISMATCH);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {20000L})
    @DisplayName("PG 결제 금액이 없거나 PAYMENT.amount와 다르면 AMOUNT_MISMATCH다")
    void amountMismatch(Long totalAmount) {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, totalAmount, EXPIRES_AT);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("PAID인데 paidAt이 없으면 만료가 아니라 NOT_PAID다 (EXPIRED는 환불·주문 종료를 집행하므로)")
    void paidAtMissingIsNotPaid() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, null);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.NOT_PAID);
    }

    @Test
    @DisplayName("PG 결제 완료 시각이 만료 시각을 1초라도 넘기면 EXPIRED다")
    void expired() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT.plusSeconds(1));

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.EXPIRED);
    }

    @Test
    @DisplayName("금액 불일치는 만료보다 먼저 판정한다")
    void amountMismatchBeforeExpired() {
        // given
        PortOnePayment portOne = portOne(
            PortOnePaymentStatus.PAID,
            1L,
            20000L,
            EXPIRES_AT.plusMinutes(10)
        );

        // when
        PaymentApprovalOutcome outcome =
            verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.AMOUNT_MISMATCH);
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(status)
            .expiresAt(EXPIRES_AT)
            .build();
    }

    private PortOnePayment paid() {
        return portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT.minusMinutes(1));
    }

    private PortOnePayment portOne(PortOnePaymentStatus status, Long orderId, Long amount, LocalDateTime paidAt) {
        return new PortOnePayment("pay_1", "tx_1", status, amount, "KRW", paidAt, orderId, null);
    }
}
