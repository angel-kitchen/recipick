package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.RefundReconcileOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

// 환불 재확인의 판정 행렬을 본다. 상태 전이는 PaymentCancelProcessorTest, 조회 계약은 RefundRepositoryTest가 맡는다
@ExtendWith(MockitoExtension.class)
class RefundReconciliationFacadeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW_INSTANT = Instant.parse("2026-10-26T04:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, SEOUL);
    private static final LocalDateTime CANCELED_AT = NOW.minusMinutes(1);
    private static final Instant RECHECK_AT = NOW_INSTANT.plus(Duration.ofMinutes(10));
    private static final Long PAYMENT_ID = 10L;
    private static final Long REFUND_ID = 55L;
    private static final Long AMOUNT = 23800L;
    private static final String PORTONE_PAYMENT_ID = "pay_1";
    private static final String CANCELLATION_ID = "cancel_1";
    private static final String CONSOLE_CANCELLATION_ID = "cancel_console";

    @Mock
    private PaymentService paymentService;

    @Mock
    private PortOnePaymentClient portOnePaymentClient;

    @Mock
    private PaymentCancelProcessor paymentCancelProcessor;

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    private RefundReconciliationFacade facade;

    private Payment payment;

    @BeforeEach
    void setUp() {
        facade = new RefundReconciliationFacade(paymentService, portOnePaymentClient, paymentCancelProcessor,
            paymentDelayQueue, Clock.fixed(NOW_INSTANT, SEOUL));
        payment = cancelRequestedPayment();
    }

    // ---------- 확인하지 않는 경우 ----------

    @Test
    @DisplayName("환불이 없으면 SKIPPED — PortOne을 묻지 않는다")
    void skipWhenRefundMissing() {
        // given
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.empty());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("이미 끝난 환불(COMPLETED)이면 SKIPPED — 웹훅이나 다른 재확인이 먼저 끝냈다")
    void skipWhenRefundFinished() {
        // given
        Refund refund = memberRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        refund.complete(CANCELLATION_ID, CANCELED_AT);
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("SYSTEM 환불이면 SKIPPED — apply는 회원 환불만 받고 SYSTEM 환불은 실행 Facade가 맡는다")
    void skipWhenRefundIsNotMember() {
        // given
        Refund systemRefund = Refund.requestedBySystem(payment, AMOUNT, "결제 금액 불일치", "sys-key", NOW);
        ReflectionTestUtils.setField(systemRefund, "id", REFUND_ID);
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(systemRefund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("최초 환불 조회에서 일시적 DB 장애가 나면 10분 뒤 재확인을 예약한다 — PortOne은 묻지 않는다")
    void rescheduleWhenInitialRefundLookupFails() {
        // given
        given(paymentService.findRefundWithPayment(REFUND_ID))
            .willThrow(new CannotAcquireLockException("lock wait timeout"));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        verifyNoInteractions(portOnePaymentClient, paymentCancelProcessor);
    }

    // ---------- 확정 ----------

    @Test
    @DisplayName("환불의 취소 ID가 조회 결과에서 SUCCEEDED면 apply로 넘기고 RESOLVED — 더 예약하지 않는다")
    void resolveSucceededById() {
        // given
        Refund refund = givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("PortOne이 취소 실패(FAILED)를 명시했으면 apply가 PAID로 되돌리고 RESOLVED")
    void resolveFailedById() {
        // given
        Refund refund = givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.PG_FAILED, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("취소 ID가 없으면 서버가 요청한 전액 취소 한 건으로 apply한다 — 실패한 콘솔 취소는 세지 않는다")
    void resolveUniqueServerCancellationWithoutId() {
        // given
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        PortOneCancellation console = cancellation(
            "cancel_x", PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.CONSOLE);
        PortOneCancellation server = cancellation(
            "cancel_7", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console, server));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, server.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
    }

    // ---------- 다시 예약 ----------

    @Test
    @DisplayName("PortOne이 아직 처리 중(REQUESTED)이면 apply로 취소 ID만 남기고 10분 뒤로 다시 예약한다")
    void rescheduleWhenStillRequested() {
        // given
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.PENDING, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("환불의 취소 ID가 조회 결과에 아직 없으면 apply 없이 다시 예약한다 — PortOne 반영 지연")
    void rescheduleWhenCancellationNotVisibleYet() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("취소 ID도 없고 서버가 요청한 전액 취소도 아직 없으면 재취소 POST 없이 다시 예약만 한다")
    void rescheduleWhenNoCandidateYet() {
        // given
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("PortOne 조회가 실패하면 다시 예약한다")
    void rescheduleWhenPortOneLookupFails() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("락 대기 초과 같은 일시적 DB 장애는 다시 예약한다")
    void rescheduleOnTransientDataAccessFailure() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willThrow(new CannotAcquireLockException("lock wait timeout"));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("다시 예약이 실패해도 예외를 던지지 않는다 — 환불이 REQUESTED로 남아 Fallback이 다시 넣는다")
    void rescheduleFailureIsSwallowed() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID));
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(any(), any());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
    }

    // ---------- 고빈도 재확인 종료 ----------

    @Test
    @DisplayName("요청 뒤 3시간이 되면 미확정이어도 더 예약하지 않고 STALLED — 실패로 확정하지도 않는다")
    void stallAtRecheckWindow() {
        // given — 정확히 3시간째(경계 포함)
        givenRefund(null, NOW.minus(RefundReconciliationFacade.RECHECK_WINDOW));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.STALLED);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("3시간이 지난 뒤 PortOne 조회가 실패해도 STALLED — 다시 예약하지 않는다")
    void stallAfterWindowEvenWhenLookupFails() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusHours(4));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.STALLED);
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- 수동 확인 ----------

    @Test
    @DisplayName("취소 ID가 없는데 서버가 요청한 전액 취소가 여러 건이면 apply하지 않고 MANUAL_REVIEW")
    void manualReviewWhenMultipleCandidates() {
        // given
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_7", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.API),
            cancellation("cancel_8", PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.API)));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("취소 금액이 환불 금액과 다르면 apply하지 않고 MANUAL_REVIEW — 전액 취소만 허용한다")
    void manualReviewWhenAmountDiffers() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PARTIAL_CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, 1000L, CANCELED_AT,
                PortOneCancellationTrigger.API)));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("고른 취소 외에 성공한 취소가 또 있으면 apply하지 않고 MANUAL_REVIEW — API 실패 + 콘솔 성공")
    void manualReviewWhenOtherSucceededCancellationExists() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.FAILED, AMOUNT, null,
                PortOneCancellationTrigger.API),
            cancellation("cancel_x", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("취소 상태를 알 수 없어(UNKNOWN) apply가 PENDING으로 두면 MANUAL_REVIEW — 다시 물어도 같은 답이다")
    void manualReviewWhenResultUnconfirmed() {
        // given
        Refund refund = givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.UNKNOWN, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.PENDING, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("예상 못한 내부 오류는 다시 예약하지 않고 MANUAL_REVIEW")
    void manualReviewOnUnexpectedException() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willThrow(new IllegalStateException("기존 PortOne 취소 ID와 일치하지 않습니다."));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- 외부 전액 취소 ----------

    @Test
    @DisplayName("취소 ID 없는 회원 환불에 콘솔 전액 취소가 끝나 있으면 그 취소로 apply해 RESOLVED — EXTERNAL 환불을 만들지 않는다")
    void resolveConsoleCancellationWithoutId() {
        // given
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        PortOneCancellation console = cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED,
            AMOUNT, CANCELED_AT, PortOneCancellationTrigger.CONSOLE);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, console.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("서버 API 취소가 실패로 끝났고 콘솔 전액 취소가 성공했으면 콘솔 취소로 apply해 RESOLVED")
    void resolveConsoleCancellationBesideFailedApi() {
        // given
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        PortOneCancellation api = cancellation(
            "cancel_api", PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.API);
        PortOneCancellation console = cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED,
            AMOUNT, CANCELED_AT, PortOneCancellationTrigger.CONSOLE);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, api, console));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, console.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("콘솔 취소는 성공했지만 서버 API 취소가 아직 처리 중이면 apply하지 않고 다시 예약한다")
    void rescheduleWhenApiInProgressBesideConsole() {
        // given
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_api", PortOneCancellationStatus.REQUESTED, AMOUNT, null,
                PortOneCancellationTrigger.API),
            cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("콘솔 취소가 아직 처리 중이면 apply하지 않고 다시 예약한다")
    void rescheduleWhenConsoleStillRequested() {
        // given
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID,
            cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, null,
                PortOneCancellationTrigger.CONSOLE)));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("콘솔 취소 금액이 환불 금액과 다르면 apply하지 않고 MANUAL_REVIEW — 전액 취소만 자동 반영한다")
    void manualReviewWhenConsoleAmountDiffers() {
        // given
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, 10000L, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("차지백 취소가 보이면 apply하지 않고 MANUAL_REVIEW — 카드사 분쟁은 운영이 판단한다")
    void manualReviewWhenChargebackActive() {
        // given
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_chargeback", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.CHARGEBACK)));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("받아 둔 서버 API 취소가 성공했어도 같은 결제에 콘솔 취소가 처리 중이면 apply하지 않고 MANUAL_REVIEW — 웹훅과 같은 기준")
    void manualReviewWhenStoredApiHasActiveConsoleBeside() {
        // given
        givenRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.API),
            cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, null,
                PortOneCancellationTrigger.CONSOLE)));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then — 저장된 API 취소 ID를 콘솔 취소로 다시 묶지 않는다
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentCancelOutcome.class, names = {"PG_FAILED", "REQUEST_REJECTED"})
    @DisplayName("콘솔 취소는 성공했는데 잠금 사이에 회원 환불이 실패로 끝나 apply가 실패 결과를 주면 RESOLVED가 아니라 MANUAL_REVIEW")
    void manualReviewWhenMemberRefundFailedDespiteConsoleSucceeded(PaymentCancelOutcome failedOutcome) {
        // given
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        PortOneCancellation console = cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED,
            AMOUNT, CANCELED_AT, PortOneCancellationTrigger.CONSOLE);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, console.toCancelResult()))
            .willReturn(applied(failedOutcome, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- helpers ----------

    private Refund givenRefund(String cancellationId, LocalDateTime requestedAt) {
        Refund refund = memberRefund(cancellationId, requestedAt);
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(refund));
        return refund;
    }

    // 취소 API의 TX1이 만든 회원 환불. cancellationId가 있으면 TX2나 앞선 웹훅이 PortOne 취소 ID를 기록한 상태다
    private Refund memberRefund(String cancellationId, LocalDateTime requestedAt) {
        Refund refund = Refund.requestedByMember(payment, "단순 변심", "client-key-1", requestedAt);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        if (cancellationId != null) {
            refund.recordRequested(cancellationId);
        }
        return refund;
    }

    private static Payment cancelRequestedPayment() {
        Payment created = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(PORTONE_PAYMENT_ID)
            .amount(AMOUNT)
            .status(PaymentStatus.CANCEL_REQUESTED)
            .expiresAt(NOW.minusHours(1))
            .build();
        ReflectionTestUtils.setField(created, "id", PAYMENT_ID);
        return created;
    }

    private static PortOnePayment portOnePayment(PortOnePaymentStatus status, PortOneCancellation... cancellations) {
        return new PortOnePayment(PORTONE_PAYMENT_ID, "tx_1", status, AMOUNT, "KRW", NOW.minusHours(1), 1L, null,
            List.of(cancellations));
    }

    private static PortOneCancellation cancellation(String id, PortOneCancellationStatus status, Long amount,
                                                    LocalDateTime canceledAt, PortOneCancellationTrigger trigger) {
        return new PortOneCancellation(id, status, amount, NOW.minusMinutes(9), canceledAt, trigger);
    }

    private static PaymentCancelResult applied(PaymentCancelOutcome outcome, Refund refund) {
        return new PaymentCancelResult(outcome, PaymentCancelResponse.afterPay(refund.getPayment(), refund));
    }
}
