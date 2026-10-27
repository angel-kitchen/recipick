package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.facade.ExternalCancellationMatch.Type;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RefundCancellationRulesTest {

    private static final Long AMOUNT = 23800L;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 27, 20, 0);
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 27, 21, 0);
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 27, 21, 0, 10);

    // ---------- 외부 취소 경로 ----------

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationTrigger.class, names = {"CONSOLE", "PORTONE_ADMIN"})
    @DisplayName("콘솔·PortOne 관리자 취소만 자동 정합화 경로다")
    void autoReconcilableTrigger(PortOneCancellationTrigger trigger) {
        // when
        boolean result = RefundCancellationRules.isAutoReconcilableExternalTrigger(trigger);

        // then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationTrigger.class, names = {"API", "CHARGEBACK", "UNKNOWN"})
    @DisplayName("서버 API·차지백·알 수 없는 경로는 자동 정합화 경로가 아니다")
    void notAutoReconcilableTrigger(PortOneCancellationTrigger trigger) {
        // when
        boolean result = RefundCancellationRules.isAutoReconcilableExternalTrigger(trigger);

        // then
        assertThat(result).isFalse();
    }

    // ---------- 고른 취소 외의 비API 취소 ----------

    @Test
    @DisplayName("고른 외부 취소 자신은 다른 활성 비API 취소로 세지 않는다")
    void selectedItselfIsNotOtherActiveNonApi() {
        // given
        PortOneCancellation console = succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE);
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED, console);

        // when
        boolean result = RefundCancellationRules.hasOtherActiveNonApiCancellation(portOne, console);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("고른 취소 외에 처리 중인 비API 취소가 있으면 다른 활성 비API 취소가 있다")
    void otherActiveNonApi() {
        // given
        PortOneCancellation console = succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE);
        PortOneCancellation chargeback = requested("cancel_chargeback", PortOneCancellationTrigger.CHARGEBACK);
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED, console, chargeback);

        // when
        boolean result = RefundCancellationRules.hasOtherActiveNonApiCancellation(portOne, console);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("실패로 끝난 비API 취소와 API 취소는 다른 활성 비API 취소로 세지 않는다")
    void failedNonApiAndApiAreNotOtherActiveNonApi() {
        // given
        PortOneCancellation console = succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE);
        PortOneCancellation failedAdmin = failed("cancel_admin", PortOneCancellationTrigger.PORTONE_ADMIN);
        PortOneCancellation api = requested("cancel_api", PortOneCancellationTrigger.API);
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED, console, failedAdmin, api);

        // when
        boolean result = RefundCancellationRules.hasOtherActiveNonApiCancellation(portOne, console);

        // then
        assertThat(result).isFalse();
    }

    // ---------- MATCHED ----------

    @Test
    @DisplayName("PG 결제가 CANCELED이고 콘솔 전액 취소 하나만 성공했으면 그 취소를 고른다")
    void matchedConsoleFullCancellation() {
        // given
        PortOneCancellation console = succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE);
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED, console);

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.MATCHED);
        assertThat(match.cancellation()).isSameAs(console);
    }

    @Test
    @DisplayName("웹훅이 알려 준 PortOne 관리자 취소 ID로 고른다")
    void matchedByHint() {
        // given
        PortOneCancellation admin = succeeded("cancel_admin", PortOneCancellationTrigger.PORTONE_ADMIN);
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED, admin);

        // when
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, "cancel_admin");

        // then
        assertThat(match.type()).isEqualTo(Type.MATCHED);
        assertThat(match.cancellation()).isSameAs(admin);
    }

    @Test
    @DisplayName("서버 API 취소가 실패로 끝났으면 콘솔 전액 취소를 고른다 — 실패한 취소는 결제에 영향이 없다")
    void matchedWhenApiCancellationFailed() {
        // given
        PortOneCancellation api = failed("cancel_api", PortOneCancellationTrigger.API);
        PortOneCancellation console = succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE);
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED, api, console);

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.MATCHED);
        assertThat(match.cancellation()).isSameAs(console);
    }

    // ---------- ABSENT ----------

    @Test
    @DisplayName("취소 내역이 없으면 ABSENT")
    void absentWithoutCancellations() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID);

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.ABSENT);
        assertThat(match.cancellation()).isNull();
    }

    @Test
    @DisplayName("서버 API 취소만 있으면 ABSENT — 기존 API 판정으로 넘긴다")
    void absentWithOnlyApiCancellation() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            succeeded("cancel_api", PortOneCancellationTrigger.API));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.ABSENT);
    }

    @Test
    @DisplayName("웹훅이 알려 준 취소가 서버 API 취소면 ABSENT — 정상 서버 취소를 외부 충돌로 오판하지 않는다")
    void absentWhenHintIsApi() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            succeeded("cancel_api", PortOneCancellationTrigger.API));

        // when
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, "cancel_api");

        // then
        assertThat(match.type()).isEqualTo(Type.ABSENT);
    }

    @Test
    @DisplayName("웹훅이 알려 준 콘솔 취소가 실패로 끝났으면 ABSENT")
    void absentWhenHintFailed() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID,
            failed("cancel_console", PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, "cancel_console");

        // then
        assertThat(match.type()).isEqualTo(Type.ABSENT);
    }

    // ---------- CONFLICT ----------

    @Test
    @DisplayName("웹훅이 알려 준 취소가 서버 API 취소여도 다른 외부 취소가 살아 있으면 CONFLICT — 없음으로 숨기지 않는다")
    void conflictWhenHintIsApiAndOtherExternalActive() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID,
            failed("cancel_api", PortOneCancellationTrigger.API),
            requested("cancel_console", PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, "cancel_api");

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("웹훅이 알려 준 외부 취소가 실패했어도 다른 외부 취소가 살아 있으면 CONFLICT")
    void conflictWhenHintFailedAndOtherExternalActive() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID,
            failed("cancel_console", PortOneCancellationTrigger.CONSOLE),
            requested("cancel_admin", PortOneCancellationTrigger.PORTONE_ADMIN));

        // when
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, "cancel_console");

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationTrigger.class, names = {"CHARGEBACK", "UNKNOWN"})
    @DisplayName("웹훅이 알려 준 취소가 차지백·알 수 없는 경로면 CONFLICT")
    void conflictWhenHintIsNotAutoReconcilable(PortOneCancellationTrigger trigger) {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED, succeeded("cancel_x", trigger));

        // when
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, "cancel_x");

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("콘솔·관리자 취소는 없지만 차지백이 살아 있으면 ABSENT가 아니라 CONFLICT")
    void conflictWhenOnlyChargebackActive() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            succeeded("cancel_chargeback", PortOneCancellationTrigger.CHARGEBACK));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("살아 있는 외부 취소가 여러 건이면 CONFLICT")
    void conflictWhenMultipleExternalCancellations() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE),
            requested("cancel_admin", PortOneCancellationTrigger.PORTONE_ADMIN));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("외부 취소에 ID가 없으면 CONFLICT")
    void conflictWithoutCancellationId() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            new PortOneCancellation(null, PortOneCancellationStatus.SUCCEEDED, AMOUNT, REQUESTED_AT, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("외부 취소 금액이 기대 금액과 다르면 재조회하지 않고 바로 CONFLICT")
    void conflictWhenAmountMismatch() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            new PortOneCancellation("cancel_console", PortOneCancellationStatus.SUCCEEDED, 10000L, REQUESTED_AT,
                CANCELED_AT, PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("고른 외부 취소 외에 성공한 서버 API 취소가 있으면 CONFLICT — 성공 취소가 둘이다")
    void conflictWhenOtherSucceeded() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            succeeded("cancel_api", PortOneCancellationTrigger.API),
            succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("고른 외부 취소 외에 처리 중인 비API 취소가 있으면 CONFLICT")
    void conflictWhenOtherActiveNonApi() {
        // given — 콘솔 취소는 성공했지만 차지백이 진행 중이라 결제 전체의 최종 상태가 모호하다
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE),
            requested("cancel_chargeback", PortOneCancellationTrigger.CHARGEBACK));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("웹훅으로 고른 외부 취소 외에 처리 중인 다른 외부 취소가 있으면 CONFLICT")
    void conflictWhenHintAndOtherActiveExternal() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE),
            requested("cancel_admin", PortOneCancellationTrigger.PORTONE_ADMIN));

        // when
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, "cancel_console");

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    @Test
    @DisplayName("PG 결제가 부분 취소 상태면 CONFLICT")
    void conflictWhenPartialCanceled() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PARTIAL_CANCELED,
            succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.CONFLICT);
    }

    // ---------- PENDING ----------

    @Test
    @DisplayName("웹훅이 알려 준 취소가 조회 결과에 아직 없으면 PENDING")
    void pendingWhenHintNotFound() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID);

        // when
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, "cancel_console");

        // then
        assertThat(match.type()).isEqualTo(Type.PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationStatus.class, names = {"REQUESTED", "UNKNOWN"})
    @DisplayName("외부 취소가 처리 중이거나 상태를 알 수 없으면 PENDING")
    void pendingWhenExternalNotFinished(PortOneCancellationStatus status) {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID,
            new PortOneCancellation("cancel_console", status, AMOUNT, REQUESTED_AT, null,
                PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.PENDING);
    }

    @Test
    @DisplayName("외부 취소가 성공인데 완료 시각이 없으면 PENDING — 서버 시각으로 채우지 않는다")
    void pendingWithoutCanceledAt() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            new PortOneCancellation("cancel_console", PortOneCancellationStatus.SUCCEEDED, AMOUNT, REQUESTED_AT,
                null, PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationStatus.class, names = {"REQUESTED", "UNKNOWN"})
    @DisplayName("외부 취소는 성공했지만 서버 API 취소가 처리 중이면 PENDING — 곧 결과가 정해진다")
    void pendingWhenApiInProgress(PortOneCancellationStatus apiStatus) {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.CANCELED,
            new PortOneCancellation("cancel_api", apiStatus, AMOUNT, REQUESTED_AT, null,
                PortOneCancellationTrigger.API),
            succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.PENDING);
    }

    @Test
    @DisplayName("외부 취소는 성공했지만 PG 결제가 아직 PAID면 PENDING — PortOne 반영 시점 차이")
    void pendingWhenPgStillPaid() {
        // given
        PortOnePayment portOne = portOne(PortOnePaymentStatus.PAID,
            succeeded("cancel_console", PortOneCancellationTrigger.CONSOLE));

        // when
        ExternalCancellationMatch match = RefundCancellationRules.matchExternalFullCancellation(portOne, AMOUNT, null);

        // then
        assertThat(match.type()).isEqualTo(Type.PENDING);
    }

    // ---------- 준비 ----------

    private static PortOneCancellation succeeded(String id, PortOneCancellationTrigger trigger) {
        return new PortOneCancellation(id, PortOneCancellationStatus.SUCCEEDED, AMOUNT, REQUESTED_AT, CANCELED_AT,
            trigger);
    }

    private static PortOneCancellation requested(String id, PortOneCancellationTrigger trigger) {
        return new PortOneCancellation(id, PortOneCancellationStatus.REQUESTED, AMOUNT, REQUESTED_AT, null, trigger);
    }

    private static PortOneCancellation failed(String id, PortOneCancellationTrigger trigger) {
        return new PortOneCancellation(id, PortOneCancellationStatus.FAILED, AMOUNT, REQUESTED_AT, null, trigger);
    }

    private static PortOnePayment portOne(PortOnePaymentStatus status, PortOneCancellation... cancellations) {
        return new PortOnePayment("pay_1", "tx_1", status, AMOUNT, "KRW", PAID_AT, 1L, null,
            List.of(cancellations));
    }
}
