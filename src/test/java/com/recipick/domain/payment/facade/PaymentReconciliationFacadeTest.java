package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.WebhookEventService;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.PortOneWebhookParser;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

// 판정 행렬을 본다. TX 경계·처리권 검증은 WebhookEventServiceTest, 결제·환불 상태 전이는 기존 Processor 테스트가 맡는다.
// 본문 파서는 실제 객체를 쓴다 — 저장된 원문에서 취소 ID를 꺼내는 것까지가 이 Facade의 일이다.
// 결과 기록(mark*)은 처리권이 맞으면 true를 돌려주므로 테스트마다 기대하는 기록 하나만 true로 정해 둔다
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationFacadeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW_INSTANT = Instant.parse("2026-10-26T04:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, SEOUL);
    private static final LocalDateTime CANCELED_AT = NOW.minusMinutes(1);
    private static final Instant SYSTEM_RECHECK_AT = NOW_INSTANT.plus(Duration.ofMinutes(10));
    private static final Long EVENT_ID = 100L;
    private static final int ATTEMPT = 1;
    private static final Long PAYMENT_ID = 10L;
    private static final Long ORDER_ID = 1L;
    private static final Long REFUND_ID = 55L;
    private static final Long AMOUNT = 23800L;
    private static final String PORTONE_PAYMENT_ID = "pay_1";
    private static final String CANCELLATION_ID = "cancel_1";
    private static final String PAID = "Transaction.Paid";
    private static final String CANCELED = "Transaction.Cancelled";
    private static final String CANCEL_PENDING = "Transaction.CancelPending";

    @Mock
    private WebhookEventService webhookEventService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PortOnePaymentClient portOnePaymentClient;

    @Mock
    private PaymentConfirmProcessor paymentConfirmProcessor;

    @Mock
    private PaymentCancelProcessor paymentCancelProcessor;

    @Mock
    private SystemRefundProcessor systemRefundProcessor;

    @Mock
    private SystemRefundExecutionFacade systemRefundExecutionFacade;

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    private PaymentReconciliationFacade facade;

    private Payment payment;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW_INSTANT, SEOUL);
        facade = new PaymentReconciliationFacade(webhookEventService, paymentService, portOnePaymentClient,
            new PortOneWebhookParser(), paymentConfirmProcessor, paymentCancelProcessor, systemRefundProcessor,
            systemRefundExecutionFacade, paymentDelayQueue, fixedClock);
        // Facade는 결제 상태를 읽지 않는다. 회원 환불 행을 붙일 수 있는 상태(CANCEL_REQUESTED)로 하나만 만들어 공유한다
        payment = payment(PAYMENT_ID, ORDER_ID);
    }

    // ---------- claim · 처리권 ----------

    @Test
    @DisplayName("잡지 못한 이벤트(끝났거나 처리 중이거나 없음)는 SKIPPED — PortOne도 DB도 건드리지 않는다")
    void skipWhenNotClaimed() {
        // given
        given(webhookEventService.claim(EVENT_ID, NOW, PaymentReconciliationFacade.PROCESSING_TIMEOUT))
            .willReturn(Optional.empty());

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.SKIPPED);
        verifyNoInteractions(paymentService, portOnePaymentClient, paymentConfirmProcessor, paymentCancelProcessor,
            paymentDelayQueue);
    }

    @Test
    @DisplayName("PortOne 조회는 claim이 돌아온 뒤에만 한다 — claim TX가 커밋돼 잠금이 풀린 상태에서 기다린다")
    void lookUpPortOneOnlyAfterClaim() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        PortOnePayment portOnePayment = portOnePayment(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment)).willReturn(PaymentApprovalOutcome.APPROVED);
        givenProcessedRecorded();

        // when
        facade.processWebhook(EVENT_ID);

        // then — 결과 기록에는 claim 때 받은 처리권 번호를 그대로 넘긴다
        InOrder inOrder = inOrder(webhookEventService, portOnePaymentClient, paymentConfirmProcessor);
        inOrder.verify(webhookEventService).claim(EVENT_ID, NOW, PaymentReconciliationFacade.PROCESSING_TIMEOUT);
        inOrder.verify(portOnePaymentClient).getPayment(PORTONE_PAYMENT_ID);
        inOrder.verify(paymentConfirmProcessor).process(ORDER_ID, portOnePayment);
        inOrder.verify(webhookEventService).markProcessed(EVENT_ID, ATTEMPT, NOW);
    }

    @Test
    @DisplayName("결과를 기록하려는데 처리권이 다른 Worker로 넘어갔으면 SKIPPED — 그쪽 결과를 덮어쓰지 않는다")
    void skipWhenOwnershipLostOnRecord() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        PortOnePayment portOnePayment = portOnePayment(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment)).willReturn(PaymentApprovalOutcome.APPROVED);
        given(webhookEventService.markProcessed(EVENT_ID, ATTEMPT, NOW)).willReturn(false);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.SKIPPED);
    }

    @Test
    @DisplayName("재시도로 기록하려는데 처리권이 넘어갔으면 큐에 다시 예약하지도 않는다 — 새 Worker가 예약을 정한다")
    void noRescheduleWhenOwnershipLostOnRetry() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));
        given(webhookEventService.markRetryPending(EVENT_ID, ATTEMPT, "PortOne 조회 실패: COMMON-009"))
            .willReturn(false);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.SKIPPED);
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- 공통 분기 ----------

    @Test
    @DisplayName("우리 결제가 아닌 PortOne 결제 ID면 IGNORED — PortOne을 묻지 않는다")
    void ignoreWhenPaymentIsNotOurs() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        given(paymentService.findByPortonePaymentId(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());
        given(webhookEventService.markIgnored(eq(EVENT_ID), eq(ATTEMPT), contains("우리 결제가 아님"), eq(NOW)))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.IGNORED);
        verifyNoInteractions(portOnePaymentClient, paymentConfirmProcessor, paymentCancelProcessor);
    }

    @Test
    @DisplayName("부분 취소 웹훅은 PortOne 조회 없이 MANUAL_REVIEW — 전액 취소만 허용하므로 자동 반영 규칙이 없다")
    void manualReviewOnPartialCancel() {
        // given
        givenClaimed(claimedEvent("Transaction.PartialCancelled", "cancel_9"));
        givenOurPayment();
        givenManualReviewRecorded("부분 취소");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(portOnePaymentClient, paymentConfirmProcessor, paymentCancelProcessor);
    }

    // ---------- 재시도 · 예외 분류 ----------

    @Test
    @DisplayName("PortOne 조회가 실패하면 RETRY_PENDING을 적고 1분 뒤로 큐에 다시 예약한다")
    void retryWhenPortOneLookupFails() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));
        given(webhookEventService.markRetryPending(EVENT_ID, ATTEMPT, "PortOne 조회 실패: COMMON-009"))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        verify(paymentDelayQueue).reschedule(
            PaymentDelayTask.webhookProcess(EVENT_ID), NOW_INSTANT.plus(Duration.ofMinutes(1)));
        verifyNoInteractions(paymentConfirmProcessor, paymentCancelProcessor);
    }

    @ParameterizedTest(name = "{0}번째 시도 실패 → {1}분 뒤")
    @CsvSource({"1, 1", "2, 2", "3, 4", "4, 8"})
    @DisplayName("재시도 간격은 시도 횟수마다 두 배로 늘어난다")
    void retryDelayDoubles(int attemptCount, long delayMinutes) {
        // given — PortOne에 아직 없는 결제(404)도 재시도 대상이다
        givenClaimed(claimedEvent(PAID, null, attemptCount));
        givenOurPayment();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        given(webhookEventService.markRetryPending(EVENT_ID, attemptCount, "PortOne 조회 실패: PAYMENT-001"))
            .willReturn(true);

        // when
        facade.processWebhook(EVENT_ID);

        // then
        verify(paymentDelayQueue).reschedule(
            PaymentDelayTask.webhookProcess(EVENT_ID), NOW_INSTANT.plus(Duration.ofMinutes(delayMinutes)));
    }

    @Test
    @DisplayName("최대 시도 횟수에 도달한 실패는 재시도 대신 MANUAL_REVIEW — 같은 웹훅을 무한히 돌리지 않는다")
    void manualReviewWhenMaxAttemptsReached() {
        // given
        int lastAttempt = PaymentReconciliationFacade.MAX_ATTEMPTS;
        givenClaimed(claimedEvent(PAID, null, lastAttempt));
        givenOurPayment();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR));
        given(webhookEventService.markManualReview(
            eq(EVENT_ID), eq(lastAttempt), startsWith("최대 시도 5회 도달"), eq(NOW))).willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verify(webhookEventService, never()).markRetryPending(anyLong(), anyInt(), anyString());
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("큐 예약이 실패해도 RETRY_PENDING은 이미 적혀 있다 — DB가 원본이라 Fallback이 다시 등록한다")
    void retryPendingEvenIfRescheduleFails() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR));
        given(webhookEventService.markRetryPending(EVENT_ID, ATTEMPT, "PortOne 조회 실패: COMMON-008"))
            .willReturn(true);
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).reschedule(any(), any());

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
    }

    @Test
    @DisplayName("락 대기 초과 같은 일시적 DB 장애는 RETRY_PENDING — 다시 하면 풀릴 수 있다")
    void retryOnTransientDataAccessFailure() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        PortOnePayment portOnePayment = portOnePayment(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment))
            .willThrow(new CannotAcquireLockException("lock wait timeout"));
        given(webhookEventService.markRetryPending(EVENT_ID, ATTEMPT, "일시적 DB 장애: CannotAcquireLockException"))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        verify(paymentDelayQueue).reschedule(
            PaymentDelayTask.webhookProcess(EVENT_ID), NOW_INSTANT.plus(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("예상 못한 내부 오류는 재시도하지 않고 바로 MANUAL_REVIEW — 같은 버그를 1·2·4·8분 반복하지 않는다")
    void manualReviewOnUnexpectedException() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        PortOnePayment portOnePayment = portOnePayment(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment))
            .willThrow(new IllegalStateException("예상 못한 상태"));
        givenManualReviewRecorded("예상하지 못한 내부 오류: IllegalStateException");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verify(webhookEventService, never()).markRetryPending(anyLong(), anyInt(), anyString());
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- Paid · Failed → 결제 확정 Processor ----------

    @ParameterizedTest
    @EnumSource(value = PaymentApprovalOutcome.class, names = {
        "APPROVED", "ALREADY_PAID", "PG_FAILED", "AMOUNT_MISMATCH", "EXPIRED", "ALREADY_CLOSED", "INVALID_STATUS"})
    @DisplayName("결제 확정 Processor가 반영을 끝낸 판정은 모두 PROCESSED — 실패 판정도 Processor가 이미 집행했고 환불 실행 예약 여부 판단은 판정과 함께 맡긴다")
    void processedOnFinalApprovalOutcomes(PaymentApprovalOutcome approvalOutcome) {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        PortOnePayment portOnePayment = portOnePayment(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment)).willReturn(approvalOutcome);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(systemRefundExecutionFacade).scheduleAfterConfirm(PAYMENT_ID, approvalOutcome);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("Paid 웹훅인데 PortOne이 아직 PAID가 아니면(NOT_PAID) 반영이 늦는 것으로 보고 재시도한다")
    void retryWhenNotPaidYet() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        PortOnePayment portOnePayment = portOnePayment(PortOnePaymentStatus.PENDING);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment)).willReturn(PaymentApprovalOutcome.NOT_PAID);
        given(webhookEventService.markRetryPending(EVENT_ID, ATTEMPT, "PortOne 결제 미확정: status=PENDING"))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        verify(paymentDelayQueue).reschedule(
            PaymentDelayTask.webhookProcess(EVENT_ID), NOW_INSTANT.plus(Duration.ofMinutes(1)));
        verify(systemRefundExecutionFacade).scheduleAfterConfirm(PAYMENT_ID, PaymentApprovalOutcome.NOT_PAID);
    }

    @Test
    @DisplayName("customData 주문 ID 불일치(ORDER_MISMATCH)는 자동으로 고칠 수 없어 MANUAL_REVIEW")
    void manualReviewOnOrderMismatch() {
        // given
        givenClaimed(claimedEvent(PAID, null));
        givenOurPayment();
        PortOnePayment portOnePayment = portOnePayment(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment))
            .willReturn(PaymentApprovalOutcome.ORDER_MISMATCH);
        givenManualReviewRecorded("주문 ID 불일치");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentDelayQueue);
        verify(systemRefundExecutionFacade).scheduleAfterConfirm(PAYMENT_ID, PaymentApprovalOutcome.ORDER_MISMATCH);
    }

    // ---------- Cancelled · CancelPending → 환불 찾기 ----------

    @Test
    @DisplayName("웹훅의 취소 ID로 환불을 찾으면(TX2가 먼저 끝난 경우) 조회한 취소 내역을 apply에 넘기고 PROCESSED")
    void applyWhenRefundFoundByCancellationId() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, CANCELLATION_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentService, never()).findRequestedMemberRefunds(anyLong());
    }

    @Test
    @DisplayName("취소 ID를 아직 못 받은 회원 환불이 정확히 한 건이면(TX2 전에 웹훅이 먼저 온 경우) 그 환불로 apply한다")
    void applyWhenRequestedRefundHasNoCancellationIdYet() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, null);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(refund));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
    }

    @Test
    @DisplayName("대응하는 REQUESTED 환불이 없으면(콘솔 등 외부 취소) 아무것도 바꾸지 않고 MANUAL_REVIEW")
    void manualReviewWhenNoRequestedRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));
        givenManualReviewRecorded("REQUESTED 환불 없음");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("취소 ID 없는 회원 환불이 여러 건이면 어느 것인지 고를 수 없어 MANUAL_REVIEW")
    void manualReviewWhenMultipleRequestedRefunds() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID))
            .willReturn(List.of(memberRefund(payment, null), memberRefund(payment, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED));
        givenManualReviewRecorded("여러 건");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("취소 ID로 찾은 환불이 다른 결제의 것이면 MANUAL_REVIEW")
    void manualReviewWhenRefundBelongsToAnotherPayment() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund otherRefund = memberRefund(payment(11L, 2L), CANCELLATION_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(otherRefund));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED));
        givenManualReviewRecorded("다른 결제");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("환불에 기록된 취소 ID와 웹훅의 취소 ID가 다르면(같은 결제에 취소가 둘) 자동으로 고르지 않고 MANUAL_REVIEW")
    void manualReviewWhenWebhookCancellationIdDiffersFromRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, "cancel_2"));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId("cancel_2")).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID))
            .willReturn(List.of(memberRefund(payment, CANCELLATION_ID)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED));
        givenManualReviewRecorded("환불의 취소 ID와 다름");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    // ---------- 취소 내역 고르기 ----------

    @Test
    @DisplayName("환불이 가리키는 취소 내역이 조회 결과에 아직 없으면 PortOne 반영이 늦는 것으로 보고 재시도한다")
    void retryWhenCancellationNotInLookupYet() {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(memberRefund(payment, CANCELLATION_ID)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID));
        given(webhookEventService.markRetryPending(eq(EVENT_ID), eq(ATTEMPT), contains("취소 내역 없음")))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        verify(paymentDelayQueue).reschedule(
            PaymentDelayTask.webhookProcess(EVENT_ID), NOW_INSTANT.plus(Duration.ofMinutes(1)));
        verifyNoInteractions(paymentCancelProcessor);
    }

    @ParameterizedTest(name = "trigger={0}, 금액={1}")
    @CsvSource({"CONSOLE, 23800", "API, 1000"})
    @DisplayName("취소 ID 없는 환불에 묶으려는 취소가 API 전액 취소가 아니면(콘솔·금액 다름) MANUAL_REVIEW")
    void manualReviewWhenCancellationIsNotOurs(PortOneCancellationTrigger trigger, long amount) {
        // given
        givenClaimed(claimedEvent(CANCELED, "cancel_9"));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId("cancel_9")).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(memberRefund(payment, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_9", PortOneCancellationStatus.SUCCEEDED, amount, CANCELED_AT, trigger)));
        givenManualReviewRecorded("금액·요청 경로가 다른");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("취소 ID가 어디에도 없으면 API 전액 취소가 정확히 한 건일 때 그것으로 apply한다 — 실패한 콘솔 취소는 세지 않는다")
    void matchUniqueApiCancellationWhenNoIds() {
        // given
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        Refund refund = memberRefund(payment, null);
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(refund));
        PortOneCancellation console = cancellation(
            "cancel_x", PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.CONSOLE);
        PortOneCancellation ours = cancellation(
            "cancel_7", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console, ours));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, ours.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentService, never()).findRefundByPortoneCancellationId(anyString());
    }

    @Test
    @DisplayName("취소 ID가 없고 API 전액 취소가 한 건이 아니면 MANUAL_REVIEW")
    void manualReviewWhenNoUniqueApiCancellation() {
        // given
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(memberRefund(payment, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_7", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.API),
            cancellation("cancel_8", PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.API)));
        givenManualReviewRecorded("API 취소 내역이 2건");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("고른 취소 외에 성공한 취소가 또 있으면 apply하지 않고 MANUAL_REVIEW — API 실패 + 콘솔 성공을 PAID로 되돌리지 않는다")
    void manualReviewWhenOtherSucceededCancellationExists() {
        // given — API 취소는 FAILED, 그 뒤 운영자가 콘솔에서 취소해 성공했다
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(memberRefund(payment, CANCELLATION_ID)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.FAILED, AMOUNT, null,
                PortOneCancellationTrigger.API),
            cancellation("cancel_x", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));
        givenManualReviewRecorded("성공한 취소가 또 있음");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("환불에 기록된 취소 ID로 찾았어도 취소 금액이 환불 금액과 다르면 MANUAL_REVIEW — 전액 취소만 허용한다")
    void manualReviewWhenCancellationAmountDiffers() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(memberRefund(payment, CANCELLATION_ID)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PARTIAL_CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, 1000L, CANCELED_AT,
                PortOneCancellationTrigger.API)));
        givenManualReviewRecorded("취소 금액 불일치");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    // ---------- apply 결과 → 웹훅 결과 ----------

    @Test
    @DisplayName("취소가 아직 처리 중(REQUESTED)이면 이 웹훅은 PROCESSED로 끝내고 환불 재확인을 5분 뒤로 예약한다")
    void processedWhenCancellationStillRequested() {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, null);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(refund));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.PENDING, refund));
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then — 이 웹훅은 끝내고, 환불 완료 확인은 5분 뒤 환불 재확인에 넘긴다
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentDelayQueue).enqueueIfEarlier(
            PaymentDelayTask.refundReconcile(REFUND_ID), NOW_INSTANT.plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("환불 재확인 예약이 실패해도 웹훅은 PROCESSED — 환불이 REQUESTED로 남아 Fallback이 다시 넣는다")
    void processedEvenIfRefundReconcileScheduleFails() {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, null);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(refund));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.PENDING, refund));
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(any(), any());
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
    }

    @Test
    @DisplayName("원문 status가 REQUESTED여도 취소 ID가 비어 있으면 PROCESSED가 아니라 MANUAL_REVIEW — ID가 기록되지 않았다")
    void manualReviewWhenRequestedCancellationHasNoId() {
        // given — 취소 ID가 어디에도 없어 API 전액 취소 한 건을 골랐는데 그 취소의 ID가 비어 있다
        givenClaimed(claimedEvent(CANCEL_PENDING, null));
        givenOurPayment();
        Refund refund = memberRefund(payment, null);
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(refund));
        PortOneCancellation cancellation = cancellation(
            "", PortOneCancellationStatus.REQUESTED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        // toCancelResult가 UNCONFIRMED로 바꾸고, apply는 상태를 두고 PENDING으로 답한다
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.PENDING, refund));
        givenManualReviewRecorded("result=UNCONFIRMED");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then — 확정 근거가 없으니 환불 재확인도 예약하지 않는다
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verify(webhookEventService, never()).markProcessed(anyLong(), anyInt(), any());
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("SUCCEEDED인데 취소 시각이 없어 apply가 PENDING으로 두면 자동 확정 근거가 없어 MANUAL_REVIEW")
    void manualReviewWhenSucceededWithoutCanceledAt() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, CANCELLATION_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.PENDING, refund));
        givenManualReviewRecorded("status=SUCCEEDED");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("PortOne이 취소 실패(FAILED)를 명시해 apply가 PAID로 되돌렸으면 반영은 끝난 것이라 PROCESSED")
    void processedWhenPortOneFailedCancellation() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, CANCELLATION_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.PG_FAILED, refund));
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
    }

    // ---------- SYSTEM 환불 → SystemRefundProcessor ----------

    @Test
    @DisplayName("취소 ID로 SYSTEM 환불을 찾고 API 전액 취소가 성공했으면 SystemRefundProcessor로 반영해 PROCESSED — 회원 Processor는 부르지 않는다")
    void applySystemRefundFoundByCancellationId() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        Refund refund = systemRefund(failed, CANCELLATION_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(RefundStatus.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("취소 ID를 아직 못 받은 SYSTEM 환불이 정확히 한 건이면(실행 POST의 응답보다 웹훅이 먼저 온 경우) 그 환불로 반영한다")
    void applySystemRefundWithoutCancellationIdYet() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID)).willReturn(Optional.of(systemRefund(failed, null)));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(RefundStatus.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("PortOne이 SYSTEM 환불 취소 실패를 명시해 Processor가 FAILED로 끝냈으면 PROCESSED이고 다시 예약하지 않는다")
    void processedWhenSystemRefundFailed() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(systemRefund(failed, CANCELLATION_ID)));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(RefundStatus.FAILED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("SYSTEM 환불 취소가 아직 처리 중이면 PROCESSED로 끝내고 SYSTEM 환불 실행을 10분 뒤로 예약한다 — 회원 환불 재확인으로 보내지 않는다")
    void scheduleSystemRefundExecuteWhenCancellationStillRequested() {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(systemRefund(failed, CANCELLATION_ID)));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(RefundStatus.REQUESTED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(REFUND_ID), SYSTEM_RECHECK_AT);
        verify(paymentDelayQueue, never()).enqueueIfEarlier(eq(PaymentDelayTask.refundReconcile(REFUND_ID)), any());
    }

    @Test
    @DisplayName("SUCCEEDED인데 취소 시각이 아직 없어도 취소 ID로 추적할 수 있으면 MANUAL_REVIEW가 아니라 재실행 예약 후 PROCESSED")
    void scheduleSystemRefundExecuteWhenSucceededWithoutCanceledAt() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(systemRefund(failed, CANCELLATION_ID)));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, cancellation));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(RefundStatus.REQUESTED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(REFUND_ID), SYSTEM_RECHECK_AT);
    }

    @Test
    @DisplayName("UNKNOWN 취소라도 ID가 있으면 SYSTEM 환불을 추적할 수 있어 재실행 예약 후 PROCESSED")
    void scheduleSystemRefundExecuteWhenCancellationUnknownWithId() {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(systemRefund(failed, CANCELLATION_ID)));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.UNKNOWN, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(RefundStatus.REQUESTED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(REFUND_ID), SYSTEM_RECHECK_AT);
    }

    @Test
    @DisplayName("고른 취소 내역에 ID가 없으면 SYSTEM 환불을 다시 추적할 근거가 없어 MANUAL_REVIEW이고 예약하지 않는다")
    void manualReviewWhenSystemCancellationHasNoId() {
        // given — 취소 ID가 어디에도 없어 API 전액 취소 한 건을 골랐는데 그 취소의 ID가 비어 있다
        givenClaimed(claimedEvent(CANCEL_PENDING, null));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID)).willReturn(Optional.of(systemRefund(failed, null)));
        PortOneCancellation cancellation = cancellation(
            "", PortOneCancellationStatus.REQUESTED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        // toCancelResult가 ID 없는 UNCONFIRMED로 바꾸고, Processor는 상태를 그대로 둔다
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(RefundStatus.REQUESTED);
        givenManualReviewRecorded("추적할 ID가 없음");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("SYSTEM 환불 재실행 예약이 Redis 오류로 실패해도 웹훅은 PROCESSED — 환불이 REQUESTED로 남아 Fallback이 복구한다")
    void systemRefundProcessedEvenWhenRecheckScheduleFails() {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(systemRefund(failed, CANCELLATION_ID)));
        PortOneCancellation cancellation = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID, cancellation));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, cancellation.toCancelResult()))
            .willReturn(RefundStatus.REQUESTED);
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(any(), any());
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
    }

    @Test
    @DisplayName("SYSTEM 환불인데 결제 스냅샷이 FAILED가 아니면 Processor를 부르지 않고 사유를 남겨 MANUAL_REVIEW")
    void manualReviewWhenSystemRefundPaymentNotFailed() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(systemRefund(payment, CANCELLATION_ID)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.API)));
        givenManualReviewRecorded("FAILED가 아님");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(systemRefundProcessor, paymentCancelProcessor);
    }

    @Test
    @DisplayName("취소 ID 없는 SYSTEM 환불에 콘솔 취소가 들어오면 그 환불과 묶지 않고 MANUAL_REVIEW")
    void manualReviewWhenConsoleCancellationOnSystemRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, "cancel_console"));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId("cancel_console")).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID)).willReturn(Optional.of(systemRefund(failed, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_console", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));
        givenManualReviewRecorded("금액·요청 경로가 다른");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(systemRefundProcessor);
    }

    @Test
    @DisplayName("회원 환불과 SYSTEM 환불이 함께 REQUESTED면 정상 상태가 아니라 어느 것인지 고르지 않고 MANUAL_REVIEW")
    void manualReviewWhenMemberAndSystemRefundsBothRequested() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(memberRefund(payment, null)));
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID))
            .willReturn(Optional.of(systemRefund(payment, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED));
        givenManualReviewRecorded("여러 건");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, systemRefundProcessor);
    }

    @ParameterizedTest
    @EnumSource(value = RefundRequester.class, names = {"EXTERNAL", "ADMIN"})
    @DisplayName("외부·관리자 환불이 취소 ID로 찾히면 자동 반영 규칙이 없어 어느 Processor에도 넘기지 않고 MANUAL_REVIEW")
    void manualReviewWhenRefundIsNotAutoApplicable(RefundRequester requester) {
        // given — 외부·관리자 환불을 만드는 도메인 팩토리가 아직 없다. 방어 분기만 보려고 요청 주체를 바꿔 끼운다
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, CANCELLATION_ID);
        ReflectionTestUtils.setField(refund, "requestedBy", requester);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.API)));
        givenManualReviewRecorded("requestedBy=" + requester);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, systemRefundProcessor);
    }

    // ---------- helpers ----------

    private void givenClaimed(WebhookEvent event) {
        given(webhookEventService.claim(EVENT_ID, NOW, PaymentReconciliationFacade.PROCESSING_TIMEOUT))
            .willReturn(Optional.of(event));
    }

    private void givenOurPayment() {
        given(paymentService.findByPortonePaymentId(PORTONE_PAYMENT_ID)).willReturn(Optional.of(payment));
    }

    // SYSTEM 환불은 실패로 닫힌 결제에만 생긴다. 웹훅이 찾는 결제를 FAILED 상태로 돌려준다
    private Payment givenOurFailedPayment() {
        Payment failed = payment(PAYMENT_ID, ORDER_ID, PaymentStatus.FAILED);
        given(paymentService.findByPortonePaymentId(PORTONE_PAYMENT_ID)).willReturn(Optional.of(failed));
        return failed;
    }

    // 처리권이 그대로라 기록이 받아들여지는 경우. 다른 인자로 부르면 strict stubs가 실패시킨다
    private void givenProcessedRecorded() {
        given(webhookEventService.markProcessed(EVENT_ID, ATTEMPT, NOW)).willReturn(true);
    }

    private void givenManualReviewRecorded(String reasonPart) {
        given(webhookEventService.markManualReview(eq(EVENT_ID), eq(ATTEMPT), contains(reasonPart), eq(NOW)))
            .willReturn(true);
    }

    private WebhookEvent claimedEvent(String type, String cancellationId) {
        return claimedEvent(type, cancellationId, ATTEMPT);
    }

    // claim TX가 돌려준 상태(PROCESSING, 처리권 번호 = attemptCount)의 이벤트. 본문은 수신 때 저장된 원문과 같은 모양이다
    private WebhookEvent claimedEvent(String type, String cancellationId, int attemptCount) {
        WebhookEvent event = WebhookEvent.received("wh_1", type, PORTONE_PAYMENT_ID, payload(type, cancellationId));
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        event.claim(NOW, PaymentReconciliationFacade.PROCESSING_TIMEOUT);
        ReflectionTestUtils.setField(event, "attemptCount", attemptCount);
        return event;
    }

    private static String payload(String type, String cancellationId) {
        String cancellation = cancellationId == null ? "" : ",\"cancellationId\":\"" + cancellationId + "\"";
        return "{\"type\":\"" + type + "\",\"timestamp\":\"2026-10-26T03:00:00Z\","
            + "\"data\":{\"storeId\":\"test-store-id\",\"paymentId\":\"" + PORTONE_PAYMENT_ID + "\","
            + "\"transactionId\":\"tx_1\"" + cancellation + "}}";
    }

    private static PortOnePayment portOnePayment(PortOnePaymentStatus status, PortOneCancellation... cancellations) {
        return new PortOnePayment(PORTONE_PAYMENT_ID, "tx_1", status, AMOUNT, "KRW", NOW.minusMinutes(5), ORDER_ID,
            null, List.of(cancellations));
    }

    private static PortOneCancellation cancellation(String id, PortOneCancellationStatus status, Long amount,
                                                    LocalDateTime canceledAt, PortOneCancellationTrigger trigger) {
        return new PortOneCancellation(id, status, amount, NOW.minusMinutes(2), canceledAt, trigger);
    }

    private static Payment payment(Long paymentId, Long orderId) {
        return payment(paymentId, orderId, PaymentStatus.CANCEL_REQUESTED);
    }

    private static Payment payment(Long paymentId, Long orderId, PaymentStatus status) {
        Payment created = Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(PORTONE_PAYMENT_ID)
            .amount(AMOUNT)
            .status(status)
            .expiresAt(NOW.minusMinutes(10))
            .build();
        ReflectionTestUtils.setField(created, "id", paymentId);
        return created;
    }

    // 취소 API의 TX1이 만든 회원 환불. cancellationId가 있으면 TX2(또는 앞선 웹훅)가 PortOne 취소 ID를 기록한 상태다
    private static Refund memberRefund(Payment owner, String cancellationId) {
        Refund refund = Refund.requestedByMember(owner, "단순 변심", "client-key-1", NOW.minusMinutes(3));
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        if (cancellationId != null) {
            refund.recordRequested(cancellationId);
        }
        return refund;
    }

    // 결제 확정이 만든 SYSTEM 환불. cancellationId가 있으면 실행 Facade(또는 앞선 웹훅)가 PortOne 취소 ID를 기록한 상태다
    private static Refund systemRefund(Payment owner, String cancellationId) {
        Refund refund = Refund.requestedBySystem(owner, AMOUNT, "결제 대기 5분 만료", "system-key-1", NOW.minusMinutes(3));
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        if (cancellationId != null) {
            refund.recordRequested(cancellationId);
        }
        return refund;
    }

    private static PaymentCancelResult applied(PaymentCancelOutcome outcome, Refund refund) {
        return new PaymentCancelResult(outcome, PaymentCancelResponse.afterPay(refund.getPayment(), refund));
    }
}
