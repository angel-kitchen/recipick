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

import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.ExternalCancellationResult;
import com.recipick.domain.payment.service.MartRejectionOutcome;
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

import com.recipick.infra.portone.dto.PortOneCancelResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
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
    private static final Instant ADMIN_RECHECK_AT = NOW_INSTANT.plus(Duration.ofMinutes(10));
    private static final LocalDateTime ADMIN_REQUESTED_AT = NOW.minusMinutes(3);
    private static final LocalDateTime BEFORE_ADMIN_REQUEST = ADMIN_REQUESTED_AT.minusDays(1);
    private static final Long EVENT_ID = 100L;
    private static final int ATTEMPT = 1;
    private static final Long PAYMENT_ID = 10L;
    private static final Long ORDER_ID = 1L;
    private static final Long REFUND_ID = 55L;
    private static final Long AMOUNT = 23800L;
    private static final String PORTONE_PAYMENT_ID = "pay_1";
    private static final String CANCELLATION_ID = "cancel_1";
    private static final String CONSOLE_CANCELLATION_ID = "cancel_console";
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
    private MartRejectionProcessor martRejectionProcessor;

    @Mock
    private ExternalCancellationProcessor externalCancellationProcessor;

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    private PaymentReconciliationFacade facade;

    private Payment payment;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW_INSTANT, SEOUL);
        facade = new PaymentReconciliationFacade(webhookEventService, paymentService, portOnePaymentClient,
            new PortOneWebhookParser(), paymentConfirmProcessor, paymentCancelProcessor, systemRefundProcessor,
            systemRefundExecutionFacade, martRejectionProcessor, externalCancellationProcessor, paymentDelayQueue,
            fixedClock);
        // 회원 환불 행을 붙일 수 있는 상태(CANCEL_REQUESTED)로 하나만 만들어 공유한다.
        // 결제 상태가 판정에 쓰이는 테스트는 givenOurFailedPayment·givenOurPaymentIn으로 따로 만든다
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
    @DisplayName("대응 환불이 없는데 결제가 PAID가 아니면 외부 취소로도 반영하지 않고 MANUAL_REVIEW")
    void manualReviewWhenNoRefundAndPaymentNotPaid() {
        // given — 결제는 취소 진행 중(CANCEL_REQUESTED)인데 환불 행이 없는 이상 상태
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));
        givenManualReviewRecorded("결제가 PAID가 아니라");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, externalCancellationProcessor);
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

        // then — 저장된 취소 ID를 외부 취소 ID로 다시 묶지 않는다
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, externalCancellationProcessor);
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

    @Test
    @DisplayName("취소 ID 없는 환불에 묶으려는 API 취소의 금액이 환불과 다르면 MANUAL_REVIEW")
    void manualReviewWhenCancellationIsNotOurs() {
        // given
        givenClaimed(claimedEvent(CANCELED, "cancel_9"));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId("cancel_9")).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(memberRefund(payment, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_9", PortOneCancellationStatus.SUCCEEDED, 1000L, CANCELED_AT,
                PortOneCancellationTrigger.API)));
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
        givenManualReviewRecorded("별도의 비API 취소");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then — 저장된 API 취소 ID를 콘솔 취소로 다시 묶지 않는다
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, externalCancellationProcessor);
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
    @DisplayName("SYSTEM 환불의 API 취소가 성공했어도 같은 결제에 콘솔 취소가 처리 중이면 Processor를 부르지 않고 MANUAL_REVIEW — 실행 Facade와 같은 기준")
    void manualReviewWhenNonApiCancellationActiveOnSystemRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(systemRefund(failed, CANCELLATION_ID)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.API),
            cancellation("cancel_console", PortOneCancellationStatus.REQUESTED, AMOUNT, null,
                PortOneCancellationTrigger.CONSOLE)));
        givenManualReviewRecorded("별도의 비API 취소");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(systemRefundProcessor, paymentCancelProcessor, externalCancellationProcessor,
            paymentDelayQueue);
    }

    @Test
    @DisplayName("곁에 있는 콘솔 취소가 실패로 끝난 시도면 막지 않고 SYSTEM 환불을 반영해 PROCESSED")
    void applySystemRefundWhenNonApiCancellationFailed() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID))
            .willReturn(Optional.of(systemRefund(failed, CANCELLATION_ID)));
        PortOneCancellation api = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED, api,
            cancellation("cancel_console", PortOneCancellationStatus.FAILED, AMOUNT, null,
                PortOneCancellationTrigger.CONSOLE)));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, api.toCancelResult()))
            .willReturn(RefundStatus.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentCancelProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("취소 ID 없는 SYSTEM 환불에 콘솔 전액 취소가 들어오면 그 취소로 SYSTEM 환불을 완료해 PROCESSED — PortOne에 취소를 새로 요청하지 않는다")
    void applySystemRefundWithConsoleCancellation() {
        // given — 결제 확정이 만든 SYSTEM 환불을 실행하기 전에 운영자가 콘솔에서 먼저 돌려줬다
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID)).willReturn(Optional.of(systemRefund(failed, null)));
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, console.toCancelResult()))
            .willReturn(RefundStatus.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then — 결제 FAILED·주문 CANCELED는 SystemRefundProcessor가 그대로 둔다
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(paymentCancelProcessor, externalCancellationProcessor, paymentDelayQueue);
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
    @EnumSource(value = RefundRequester.class, names = {"EXTERNAL"})
    @DisplayName("외부 환불이 취소 ID로 찾히면 자동 반영 규칙이 없어 어느 Processor에도 넘기지 않고 MANUAL_REVIEW")
    void manualReviewWhenRefundIsNotAutoApplicable(RefundRequester requester) {
        // given — 서버 API 취소에 묶인 외부 환불은 정상 흐름에 없다. 방어 분기만 보려고 요청 주체를 바꿔 끼운다
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
        verifyNoInteractions(paymentCancelProcessor, systemRefundProcessor, martRejectionProcessor,
            externalCancellationProcessor);
    }

    // ---------- 외부 취소 — 대응 환불 없음 ----------

    @Test
    @DisplayName("대응 환불이 없는 PAID 결제의 콘솔 전액 취소는 외부 취소 Processor로 반영해 PROCESSED — 회원·SYSTEM Processor는 부르지 않는다")
    void reconcileExternalCancellationWithoutRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.PAID);
        givenNoRefund();
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(externalCancellationProcessor.apply(PAYMENT_ID, console))
            .willReturn(ExternalCancellationResult.reconciled());
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentCancelProcessor, systemRefundProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("외부 취소 Processor가 이미 반영됐다고 하면 PROCESSED")
    void processedWhenExternalCancellationAlreadyReconciled() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.PAID);
        givenNoRefund();
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(externalCancellationProcessor.apply(PAYMENT_ID, console))
            .willReturn(ExternalCancellationResult.alreadyReconciled());
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
    }

    @Test
    @DisplayName("외부 취소 Processor가 RETRY면 사유를 남겨 RETRY_PENDING으로 적고 1분 뒤로 다시 예약한다")
    void retryWhenExternalCancellationProcessorRetries() {
        // given — 판정과 잠금 사이에 회원 취소가 시작돼 기존 환불로 마무리돼야 하는 경우
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.PAID);
        givenNoRefund();
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(externalCancellationProcessor.apply(PAYMENT_ID, console))
            .willReturn(ExternalCancellationResult.retry("기존 환불로 연결"));
        given(webhookEventService.markRetryPending(eq(EVENT_ID), eq(ATTEMPT), contains("기존 환불로 연결")))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        verify(paymentDelayQueue).reschedule(
            PaymentDelayTask.webhookProcess(EVENT_ID), NOW_INSTANT.plus(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("외부 취소 Processor가 NOT_APPLICABLE이면 그 사유로 MANUAL_REVIEW")
    void manualReviewWhenExternalCancellationNotApplicable() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.PAID);
        givenNoRefund();
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(externalCancellationProcessor.apply(PAYMENT_ID, console))
            .willReturn(ExternalCancellationResult.notApplicable("REQUESTED 환불이 여러 건: 2"));
        givenManualReviewRecorded("REQUESTED 환불이 여러 건: 2");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("외부 취소가 아직 처리 중이면 Processor를 부르지 않고 RETRY_PENDING")
    void retryWhenExternalCancellationStillRequested() {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CONSOLE_CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.PAID);
        givenNoRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID,
            cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, null,
                PortOneCancellationTrigger.CONSOLE)));
        given(webhookEventService.markRetryPending(eq(EVENT_ID), eq(ATTEMPT), contains("외부 취소 처리 중")))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        verifyNoInteractions(externalCancellationProcessor);
    }

    @Test
    @DisplayName("외부 취소 금액이 결제 금액과 다르면 Processor를 부르지 않고 MANUAL_REVIEW — 전액 취소만 자동 반영한다")
    void manualReviewWhenExternalCancellationAmountDiffers() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.PAID);
        givenNoRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, 10000L, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));
        givenManualReviewRecorded("외부 취소 금액 불일치");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(externalCancellationProcessor);
    }

    @Test
    @DisplayName("웹훅이 알려 준 취소가 차지백이면 Processor를 부르지 않고 MANUAL_REVIEW")
    void manualReviewWhenWebhookCancellationIsChargeback() {
        // given
        givenClaimed(claimedEvent(CANCELED, "cancel_chargeback"));
        givenOurPaymentIn(PaymentStatus.PAID);
        given(paymentService.findRefundByPortoneCancellationId("cancel_chargeback")).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_chargeback", PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.CHARGEBACK)));
        givenManualReviewRecorded("자동 정합화하지 않는 취소 경로");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(externalCancellationProcessor);
    }

    @Test
    @DisplayName("서버 API 취소인데 대응 환불이 없으면 외부 취소로 처리하지 않고 MANUAL_REVIEW — 환불 행이 빠진 데이터 불일치다")
    void manualReviewWhenApiCancellationHasNoRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.PAID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
                PortOneCancellationTrigger.API)));
        givenManualReviewRecorded("서버 API 취소인데 대응 Refund 없음");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(externalCancellationProcessor, paymentCancelProcessor, systemRefundProcessor);
    }

    // ---------- 외부 취소 — 진행 중인 기존 환불 ----------

    @Test
    @DisplayName("취소 ID 없는 회원 환불에 콘솔 전액 취소가 들어오면 그 취소로 회원 환불을 완료해 PROCESSED — EXTERNAL 환불을 만들지 않는다")
    void applyMemberRefundWithConsoleCancellation() {
        // given — 회원 취소의 PortOne 응답을 받기 전에 운영자가 콘솔에서 먼저 취소했다
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, null);
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(refund));
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, console.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(externalCancellationProcessor, systemRefundProcessor);
    }

    @Test
    @DisplayName("콘솔 취소는 성공했지만 서버 API 취소가 아직 처리 중이면 회원 환불을 완료하지 않고 RETRY_PENDING")
    void retryWhenApiCancellationInProgressWithConsoleCancellation() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(memberRefund(payment, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation("cancel_api", PortOneCancellationStatus.REQUESTED, AMOUNT, null,
                PortOneCancellationTrigger.API),
            consoleSucceeded()));
        given(webhookEventService.markRetryPending(eq(EVENT_ID), eq(ATTEMPT), contains("서버 API 취소가 처리 중")))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        verifyNoInteractions(paymentCancelProcessor, externalCancellationProcessor);
    }

    @Test
    @DisplayName("회원 환불에 묶으려는 콘솔 취소의 금액이 환불과 다르면 완료하지 않고 MANUAL_REVIEW")
    void manualReviewWhenConsoleCancellationAmountDiffersFromMemberRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPayment();
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(memberRefund(payment, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, 10000L, CANCELED_AT,
                PortOneCancellationTrigger.CONSOLE)));
        givenManualReviewRecorded("외부 취소 금액 불일치");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, externalCancellationProcessor);
    }

    @Test
    @DisplayName("취소 ID 없는 SYSTEM 환불에 콘솔 취소가 들어와도 다른 비API 취소가 살아 있으면 완료하지 않고 MANUAL_REVIEW")
    void manualReviewWhenOtherNonApiCancellationActiveOnSystemRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID)).willReturn(Optional.of(systemRefund(failed, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            consoleSucceeded(),
            cancellation("cancel_chargeback", PortOneCancellationStatus.REQUESTED, AMOUNT, null,
                PortOneCancellationTrigger.CHARGEBACK)));
        givenManualReviewRecorded("API가 아닌 취소");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(systemRefundProcessor, externalCancellationProcessor);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentCancelOutcome.class, names = {"PG_FAILED", "REQUEST_REJECTED"})
    @DisplayName("콘솔 취소는 성공했는데 잠금 사이에 회원 환불이 실패로 끝나 apply가 실패 결과를 주면 PROCESSED가 아니라 MANUAL_REVIEW")
    void manualReviewWhenMemberRefundFailedDespiteConsoleSucceeded(PaymentCancelOutcome failedOutcome) {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, null);
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(refund));
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, console.toCancelResult()))
            .willReturn(applied(failedOutcome, refund));
        givenManualReviewRecorded("회원 환불이 실패 상태");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(externalCancellationProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("콘솔 취소는 성공했는데 잠금 사이에 SYSTEM 환불이 실패로 끝나 Processor가 FAILED를 주면 PROCESSED가 아니라 MANUAL_REVIEW")
    void manualReviewWhenSystemRefundFailedDespiteConsoleSucceeded() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        Payment failed = givenOurFailedPayment();
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID)).willReturn(Optional.of(systemRefund(failed, null)));
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(systemRefundProcessor.apply(PAYMENT_ID, REFUND_ID, console.toCancelResult()))
            .willReturn(RefundStatus.FAILED);
        givenManualReviewRecorded("SYSTEM 환불이 FAILED");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(externalCancellationProcessor, paymentDelayQueue);
    }
    // ---------- 외부 취소 — 이미 끝난 환불 ----------

    @Test
    @DisplayName("같은 콘솔 취소의 EXTERNAL 환불이 이미 있으면 외부 취소 Processor가 이미 반영됐다고 판정해 PROCESSED — 웹훅 재전송")
    void processedWhenExternalRefundRedelivered() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        Payment canceled = givenOurPaymentIn(PaymentStatus.CANCELED);
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID))
            .willReturn(Optional.of(externalRefund(canceled, CONSOLE_CANCELLATION_ID)));
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(externalCancellationProcessor.apply(PAYMENT_ID, console))
            .willReturn(ExternalCancellationResult.alreadyReconciled());
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentCancelProcessor, systemRefundProcessor);
    }

    @Test
    @DisplayName("콘솔 취소로 완료된 회원 환불의 재전송은 회원 Processor가 아니라 외부 취소 Processor로 판정해 PROCESSED")
    void processedWhenCompletedMemberRefundRedelivered() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.CANCELED);
        Refund refund = memberRefund(payment, null);
        refund.complete(CONSOLE_CANCELLATION_ID, CANCELED_AT);
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID))
            .willReturn(Optional.of(refund));
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(externalCancellationProcessor.apply(PAYMENT_ID, console))
            .willReturn(ExternalCancellationResult.alreadyReconciled());
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @Test
    @DisplayName("완료된 회원 환불인데 결제가 PAID면 외부 취소 Processor의 판정대로 MANUAL_REVIEW — 회원 Processor가 완료 결과로 덮지 않게 한다")
    void manualReviewWhenCompletedMemberRefundButPaymentPaid() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPaymentIn(PaymentStatus.PAID);
        Refund refund = memberRefund(payment, null);
        refund.complete(CONSOLE_CANCELLATION_ID, CANCELED_AT);
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID))
            .willReturn(Optional.of(refund));
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(externalCancellationProcessor.apply(PAYMENT_ID, console)).willReturn(ExternalCancellationResult
            .notApplicable("완료 환불과 결제 상태·요청 주체가 일치하지 않음: paymentStatus=PAID"));
        givenManualReviewRecorded("완료 환불과 결제 상태·요청 주체가 일치하지 않음");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor);
    }

    @ParameterizedTest(name = "결제={0}, 요청 주체={1}")
    @CsvSource({"PAID, MEMBER", "FAILED, SYSTEM"})
    @DisplayName("같은 콘솔 취소의 환불이 FAILED면 기존 Processor가 실패 결과로 PROCESSED를 내지 않게 외부 취소 Processor 판정대로 MANUAL_REVIEW")
    void manualReviewWhenFailedRefundMatchesConsoleCancellation(PaymentStatus paymentStatus,
                                                                RefundRequester requestedBy) {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        Payment owner = givenOurPaymentIn(paymentStatus);
        Refund refund = requestedBy == RefundRequester.MEMBER ? memberRefund(payment, null) : systemRefund(owner, null);
        refund.fail(CONSOLE_CANCELLATION_ID, RefundFailureType.PG_FAILED, "cancellation FAILED");
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID))
            .willReturn(Optional.of(refund));
        PortOneCancellation console = consoleSucceeded();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, console));
        given(externalCancellationProcessor.apply(PAYMENT_ID, console)).willReturn(ExternalCancellationResult
            .notApplicable("같은 PortOne 취소 ID의 환불이 FAILED 상태"));
        givenManualReviewRecorded("FAILED 상태");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, systemRefundProcessor);
    }

    // ---------- 마트 거절 환불(ADMIN) ----------

    @Test
    @DisplayName("취소 ID 없는 진행 중 마트 거절 환불이 한 건이면 그 환불로 웹훅 취소를 반영해 PROCESSED — 취소를 새로 요청하지 않는다")
    void applyRequestedAdminRefund() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(adminAppliedCancellationId()).isEqualTo(CANCELLATION_ID);
        verifyNoInteractions(paymentCancelProcessor, systemRefundProcessor, externalCancellationProcessor);
        verifyNoAdminPost();
    }

    @Test
    @DisplayName("진행 중 회원 환불과 마트 거절 환불이 함께 있으면 어느 것도 고르지 않고 MANUAL_REVIEW")
    void manualReviewWhenMemberAndAdminRequested() {
        // given
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(memberRefund(payment, null)));
        given(paymentService.findRequestedAdminRefunds(PAYMENT_ID)).willReturn(List.of(adminRefund(payment, null)));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID));
        givenManualReviewRecorded("REQUESTED 환불이 여러 건");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentCancelProcessor, martRejectionProcessor);
    }

    @Test
    @DisplayName("웹훅 취소 ID로 마트 거절 환불이 정확히 찾히면 묶인 ID 조회 없이 그 환불로 반영한다")
    void applyAdminRefundFoundByCancellationId() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, CANCELLATION_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentService, never()).findBoundCancellationIds(anyLong(), anyLong());
        verify(paymentService, never()).findRequestedAdminRefunds(anyLong());
        verifyNoAdminPost();
    }

    @Test
    @DisplayName("웹훅 취소 ID로 회원 환불이 정확히 찾히면 진행 중 마트 거절 환불을 찾지 않고 회원 환불로 반영한다")
    void exactMemberRefundWinsOverRequestedAdmin() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = memberRefund(payment, CANCELLATION_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        PortOneCancellation ours = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, ours));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, ours.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentService, never()).findRequestedAdminRefunds(anyLong());
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("ID 없는 마트 거절 환불은 다른 환불에 묶인 실패한 취소를 빼고 이 환불 이후의 취소 하나를 반영한다")
    void applyCurrentAfterExcludingBoundFailed() {
        // given
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, null);
        given(paymentService.findBoundCancellationIds(PAYMENT_ID, REFUND_ID)).willReturn(Set.of("cancel_member"));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            api("cancel_member", PortOneCancellationStatus.FAILED, BEFORE_ADMIN_REQUEST),
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(adminAppliedCancellationId()).isEqualTo(CANCELLATION_ID);
        verifyNoAdminPost();
    }

    @Test
    @DisplayName("ID 없는 마트 거절 환불인데 다른 환불에 묶인 취소가 아직 처리 중이면 MANUAL_REVIEW — 반영하지 않는다")
    void manualReviewWhenBoundCancellationActive() {
        // given
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, null);
        given(paymentService.findBoundCancellationIds(PAYMENT_ID, REFUND_ID)).willReturn(Set.of("cancel_member"));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID,
            api("cancel_member", PortOneCancellationStatus.REQUESTED, BEFORE_ADMIN_REQUEST),
            api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, NOW.minusMinutes(2))));
        givenManualReviewRecorded("다른 환불에 묶인");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(martRejectionProcessor);
        verifyNoAdminPost();
    }

    @Test
    @DisplayName("ID 없는 마트 거절 환불인데 이 환불보다 먼저 요청된 주인 없는 취소가 살아 있으면 MANUAL_REVIEW")
    void manualReviewWhenPastUnboundActive() {
        // given
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, null);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID,
            api("cancel_orphan", PortOneCancellationStatus.UNKNOWN, BEFORE_ADMIN_REQUEST),
            api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, NOW.minusMinutes(2))));
        givenManualReviewRecorded("먼저 요청된");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(martRejectionProcessor);
        verifyNoAdminPost();
    }

    @Test
    @DisplayName("ID 없는 마트 거절 환불인데 이 환불 이후의 취소가 둘이면 MANUAL_REVIEW")
    void manualReviewWhenTwoCurrentCancellations() {
        // given
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, null);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID,
            api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, NOW.minusMinutes(2)),
            api("cancel_2", PortOneCancellationStatus.REQUESTED, NOW.minusMinutes(1))));
        givenManualReviewRecorded("이 환불 이후의 서버 API 취소가 2건");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("ID 없는 마트 거절 환불의 취소가 아직 보이지 않으면 웹훅은 취소를 요청하지 않고 다시 묻는다")
    void retryWithoutPostWhenNoCurrentCancellation() {
        // given
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, null);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.PAID));
        given(webhookEventService.markRetryPending(eq(EVENT_ID), eq(ATTEMPT), contains("웹훅은 취소를 요청하지 않음")))
            .willReturn(true);

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        verifyNoInteractions(martRejectionProcessor);
        verifyNoAdminPost();
    }

    @Test
    @DisplayName("웹훅 취소 ID가 이 환불의 취소로 고른 것과 다르면 MANUAL_REVIEW — 늦게 온 과거 취소의 웹훅을 이 환불에 묶지 않는다")
    void manualReviewWhenWebhookCancellationDiffersFromSelected() {
        // given — 웹훅은 이 환불보다 먼저 실패로 끝난 과거 취소를 말한다
        givenClaimed(claimedEvent(CANCELED, "cancel_old"));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, "cancel_old");
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID,
            api("cancel_old", PortOneCancellationStatus.FAILED, BEFORE_ADMIN_REQUEST),
            api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, NOW.minusMinutes(2))));
        givenManualReviewRecorded("고른 것과 다름");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(martRejectionProcessor);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationStatus.class, names = {"REQUESTED", "UNKNOWN"})
    @DisplayName("반영 결과가 미확정이어도 취소 ID가 있으면 마트 거절 실행 재확인을 예약하고 PROCESSED — 웹훅을 다시 돌리지 않는다")
    void scheduleAdminExecuteWhenPending(PortOneCancellationStatus status) {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID, api(CANCELLATION_ID, status, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.PENDING);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.adminRefundExecute(REFUND_ID), ADMIN_RECHECK_AT);
        verify(paymentDelayQueue, never()).enqueueIfEarlier(eq(PaymentDelayTask.systemRefundExecute(REFUND_ID)), any());
        verifyNoAdminPost();
    }

    @Test
    @DisplayName("마트 거절 실행 재확인 예약이 Redis 오류로 실패해도 웹훅은 PROCESSED — 환불이 REQUESTED라 Fallback이 다시 넣는다")
    void processedWhenAdminScheduleFails() {
        // given
        givenClaimed(claimedEvent(CANCEL_PENDING, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID, api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.PENDING);
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(any(), any());
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
    }

    @Test
    @DisplayName("PortOne이 취소 실패를 명시해 마트 거절 환불이 실패로 끝나면 반영이 끝난 것이라 PROCESSED")
    void processedWhenAdminRefundFailed() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID, api(CANCELLATION_ID, PortOneCancellationStatus.FAILED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.PG_FAILED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @ParameterizedTest
    @EnumSource(value = MartRejectionOutcome.class, names = {"PG_FAILED", "REQUEST_REJECTED"})
    @DisplayName("PortOne 취소는 성공인데 TX2가 실패한 마트 거절 환불을 돌려주면 PROCESSED로 덮지 않고 MANUAL_REVIEW")
    void manualReviewWhenPgSucceededButAdminRefundFailed(MartRejectionOutcome failure) {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, failure);
        givenManualReviewRecorded("PortOne 취소는 성공했지만 마트 거절 환불이 실패 상태");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("진행 중 마트 거절 환불에 콘솔 전액 취소가 오면 새 EXTERNAL 환불 없이 그 취소로 기존 환불을 끝낸다")
    void completeRequestedAdminRefundWithConsoleCancellation() {
        // given
        givenClaimed(claimedEvent(CANCELED, CONSOLE_CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        givenRequestedAdmin(refund, CONSOLE_CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, consoleSucceeded()));
        givenAdminApplied(refund, MartRejectionOutcome.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(adminAppliedCancellationId()).isEqualTo(CONSOLE_CANCELLATION_ID);
        verifyNoInteractions(externalCancellationProcessor);
    }

    @Test
    @DisplayName("이미 완료된 마트 거절 환불의 같은 취소 웹훅이 다시 오면 TX2가 저장된 결과를 돌려줘 PROCESSED — 재전송 멱등")
    void processedWhenCompletedAdminRefundRedelivered() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        payment.markCanceled(CANCELED_AT);
        refund.complete(CANCELLATION_ID, CANCELED_AT);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.COMPLETED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("이미 실패한 마트 거절 환불의 같은 실패 취소 웹훅이 다시 오면 PROCESSED — 같은 사실의 반복이다")
    void processedWhenFailedAdminRefundRedelivered() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        refund.fail(CANCELLATION_ID, RefundFailureType.PG_FAILED, "cancellation FAILED");
        payment.restorePaid();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID, api(CANCELLATION_ID, PortOneCancellationStatus.FAILED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.PG_FAILED);
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
    }

    @Test
    @DisplayName("실패로 저장된 마트 거절 환불인데 PortOne은 그 취소가 성공이라고 하면 MANUAL_REVIEW — DB와 PG가 다른 사실을 말한다")
    void manualReviewWhenFailedAdminRefundButPgSucceeded() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        refund.fail(CANCELLATION_ID, RefundFailureType.PG_FAILED, "cancellation FAILED");
        payment.restorePaid();
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, NOW.minusMinutes(2))));
        givenAdminApplied(refund, MartRejectionOutcome.PG_FAILED);
        givenManualReviewRecorded("PortOne 취소는 성공했지만 마트 거절 환불이 실패 상태");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("진행 중 마트 거절 환불인데 결제가 CANCEL_REQUESTED가 아니면 TX2에 넘기지 않고 MANUAL_REVIEW")
    void manualReviewWhenRequestedAdminRefundWithPaidPayment() {
        // given
        givenClaimed(claimedEvent(CANCELED, CANCELLATION_ID));
        givenOurPayment();
        Refund refund = adminRefund(payment, null);
        payment.restorePaid();
        givenRequestedAdmin(refund, CANCELLATION_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment(
            PortOnePaymentStatus.PAID, api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, NOW.minusMinutes(2))));
        givenManualReviewRecorded("결제가 CANCEL_REQUESTED가 아님");

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("ID 없는 회원 환불도 다른 환불에 묶인 과거 취소를 빼고 고른다 — 같은 금액의 API 취소가 둘이어도 하나로 반영")
    void memberRefundExcludesBoundCancellation() {
        // given — 앞선 회원 환불이 cancel_old로 실패했고, 다시 신청한 회원 환불은 아직 취소 ID가 없다
        givenClaimed(claimedEvent(CANCELED, null));
        givenOurPayment();
        Refund refund = memberRefund(payment, null);
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(refund));
        given(paymentService.findBoundCancellationIds(PAYMENT_ID, REFUND_ID)).willReturn(Set.of("cancel_old"));
        PortOneCancellation old = cancellation(
            "cancel_old", PortOneCancellationStatus.FAILED, AMOUNT, null, PortOneCancellationTrigger.API);
        PortOneCancellation ours = cancellation(
            CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willReturn(portOnePayment(PortOnePaymentStatus.CANCELED, old, ours));
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, ours.toCancelResult()))
            .willReturn(applied(PaymentCancelOutcome.CANCELED, refund));
        givenProcessedRecorded();

        // when
        WebhookProcessOutcome outcome = facade.processWebhook(EVENT_ID);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
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

    // 외부 취소 반영은 결제 상태로 판정한다. 웹훅이 찾는 결제를 원하는 상태로 돌려준다
    private Payment givenOurPaymentIn(PaymentStatus status) {
        Payment found = payment(PAYMENT_ID, ORDER_ID, status);
        given(paymentService.findByPortonePaymentId(PORTONE_PAYMENT_ID)).willReturn(Optional.of(found));
        return found;
    }

    // 취소 ID로 찾히는 환불도, 취소 ID 없는 진행 중 회원 환불도 없다. SYSTEM 환불 조회는 기본값(빈 Optional)을 쓴다
    private void givenNoRefund() {
        given(paymentService.findRefundByPortoneCancellationId(CONSOLE_CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
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

    // 앞선 웹훅이 외부 취소로 만든 EXTERNAL 환불. 결제는 이미 CANCELED다
    private static Refund externalRefund(Payment canceledOwner, String cancellationId) {
        Refund refund = Refund.completedByExternal(canceledOwner, cancellationId, "PortOne 외부 전액 취소(CONSOLE)",
            NOW.minusMinutes(2), CANCELED_AT);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    // 거절 접수(TX1)가 만든 마트 거절 환불. cancellationId가 있으면 거절 실행기(또는 앞선 웹훅)가 PortOne 취소 ID를 기록한 상태다
    private static Refund adminRefund(Payment owner, String cancellationId) {
        Refund refund = Refund.requestedByAdmin(owner, "상품 준비가 어렵습니다.", "admin-key-1", ADMIN_REQUESTED_AT);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        if (cancellationId != null) {
            refund.recordRequested(cancellationId);
        }
        return refund;
    }

    // 취소 ID로 찾히는 환불은 없고 진행 중 환불은 마트 거절 환불 하나뿐이다. SYSTEM 환불 조회는 기본값(빈 Optional)을 쓴다
    private void givenRequestedAdmin(Refund refund, String webhookCancellationId) {
        if (webhookCancellationId != null) {
            given(paymentService.findRefundByPortoneCancellationId(webhookCancellationId)).willReturn(Optional.empty());
        }
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(paymentService.findRequestedAdminRefunds(PAYMENT_ID)).willReturn(List.of(refund));
    }

    // TX2는 MartRejectionProcessorTest가 검증한다. 여기서는 결과 종류만 정해 웹훅 판정을 본다
    private void givenAdminApplied(Refund refund, MartRejectionOutcome outcome) {
        given(martRejectionProcessor.apply(eq(PAYMENT_ID), eq(REFUND_ID), any())).willReturn(
            new MartRejectionPaymentResult(outcome, MartRejectionResponse.of(refund.getPayment(), refund)));
    }

    private String adminAppliedCancellationId() {
        ArgumentCaptor<PortOneCancelResult> result = ArgumentCaptor.forClass(PortOneCancelResult.class);
        verify(martRejectionProcessor).apply(eq(PAYMENT_ID), eq(REFUND_ID), result.capture());
        return result.getValue().cancellationId();
    }

    // 웹훅은 이미 일어난 취소를 반영할 뿐 취소를 요청하지 않는다
    private void verifyNoAdminPost() {
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    private static PortOneCancellation api(String id, PortOneCancellationStatus status, LocalDateTime requestedAt) {
        LocalDateTime canceledAt = status == PortOneCancellationStatus.SUCCEEDED ? CANCELED_AT : null;
        return new PortOneCancellation(id, status, AMOUNT, requestedAt, canceledAt, PortOneCancellationTrigger.API);
    }

    private static PortOneCancellation consoleSucceeded() {
        return cancellation(CONSOLE_CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, CANCELED_AT,
            PortOneCancellationTrigger.CONSOLE);
    }

    private static PaymentCancelResult applied(PaymentCancelOutcome outcome, Refund refund) {
        return new PaymentCancelResult(outcome, PaymentCancelResponse.afterPay(refund.getPayment(), refund));
    }
}
