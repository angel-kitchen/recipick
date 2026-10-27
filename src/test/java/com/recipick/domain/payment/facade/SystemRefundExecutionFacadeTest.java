package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

// SYSTEM 환불 실행의 판정 행렬을 본다. 환불 상태 전이는 SystemRefundProcessorTest, 요청 본문은 PortOnePaymentClientCancelTest가 맡는다
@ExtendWith(MockitoExtension.class)
class SystemRefundExecutionFacadeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW_INSTANT = Instant.parse("2026-10-27T01:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, SEOUL);
    private static final Instant RECHECK_AT = NOW_INSTANT.plus(Duration.ofMinutes(10));
    private static final Long PAYMENT_ID = 10L;
    private static final Long REFUND_ID = 77L;
    private static final Long AMOUNT = 23800L;
    private static final String PORTONE_PAYMENT_ID = "pay_1";
    private static final String SERVER_KEY = "recipick-refund-77";
    private static final String REASON = "결제 대기 5분 만료";
    private static final String CANCELLATION_ID = "cancel_1";

    @Mock
    private PaymentService paymentService;

    @Mock
    private PortOnePaymentClient portOnePaymentClient;

    @Mock
    private SystemRefundProcessor systemRefundProcessor;

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    private SystemRefundExecutionFacade facade;

    @BeforeEach
    void setUp() {
        facade = new SystemRefundExecutionFacade(paymentService, portOnePaymentClient, systemRefundProcessor,
            paymentDelayQueue, Clock.fixed(NOW_INSTANT, SEOUL));
    }

    // ---------- 실행하지 않는 경우 ----------

    @Test
    @DisplayName("환불이 없으면 SKIPPED — PortOne을 묻지 않는다")
    void skipWhenRefundMissing() {
        // given
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.empty());

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, systemRefundProcessor);
    }

    @Test
    @DisplayName("이미 끝난 SYSTEM 환불은 SKIPPED — PortOne을 묻지 않는다")
    void skipWhenAlreadyFinished() {
        // given
        Refund refund = systemRefund(failedPayment(), NOW.minusMinutes(1));
        refund.complete(CANCELLATION_ID, NOW.minusSeconds(30));
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(refund));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, systemRefundProcessor);
    }

    @Test
    @DisplayName("회원 환불이 들어오면 SKIPPED — 등록 쪽 버그라 취소를 요청하지 않는다")
    void skipMemberRefund() {
        // given
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(memberRefund()));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, systemRefundProcessor);
    }

    @Test
    @DisplayName("결제가 FAILED가 아니면 MANUAL_REVIEW — 살아 있는 결제의 돈을 돌려주지 않도록 PortOne을 부르지 않는다")
    void manualReviewWhenPaymentNotFailed() {
        // given
        Refund refund = systemRefund(payment(PaymentStatus.PAID), NOW.minusMinutes(1));
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(refund));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(portOnePaymentClient, systemRefundProcessor, paymentDelayQueue);
    }

    // ---------- 이미 접수된 취소가 보이는 경우: POST하지 않는다 ----------

    @Test
    @DisplayName("서버가 요청한 전액 취소가 이미 성공해 있으면 다시 취소하지 않고 그 결과를 반영해 RESOLVED")
    void applyExistingSucceededCancellation() {
        // given
        Refund refund = givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOne(PortOnePaymentStatus.CANCELED, apiCancellation(PortOneCancellationStatus.SUCCEEDED)));
        given(systemRefundProcessor.apply(eq(PAYMENT_ID), eq(REFUND_ID), any())).willReturn(RefundStatus.COMPLETED);

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        ArgumentCaptor<PortOneCancelResult> result = ArgumentCaptor.forClass(PortOneCancelResult.class);
        verify(systemRefundProcessor).apply(eq(PAYMENT_ID), eq(REFUND_ID), result.capture());
        assertThat(result.getValue().outcome()).isEqualTo(PortOneCancelOutcome.SUCCEEDED);
        assertThat(result.getValue().cancellationId()).isEqualTo(CANCELLATION_ID);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("받아 둔 취소 ID의 취소가 아직 처리 중이면 POST하지 않고 10분 뒤 재실행을 예약한다")
    void recheckWhenKnownCancellationStillRequested() {
        // given
        Refund refund = givenRequestedRefund();
        refund.recordRequested(CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOne(PortOnePaymentStatus.PAID, apiCancellation(PortOneCancellationStatus.REQUESTED)));
        given(systemRefundProcessor.apply(eq(PAYMENT_ID), eq(REFUND_ID), any())).willReturn(RefundStatus.REQUESTED);

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(REFUND_ID), RECHECK_AT);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("받아 둔 취소 ID가 조회 결과에 아직 없으면 POST하지 않고 재실행을 예약한다")
    void recheckWhenKnownCancellationNotVisible() {
        // given
        Refund refund = givenRequestedRefund();
        refund.recordRequested(CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.PAID));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(REFUND_ID), RECHECK_AT);
        verifyNoInteractions(systemRefundProcessor);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    // ---------- 아직 취소가 없는 경우: PAID일 때만 POST ----------

    @Test
    @DisplayName("취소가 없고 PG 결제가 PAID면 ADMIN·서버 키·환불 사유·환불 금액으로 취소를 요청하고 완료되면 RESOLVED")
    void requestCancelWhenNoCancellation() {
        // given
        givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.PAID));
        PortOneCancelResult succeeded = new PortOneCancelResult(
            PortOneCancelOutcome.SUCCEEDED, CANCELLATION_ID, NOW, "cancellation SUCCEEDED");
        given(portOnePaymentClient.cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(succeeded);
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, succeeded)).willReturn(RefundStatus.COMPLETED);

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verify(portOnePaymentClient).cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT);
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("취소 요청 결과를 모르면(timeout 등) 환불이 REQUESTED로 남아 재실행을 예약한다")
    void recheckWhenCancelUnconfirmed() {
        // given
        givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.PAID));
        PortOneCancelResult timeout = PortOneCancelResult.unconfirmed("timeout");
        given(portOnePaymentClient.cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(timeout);
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, timeout)).willReturn(RefundStatus.REQUESTED);

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(REFUND_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("취소 요청이 실패로 확정되면(FAILED·거절) 환불이 끝나 RESOLVED이고 다시 예약하지 않는다")
    void resolvedWhenCancelFailed() {
        // given
        givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.PAID));
        PortOneCancelResult failed = new PortOneCancelResult(
            PortOneCancelOutcome.FAILED, CANCELLATION_ID, null, "cancellation FAILED");
        given(portOnePaymentClient.cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(failed);
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, failed)).willReturn(RefundStatus.FAILED);

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("3시간 넘게 지난 환불이라도 GET으로 취소가 없음을 확인하면 취소를 요청한다 — 첫 실행이 늦었다고 환불을 포기하지 않는다")
    void requestCancelEvenAfterRecheckWindow() {
        // given
        givenRequestedRefund(NOW.minusHours(4));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.PAID));
        PortOneCancelResult succeeded = new PortOneCancelResult(
            PortOneCancelOutcome.SUCCEEDED, CANCELLATION_ID, NOW, "cancellation SUCCEEDED");
        given(portOnePaymentClient.cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(succeeded);
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, succeeded)).willReturn(RefundStatus.COMPLETED);

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
    }

    // ---------- 자동으로 고르지 않는 경우 ----------

    @Test
    @DisplayName("이 환불의 취소가 없는데 PG 결제가 PAID가 아니면 MANUAL_REVIEW — 콘솔 취소 등은 1-C 몫이라 다시 취소하지 않는다")
    void manualReviewWhenPgNotPaidWithoutCancellation() {
        // given
        givenRequestedRefund();
        PortOneCancellation console = new PortOneCancellation("cancel_console", PortOneCancellationStatus.SUCCEEDED,
            AMOUNT, NOW.minusMinutes(2), NOW.minusMinutes(1), PortOneCancellationTrigger.CONSOLE);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOne(PortOnePaymentStatus.CANCELED, console));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(systemRefundProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("콘솔 취소가 처리 중이면 PG 결제가 아직 PAID여도 ADMIN 취소를 중복 요청하지 않는다")
    void manualReviewWhenExternalCancellationIsRequested() {
        // given
        givenRequestedRefund();
        PortOneCancellation consoleRequested = new PortOneCancellation("cancel_console",
            PortOneCancellationStatus.REQUESTED, AMOUNT, NOW.minusMinutes(1), null, PortOneCancellationTrigger.CONSOLE);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOne(PortOnePaymentStatus.PAID, consoleRequested));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(systemRefundProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("기존 API 취소 금액이 SYSTEM 환불 금액과 다르면 새 취소를 요청하지 않고 MANUAL_REVIEW")
    void manualReviewWhenApiCancellationAmountDiffers() {
        // given
        givenRequestedRefund();
        PortOneCancellation differentAmount = new PortOneCancellation(CANCELLATION_ID,
            PortOneCancellationStatus.REQUESTED, AMOUNT - 1000L, NOW.minusMinutes(1), null,
            PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOne(PortOnePaymentStatus.PAID, differentAmount));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(systemRefundProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("서버가 요청한 API 취소가 두 건이면 MANUAL_REVIEW")
    void manualReviewWhenMultipleCandidates() {
        // given
        givenRequestedRefund();
        PortOneCancellation second = new PortOneCancellation("cancel_2", PortOneCancellationStatus.REQUESTED,
            AMOUNT, NOW.minusMinutes(1), null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(
            portOne(PortOnePaymentStatus.PAID, apiCancellation(PortOneCancellationStatus.REQUESTED), second));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(systemRefundProcessor);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("고른 취소 외에 성공한 취소가 또 있으면 MANUAL_REVIEW")
    void manualReviewWhenOtherSucceededCancellation() {
        // given
        Refund refund = givenRequestedRefund();
        refund.recordRequested(CANCELLATION_ID);
        PortOneCancellation console = new PortOneCancellation("cancel_console", PortOneCancellationStatus.SUCCEEDED,
            AMOUNT, NOW.minusMinutes(2), NOW.minusMinutes(1), PortOneCancellationTrigger.CONSOLE);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(
            portOne(PortOnePaymentStatus.CANCELED, apiCancellation(PortOneCancellationStatus.FAILED), console));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(systemRefundProcessor);
    }

    // ---------- 재실행 경계 ----------

    @Test
    @DisplayName("PortOne 조회가 실패하면 POST하지 않고 재실행을 예약한다")
    void recheckWhenPortOneLookupFails() {
        // given
        givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(REFUND_ID), RECHECK_AT);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("결과 반영 중 락 대기가 초과되면 재실행을 예약한다 — 다음 실행은 GET부터 다시 본다")
    void recheckWhenProcessorLockTimesOut() {
        // given
        givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOne(PortOnePaymentStatus.CANCELED, apiCancellation(PortOneCancellationStatus.SUCCEEDED)));
        given(systemRefundProcessor.apply(eq(PAYMENT_ID), eq(REFUND_ID), any()))
            .willThrow(new CannotAcquireLockException("lock wait timeout"));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(REFUND_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("재실행 예약이 Redis 오류로 실패해도 예외를 던지지 않고 RESCHEDULED — 환불이 REQUESTED로 남아 Fallback이 복구한다")
    void rescheduledEvenWhenRedisFails() {
        // given
        Refund refund = givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(any(), any());

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("요청 후 3시간이 지나도 미확정이면 STALLED — 고빈도 재예약을 멈추고 Fallback 주기에 맡긴다")
    void stalledAfterRecheckWindow() {
        // given
        givenRequestedRefund(NOW.minusHours(3));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        // when
        RefundReconcileOutcome outcome = facade.execute(REFUND_ID);

        // then
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.STALLED);
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- 픽스처 ----------

    private Refund givenRequestedRefund() {
        return givenRequestedRefund(NOW.minusMinutes(1));
    }

    private Refund givenRequestedRefund(LocalDateTime requestedAt) {
        Refund refund = systemRefund(failedPayment(), requestedAt);
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(refund));
        return refund;
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(PORTONE_PAYMENT_ID)
            .amount(AMOUNT)
            .status(status)
            .expiresAt(NOW.minusMinutes(10))
            .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    // fail()은 IN_PROGRESS에서만 허용되므로 실제 전이를 거쳐 만든다
    private Payment failedPayment() {
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        payment.fail(PaymentFailureReason.EXPIRED, "tx_1");
        return payment;
    }

    private Refund systemRefund(Payment payment, LocalDateTime requestedAt) {
        Refund refund = Refund.requestedBySystem(payment, AMOUNT, REASON, "system-key-1", requestedAt);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    // requestedByMember는 CANCEL_REQUESTED 결제만 받는다
    private Refund memberRefund() {
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = Refund.requestedByMember(payment, "단순 변심", "client-key-1", NOW.minusMinutes(1));
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    private static PortOneCancellation apiCancellation(PortOneCancellationStatus status) {
        LocalDateTime canceledAt = status == PortOneCancellationStatus.SUCCEEDED ? NOW.minusSeconds(30) : null;
        return new PortOneCancellation(CANCELLATION_ID, status, AMOUNT, NOW.minusMinutes(1), canceledAt,
            PortOneCancellationTrigger.API);
    }

    private static PortOnePayment portOne(PortOnePaymentStatus status, PortOneCancellation... cancellations) {
        return new PortOnePayment(PORTONE_PAYMENT_ID, "tx_1", status, AMOUNT, "KRW", NOW.minusMinutes(6), 1L, null,
            List.of(cancellations));
    }
}
