package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// F-2. 재조회 결과 판정. 모든 분류는 결제 ID 일치가 먼저다. PAID는 paidAt·금액·통화가 다 있어야 믿고,
// KRW인데 금액만 다르면 PAID_AMOUNT_MISMATCH(실제 수납액), 통화가 KRW가 아니거나 필수값이 없으면 UNTRUSTED(환불 금액 없음)
class MembershipPgVerdictTest {

    private static final String PAYMENT_ID = "mpay_0123456789abcdef0123456789abcdef";
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 6, 10, 3);

    @Test
    @DisplayName("ID 일치·PAID·3,900 KRW·paidAt → PAID, 수납액 3,900")
    void paid() {
        MembershipPgVerdict verdict = MembershipPgVerdict.of(PAYMENT_ID, 3_900L, payment(PAYMENT_ID,
            PortOnePaymentStatus.PAID, 3_900L, "KRW", PAID_AT));

        assertThat(verdict.kind()).isEqualTo(MembershipPgVerdict.Kind.PAID);
        assertThat(verdict.pgPaidAt()).isEqualTo(PAID_AT);
        assertThat(verdict.paidAmount()).isEqualTo(3_900L);
        assertThat(verdict.isTrustedPaid()).isTrue();
    }

    @Test
    @DisplayName("KRW인데 금액이 3,800이면 PAID_AMOUNT_MISMATCH이고 실제 수납액 3,800을 돌려준다")
    void krwAmountMismatch() {
        MembershipPgVerdict verdict = MembershipPgVerdict.of(PAYMENT_ID, 3_900L, payment(PAYMENT_ID,
            PortOnePaymentStatus.PAID, 3_800L, "KRW", PAID_AT));

        assertThat(verdict.kind()).isEqualTo(MembershipPgVerdict.Kind.PAID_AMOUNT_MISMATCH);
        assertThat(verdict.paidAmount()).isEqualTo(3_800L);
        assertThat(verdict.pgPaidAt()).isEqualTo(PAID_AT);
        assertThat(verdict.code()).isEqualTo(MembershipPgVerdict.CODE_AMOUNT_MISMATCH);
        assertThat(verdict.isTrustedPaid()).isTrue();
    }

    @Test
    @DisplayName("통화가 KRW가 아니면 금액이 맞아도 UNTRUSTED다 — 환불 금액을 만들지 않는다")
    void foreignCurrencyIsUntrusted() {
        MembershipPgVerdict verdict = MembershipPgVerdict.of(PAYMENT_ID, 3_900L, payment(PAYMENT_ID,
            PortOnePaymentStatus.PAID, 3_900L, "USD", PAID_AT));

        assertThat(verdict.kind()).isEqualTo(MembershipPgVerdict.Kind.UNTRUSTED);
        assertThat(verdict.code()).isEqualTo(MembershipPgVerdict.CODE_UNTRUSTED_CURRENCY);
        assertThat(verdict.paidAmount()).isNull();
        assertThat(verdict.isTrustedPaid()).isFalse();
    }

    @Test
    @DisplayName("PAID인데 paidAt·금액·통화 중 하나라도 없으면 UNTRUSTED다")
    void paidWithoutRequiredFieldsIsUntrusted() {
        assertThat(MembershipPgVerdict.of(PAYMENT_ID, 3_900L,
            payment(PAYMENT_ID, PortOnePaymentStatus.PAID, 3_900L, "KRW", null)).kind())
            .isEqualTo(MembershipPgVerdict.Kind.UNTRUSTED);
        assertThat(MembershipPgVerdict.of(PAYMENT_ID, 3_900L,
            payment(PAYMENT_ID, PortOnePaymentStatus.PAID, null, "KRW", PAID_AT)).kind())
            .isEqualTo(MembershipPgVerdict.Kind.UNTRUSTED);
        assertThat(MembershipPgVerdict.of(PAYMENT_ID, 3_900L,
            payment(PAYMENT_ID, PortOnePaymentStatus.PAID, 3_900L, null, PAID_AT)).kind())
            .isEqualTo(MembershipPgVerdict.Kind.UNTRUSTED);
        assertThat(MembershipPgVerdict.of(PAYMENT_ID, 3_900L,
            payment(PAYMENT_ID, PortOnePaymentStatus.PAID, 0L, "KRW", PAID_AT)).kind())
            .isEqualTo(MembershipPgVerdict.Kind.UNTRUSTED);
    }

    @ParameterizedTest
    @EnumSource(value = PortOnePaymentStatus.class, names = {"PAID", "FAILED", "CANCELED", "PENDING"})
    @DisplayName("결제 ID가 다르면 어떤 상태든(FAILED 포함) UNTRUSTED다")
    void idMismatchIsUntrustedForEveryStatus(PortOnePaymentStatus status) {
        MembershipPgVerdict verdict = MembershipPgVerdict.of(PAYMENT_ID, 3_900L,
            payment("mpay_other", status, 3_900L, "KRW", PAID_AT));

        assertThat(verdict.kind()).isEqualTo(MembershipPgVerdict.Kind.UNTRUSTED);
        assertThat(verdict.code()).isEqualTo(MembershipPgVerdict.CODE_UNTRUSTED_ID);
    }

    @Test
    @DisplayName("ID가 일치하는 FAILED만 실패 확정이고 코드는 PG_FAILED다(failureSummary 문장은 쓰지 않는다)")
    void failed() {
        MembershipPgVerdict verdict = MembershipPgVerdict.of(PAYMENT_ID, 3_900L, new PortOnePayment(PAYMENT_ID, "tx",
            PortOnePaymentStatus.FAILED, 3_900L, "KRW", null, null, "card declined for a@b.c"));

        assertThat(verdict.kind()).isEqualTo(MembershipPgVerdict.Kind.FAILED);
        assertThat(verdict.code()).isEqualTo(MembershipPgVerdict.CODE_PG_FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = PortOnePaymentStatus.class, names = {"READY", "PENDING", "VIRTUAL_ACCOUNT_ISSUED"})
    @DisplayName("진행 중 상태는 UNRESOLVED — 시도 UNKNOWN으로 두고 같은 ID로 재조회한다")
    void inProgressIsUnresolved(PortOnePaymentStatus status) {
        MembershipPgVerdict verdict = MembershipPgVerdict.of(PAYMENT_ID, 3_900L,
            payment(PAYMENT_ID, status, null, null, null));

        assertThat(verdict.kind()).isEqualTo(MembershipPgVerdict.Kind.UNRESOLVED);
        assertThat(verdict.code()).isEqualTo("PG_" + status.name());
    }

    @ParameterizedTest
    @EnumSource(value = PortOnePaymentStatus.class, names = {"CANCELED", "PARTIAL_CANCELED", "UNKNOWN"})
    @DisplayName("우리가 취소한 적 없는 취소 상태·해석 불가 상태는 임의로 FAILED 확정하지 않고 UNTRUSTED다")
    void canceledOrUnknownStatusIsUntrusted(PortOnePaymentStatus status) {
        MembershipPgVerdict verdict = MembershipPgVerdict.of(PAYMENT_ID, 3_900L,
            payment(PAYMENT_ID, status, 3_900L, "KRW", PAID_AT));

        assertThat(verdict.kind()).isEqualTo(MembershipPgVerdict.Kind.UNTRUSTED);
    }

    @Test
    @DisplayName("404는 NOT_FOUND, 조회 오류는 코드만 남긴 UNRESOLVED다")
    void notFoundAndUnresolved() {
        assertThat(MembershipPgVerdict.notFound().kind()).isEqualTo(MembershipPgVerdict.Kind.NOT_FOUND);
        assertThat(MembershipPgVerdict.unresolved("COMMON-009").code()).isEqualTo("COMMON-009");
        assertThat(MembershipPgVerdict.of(PAYMENT_ID, 3_900L, null).kind())
            .isEqualTo(MembershipPgVerdict.Kind.UNRESOLVED);
    }

    private static PortOnePayment payment(String id, PortOnePaymentStatus status, Long amount, String currency,
                                          LocalDateTime paidAt) {
        return new PortOnePayment(id, "tx_1", status, amount, currency, paidAt, null, null);
    }
}
