package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.config.MemberRefundRecoveryProperties;
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
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    private static final String SERVER_KEY = "recipick-refund-55";
    private static final String REASON = "단순 변심";

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
        // 운영 기본과 같이 자동 POST는 꺼 두고 조립한다. 켜는 테스트는 enableAutoRepost()로 다시 조립한다
        facade = facadeWith(new MemberRefundRecoveryProperties(false));
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
    @DisplayName("취소 ID도 없고 서버가 요청한 취소도 아직 없으면 자동 POST 스위치가 꺼진 기본 상태에서는 POST 없이 다시 예약만 한다")
    void rescheduleWhenNoCandidateYet() {
        // given — POST할 수 있는 조건이 다 맞아도 스위치가 꺼져 있다
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        assertNoPost();
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

    // ---------- 자동 POST: 취소 API의 TX1 뒤 POST가 나가지 못한 환불 ----------

    @Test
    @DisplayName("스위치가 켜져 있고 PortOne에 이 환불의 취소가 없으면 같은 서버 키로 POST 1회를 보내 apply하고 RESOLVED")
    void autoRepostWhenCancellationNeverReachedPortOne() {
        // given — PAID 그대로, 취소 내역·누적 취소액 0, 요청 10분 전
        enableAutoRepost();
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());
        PortOneCancelResult succeeded =
            new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, "cancel_recovered", CANCELED_AT, "SUCCEEDED");
        given(portOnePaymentClient.cancelPayment(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(succeeded);
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, succeeded))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then — 클라이언트 키가 아니라 환불 행의 서버 키, 회원(CUSTOMER) 취소 메서드다
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verify(portOnePaymentClient).cancelPayment(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("POST 결과가 불명(UNCONFIRMED)이면 apply가 PENDING으로 두고 10분 뒤 다시 예약한다")
    void rescheduleWhenRepostOutcomeUnconfirmed() {
        // given
        enableAutoRepost();
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());
        PortOneCancelResult unconfirmed = PortOneCancelResult.unconfirmed("timeout");
        given(portOnePaymentClient.cancelPayment(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(unconfirmed);
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, unconfirmed))
            .willReturn(applied(PaymentCancelOutcome.PENDING, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("POST를 PortOne이 거절(REJECTED)하면 apply가 PAID로 되돌리고 RESOLVED — 거절은 다시 보내지 않는다")
    void resolveWhenRepostRejected() {
        // given
        enableAutoRepost();
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());
        PortOneCancelResult rejected = PortOneCancelResult.fromError(400, null);
        given(portOnePaymentClient.cancelPayment(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(rejected);
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, rejected))
            .willReturn(applied(PaymentCancelOutcome.REQUEST_REJECTED, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentCancelOutcome.class, names = {"PG_FAILED", "REQUEST_REJECTED"})
    @DisplayName("POST는 성공했는데 잠금 사이에 회원 환불이 실패로 끝나 apply가 실패 결과를 주면 MANUAL_REVIEW")
    void manualReviewWhenRepostSucceededButRefundFailed(PaymentCancelOutcome failedOutcome) {
        // given
        enableAutoRepost();
        Refund refund = givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());
        PortOneCancelResult succeeded =
            new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, "cancel_recovered", CANCELED_AT, "SUCCEEDED");
        given(portOnePaymentClient.cancelPayment(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(succeeded);
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, succeeded))
            .willReturn(applied(failedOutcome, refund));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentDelayQueue);
    }

    @ParameterizedTest
    @ValueSource(ints = {175, 180, 240})
    @DisplayName("요청 뒤 2시간 55분부터는 PortOne에 취소가 없어도 POST하지 않는다 — 멱등 보장 경계에서는 같은 키도 새 취소가 될 수 있다")
    void noPostAtOrAfterAutoRepostDeadline(int minutesSinceRequest) {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(minutesSinceRequest));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then — 3시간 전이면 다시 예약, 3시간째부터는 STALLED
        assertThat(outcome).isEqualTo(minutesSinceRequest < 180
            ? RefundReconcileOutcome.RESCHEDULED : RefundReconcileOutcome.STALLED);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("금액이 다른 서버 API 취소가 있으면 그것을 이 환불의 취소로 보고 금액 불일치 MANUAL_REVIEW — 취소 없음으로 보고 POST하지 않는다")
    void noPostWhenApiCancellationHasDifferentAmount() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        PortOneCancellation partial = cancellation(
            "cancel_partial", PortOneCancellationStatus.SUCCEEDED, 1000L, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PARTIAL_CANCELED, AMOUNT, 1000L, partial));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("같은 결제의 다른 환불에 묶인 서버 API 취소가 살아 있으면 POST하지 않고 MANUAL_REVIEW")
    void noPostWhenApiCancellationBoundToAnotherRefund() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        PortOneCancellation other = cancellation(
            "bound_other", PortOneCancellationStatus.REQUESTED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(paymentService.findBoundCancellationIds(PAYMENT_ID, REFUND_ID)).willReturn(Set.of("bound_other"));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, AMOUNT, 0L, other));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("PG 누적 취소액이 0이 아니면 POST하지 않고 MANUAL_REVIEW — 이미 돌려준 돈이 있다")
    void noPostWhenCancelledAmountIsNotZero() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, AMOUNT, 1000L));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("PG 응답에 누적 취소액이 없으면 POST하지 않고 다시 예약한다 — 취소된 돈이 없다고 단정할 수 없다")
    void noPostWhenCancelledAmountIsMissing() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("PG 통화가 KRW가 아니면 POST하지 않고 MANUAL_REVIEW")
    void noPostForForeignCurrency() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(new PortOnePayment(
            PORTONE_PAYMENT_ID, "tx_1", PortOnePaymentStatus.PAID, AMOUNT, "USD", NOW.minusHours(1), 1L, null,
            List.of(), "test-store-id", 0L));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("PG 결제의 customData 주문 ID가 이 결제의 주문과 다르면 POST하지 않고 MANUAL_REVIEW")
    void noPostWhenPgOrderIdMismatch() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(new PortOnePayment(
            PORTONE_PAYMENT_ID, "tx_1", PortOnePaymentStatus.PAID, AMOUNT, "KRW", NOW.minusHours(1), 2L, null,
            List.of(), "test-store-id", 0L));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("PG 결제가 PAID가 아닌데 이 환불의 취소도 없으면 POST하지 않고 MANUAL_REVIEW")
    void noPostWhenPgPaymentIsNotPaid() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, AMOUNT, 0L));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("PortOne 조회 중에 다른 경로가 환불을 끝냈으면 POST 직전 재조회에서 멈추고 RESOLVED — apply도 부르지 않는다")
    void noPostWhenRefundFinishedDuringLookup() {
        // given
        enableAutoRepost();
        Refund snapshot = memberRefund(null, NOW.minusMinutes(10));
        Refund finished = memberRefund(null, NOW.minusMinutes(10));
        finished.complete("cancel_by_webhook", CANCELED_AT);
        given(paymentService.findRefundWithPayment(REFUND_ID))
            .willReturn(Optional.of(snapshot), Optional.of(finished));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("POST 직전 재조회에서 취소 ID가 생겨 있으면 POST하지 않고 다시 예약한다 — 다음 재확인이 그 ID로 찾는다")
    void noPostWhenCancellationIdAppearedBeforePost() {
        // given
        enableAutoRepost();
        Refund snapshot = memberRefund(null, NOW.minusMinutes(10));
        Refund withId = memberRefund(CANCELLATION_ID, NOW.minusMinutes(10));
        given(paymentService.findRefundWithPayment(REFUND_ID))
            .willReturn(Optional.of(snapshot), Optional.of(withId));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("PG 응답에 원수납액이 없으면 POST하지 않고 다시 예약한다 — 금액 근거가 없으면 모순으로 단정하지 않는다")
    void noPostWhenPgTotalAmountIsMissing() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, null, 0L));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("PG 응답에 통화가 없으면 POST하지 않고 다시 예약한다")
    void noPostWhenPgCurrencyIsMissing() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(new PortOnePayment(
            PORTONE_PAYMENT_ID, "tx_1", PortOnePaymentStatus.PAID, AMOUNT, null, NOW.minusHours(1), 1L, null,
            List.of(), "test-store-id", 0L));

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(REFUND_ID), RECHECK_AT);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("POST 직전 재조회의 결제가 조회를 시작할 때와 다른 PG 결제 ID면 POST하지 않고 MANUAL_REVIEW")
    void noPostWhenPaymentIdentityChangedBeforePost() {
        // given — 처음 읽은 환불과 두 번째 읽은 환불이 서로 다른 결제 객체에 묶여 있다
        enableAutoRepost();
        Refund snapshot = memberRefund(null, NOW.minusMinutes(10));
        Refund onOtherPayment = Refund.requestedByMember(
            cancelRequestedPayment("pay_other"), REASON, "client-key-1", NOW.minusMinutes(10));
        ReflectionTestUtils.setField(onOtherPayment, "id", REFUND_ID);
        given(paymentService.findRefundWithPayment(REFUND_ID))
            .willReturn(Optional.of(snapshot), Optional.of(onOtherPayment));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("카드 결제가 아닌 결제의 회원 환불이면 POST하지 않고 MANUAL_REVIEW — 이 경로에 올 수 없는 데이터다")
    void noPostForNonCardPayment() {
        // given
        enableAutoRepost();
        Payment pointsOnly = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.POINTS_ONLY)
            .portonePaymentId(PORTONE_PAYMENT_ID)
            .amount(AMOUNT)
            .status(PaymentStatus.CANCEL_REQUESTED)
            .expiresAt(NOW.minusHours(1))
            .build();
        ReflectionTestUtils.setField(pointsOnly, "id", PAYMENT_ID);
        Refund refund = Refund.requestedByMember(pointsOnly, REASON, "client-key-1", NOW.minusMinutes(10));
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(refund));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("환불 최초 접수 시각이 미래면 POST하지 않고 다시 예약한다 — 시계가 어긋난 데이터로 시간 창을 재지 않는다")
    void noPostWhenRequestedAtIsInFuture() {
        // given
        enableAutoRepost();
        givenRefund(null, NOW.plusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(paidWithoutCancellation());

        // when
        RefundReconcileOutcome outcome = facade.reconcile(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        assertNoPost();
        verifyNoInteractions(paymentCancelProcessor);
    }

    // ---------- helpers ----------

    private RefundReconciliationFacade facadeWith(MemberRefundRecoveryProperties properties) {
        return new RefundReconciliationFacade(paymentService, portOnePaymentClient, paymentCancelProcessor,
            paymentDelayQueue, Clock.fixed(NOW_INSTANT, SEOUL), properties);
    }

    private void enableAutoRepost() {
        facade = facadeWith(new MemberRefundRecoveryProperties(true));
    }

    private void assertNoPost() {
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    // PG에는 결제가 PAID 그대로고 취소 내역이 없다. 누적 취소액 0은 PortOne 응답의 amount.cancelled다
    private static PortOnePayment paidWithoutCancellation() {
        return portOnePayment(PortOnePaymentStatus.PAID, AMOUNT, 0L);
    }

    // 상점 ID·누적 취소액까지 채운 조회 결과. 자동 POST 판정은 누적 취소액을 본다
    private static PortOnePayment portOnePayment(PortOnePaymentStatus status, Long totalAmount, Long cancelledAmount,
                                                 PortOneCancellation... cancellations) {
        return new PortOnePayment(PORTONE_PAYMENT_ID, "tx_1", status, totalAmount, "KRW", NOW.minusHours(1), 1L, null,
            List.of(cancellations), "test-store-id", cancelledAmount);
    }

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
        return cancelRequestedPayment(PORTONE_PAYMENT_ID);
    }

    private static Payment cancelRequestedPayment(String portonePaymentId) {
        Payment created = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(portonePaymentId)
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
