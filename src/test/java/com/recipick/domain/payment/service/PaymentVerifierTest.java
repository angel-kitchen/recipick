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

    @Test
    @DisplayName("PG 결제 금액이 PAYMENT.amount와 다르면 AMOUNT_MISMATCH다")
    void amountMismatch() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 20000L, EXPIRES_AT);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("PAID인데 금액이 없으면 불일치가 아니라 NOT_PAID다 (AMOUNT_MISMATCH는 환불·주문 종료를 집행하므로)")
    void amountMissingIsNotPaid() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, null, EXPIRES_AT);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.NOT_PAID);
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
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 20000L, EXPIRES_AT.plusMinutes(10));

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.AMOUNT_MISMATCH);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("PAID인데 통화가 없거나 비어 있으면 승인 근거가 없는 것이라 불일치가 아니라 NOT_PAID다")
    void currencyMissingIsNotPaid(String currency) {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT, currency);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.NOT_PAID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "JPY", "krw"})
    @DisplayName("PAID인데 통화가 정확히 KRW가 아니면 CURRENCY_MISMATCH다 — 다른 통화를 원화로 바꿔 보지 않는다")
    void currencyMismatch(String currency) {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT, currency);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.CURRENCY_MISMATCH);
    }

    @Test
    @DisplayName("통화 불일치는 금액 불일치·만료보다 먼저 판정한다 — 원화가 아닌 금액은 비교 대상이 아니다")
    void currencyMismatchBeforeAmountMismatch() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 20000L, EXPIRES_AT.plusMinutes(10), "USD");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.CURRENCY_MISMATCH);
    }

    @Test
    @DisplayName("주문 ID 불일치는 통화보다 먼저 판정한다")
    void orderMismatchBeforeCurrency() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 2L, 23800L, EXPIRES_AT, "USD");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ORDER_MISMATCH);
    }

    @Test
    @DisplayName("FAILED 결제의 늦은 승인이 같은 주문·KRW·수납액 있음이면 기존대로 ALREADY_CLOSED다 — Processor의 늦은 승인 환불 경로가 그대로 이어진다")
    void lateApprovalInKrwIsAlreadyClosed() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT.plusMinutes(10), "KRW");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.FAILED), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
    }

    @Test
    @DisplayName("FAILED 결제의 늦은 승인은 PAYMENT.amount와 다른 수납액이어도 ALREADY_CLOSED다 — 실제 수납액을 돌려주는 경로라 금액 비교로 막지 않는다")
    void lateApprovalWithDifferentAmountIsStillAlreadyClosed() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 25000L, EXPIRES_AT.plusMinutes(10), "KRW");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.FAILED), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {2L})
    @DisplayName("FAILED 결제의 늦은 승인이라도 customData 주문 ID가 없거나 다르면 ORDER_MISMATCH다 — 통화보다 먼저 보고 자동 환불 대상으로 확정하지 않는다")
    void lateApprovalWithOrderMismatch(Long customDataOrderId) {
        // given — 통화도 틀렸지만 주문 ID 판정이 먼저다
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, customDataOrderId, 23800L,
            EXPIRES_AT.plusMinutes(10), "USD");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.FAILED), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ORDER_MISMATCH);
    }

    @Test
    @DisplayName("FAILED 결제의 늦은 승인이 KRW인데 수납액이 없으면 ALREADY_CLOSED가 아니라 NOT_PAID다 — 환불을 만들 근거가 없어 재조회 경로로 둔다")
    void lateApprovalWithoutAmountIsNotPaid() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, null, EXPIRES_AT.plusMinutes(10), "KRW");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.FAILED), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.NOT_PAID);
    }

    @Test
    @DisplayName("FAILED 결제의 늦은 승인이 KRW가 아니면 ALREADY_CLOSED가 아니라 CURRENCY_MISMATCH다 — 그 금액으로 SYSTEM 환불을 만들지 않는다")
    void lateApprovalInForeignCurrencyIsCurrencyMismatch() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT.plusMinutes(10), "USD");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.FAILED), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.CURRENCY_MISMATCH);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {""})
    @DisplayName("FAILED 결제의 늦은 승인에 통화가 없으면 환불 근거가 없어 ALREADY_CLOSED가 아니라 NOT_PAID다")
    void lateApprovalWithoutCurrencyIsNotPaid(String currency) {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT.plusMinutes(10), currency);

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.FAILED), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.NOT_PAID);
    }

    @Test
    @DisplayName("FAILED 결제에 PG가 PAID가 아니면 통화와 상관없이 ALREADY_CLOSED다 — 늦은 승인 경로가 아니다")
    void closedPaymentWithPgNotPaidIgnoresCurrency() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.FAILED, 1L, 23800L, null, "USD");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.FAILED), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
    }

    @Test
    @DisplayName("CANCELED 결제는 PG PAID·다른 통화여도 ALREADY_CLOSED다 — 취소 흐름 몫이라 통화를 보지 않는다")
    void canceledPaymentIgnoresCurrency() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT, "USD");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.CANCELED), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
    }

    @Test
    @DisplayName("이미 PAID인 결제는 다른 통화가 와도 ALREADY_PAID다 — 멱등 판정이 먼저다")
    void alreadyPaidIgnoresCurrency() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID, 1L, 23800L, EXPIRES_AT, "USD");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.PAID), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_PAID);
    }

    @Test
    @DisplayName("PG가 FAILED면 통화가 달라도 PG_FAILED다 — 실패 확정은 통화 검증 전이다")
    void pgFailedIgnoresCurrency() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.FAILED, 1L, 23800L, null, "USD");

        // when
        PaymentApprovalOutcome outcome = verifier.verify(payment(PaymentStatus.IN_PROGRESS), portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.PG_FAILED);
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

    // 기존 테스트는 모두 KRW다. 통화 판정을 보는 테스트만 아래 보조 메서드로 통화를 직접 준다
    private PortOnePayment portOne(PortOnePaymentStatus status, Long orderId, Long amount, LocalDateTime paidAt) {
        return portOne(status, orderId, amount, paidAt, "KRW");
    }

    private PortOnePayment portOne(PortOnePaymentStatus status, Long orderId, Long amount, LocalDateTime paidAt,
                                   String currency) {
        return new PortOnePayment("pay_1", "tx_1", status, amount, currency, paidAt, orderId, null);
    }
}
