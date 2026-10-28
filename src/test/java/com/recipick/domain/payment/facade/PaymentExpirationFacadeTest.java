package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
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
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

// 만료 확인의 판정 행렬을 본다. 잠금 아래 상태 전이는 PaymentExpirationProcessorTest·PaymentConfirmProcessorTest,
// 실제 잠금 경합과 큐 흐름은 통합 테스트가 맡는다
@ExtendWith(MockitoExtension.class)
class PaymentExpirationFacadeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW_INSTANT = Instant.parse("2026-10-27T13:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, SEOUL);
    private static final Instant RECHECK_AT = NOW_INSTANT.plus(Duration.ofMinutes(1));
    private static final Long PAYMENT_ID = 10L;
    private static final Long ORDER_ID = 1L;
    private static final Long AMOUNT = 23800L;
    private static final String PORTONE_PAYMENT_ID = "pay_1";

    @Mock
    private PaymentService paymentService;

    @Mock
    private PortOnePaymentClient portOnePaymentClient;

    @Mock
    private PaymentConfirmProcessor paymentConfirmProcessor;

    @Mock
    private PaymentExpirationProcessor paymentExpirationProcessor;

    @Mock
    private SystemRefundExecutionFacade systemRefundExecutionFacade;

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    private PaymentExpirationFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PaymentExpirationFacade(paymentService, portOnePaymentClient, paymentConfirmProcessor,
            paymentExpirationProcessor, systemRefundExecutionFacade, paymentDelayQueue,
            Clock.fixed(NOW_INSTANT, SEOUL));
    }

    // ---------- 스냅샷 · 선행 조건 ----------

    @Test
    @DisplayName("레시픽 DB에 결제가 없으면 SKIPPED — PortOne을 묻지 않는다")
    void skipWhenPaymentMissing() {
        // given
        given(paymentService.findById(PAYMENT_ID)).willReturn(Optional.empty());

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, paymentConfirmProcessor, paymentExpirationProcessor,
            paymentDelayQueue);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "IN_PROGRESS")
    @DisplayName("이미 IN_PROGRESS가 아니면 SKIPPED — 확정·웹훅·결제 전 취소가 먼저 끝낸 결제다")
    void skipWhenNotInProgress(PaymentStatus status) {
        // given
        givenPayment(status, NOW.minusMinutes(1));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, paymentConfirmProcessor, paymentExpirationProcessor);
    }

    @Test
    @DisplayName("만료 시각이 없는 IN_PROGRESS 결제는 기준이 없어 MANUAL_REVIEW — PortOne을 묻지 않는다")
    void manualReviewWhenExpiresAtMissing() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, null);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.MANUAL_REVIEW);
        verifyNoInteractions(portOnePaymentClient, paymentDelayQueue);
    }

    @Test
    @DisplayName("만료 시각 전에 실행되면 PortOne을 묻지 않고 만료 시각으로 덮어써 다시 예약한다")
    void rescheduleAtExpiresAtWhenTooEarly() {
        // given
        LocalDateTime expiresAt = NOW.plusMinutes(2);
        givenPayment(PaymentStatus.IN_PROGRESS, expiresAt);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESCHEDULED);
        verify(paymentDelayQueue).reschedule(PaymentDelayTask.paymentExpire(PAYMENT_ID),
            expiresAt.atZone(SEOUL).toInstant());
        verify(paymentDelayQueue, never()).enqueueIfEarlier(any(), any());
        verifyNoInteractions(portOnePaymentClient);
    }

    @Test
    @DisplayName("스냅샷 조회가 일시적 DB 장애면 1분 뒤로 다시 예약한다")
    void rescheduleWhenSnapshotLookupFailsTransiently() {
        // given
        given(paymentService.findById(PAYMENT_ID)).willThrow(new QueryTimeoutException("query timeout"));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESCHEDULED);
        verify(paymentDelayQueue).reschedule(PaymentDelayTask.paymentExpire(PAYMENT_ID), RECHECK_AT);
        verifyNoInteractions(portOnePaymentClient);
    }

    // ---------- 미결제 만료 ----------

    @Test
    @DisplayName("PG 결제가 READY면 만료 Processor에 넘긴다 — 확정 Processor는 부르지 않는다")
    void expireWhenReady() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.READY));
        given(paymentExpirationProcessor.expire(PAYMENT_ID)).willReturn(PaymentExpirationOutcome.EXPIRED);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        verifyNoInteractions(paymentConfirmProcessor, systemRefundExecutionFacade, paymentDelayQueue);
    }

    @Test
    @DisplayName("PortOne에 결제가 없으면(404) 결제창을 열지 않은 것으로 보고 만료 Processor에 넘긴다")
    void expireWhenPortOneNotFound() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        given(paymentExpirationProcessor.expire(PAYMENT_ID)).willReturn(PaymentExpirationOutcome.EXPIRED);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        verifyNoInteractions(paymentConfirmProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("조회 뒤 다른 경로가 먼저 결제를 끝내 만료 Processor가 SKIPPED를 돌려주면 그대로 SKIPPED")
    void skipWhenExpirationProcessorSkips() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.READY));
        given(paymentExpirationProcessor.expire(PAYMENT_ID)).willReturn(PaymentExpirationOutcome.SKIPPED);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.SKIPPED);
    }

    // ---------- PAID · FAILED → 기존 확정 Processor ----------

    @ParameterizedTest
    @EnumSource(value = PaymentApprovalOutcome.class,
        names = {"APPROVED", "ALREADY_PAID", "PG_FAILED", "AMOUNT_MISMATCH", "EXPIRED", "ALREADY_CLOSED"})
    @DisplayName("PG가 PAID면 확정 Processor로 판정하고, 판정과 관계없이 SYSTEM 환불 실행 예약 여부를 scheduleAfterConfirm에 맡긴다")
    void resolveThroughConfirmProcessor(PaymentApprovalOutcome approvalOutcome) {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        PortOnePayment portOnePayment = portOne(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment)).willReturn(approvalOutcome);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESOLVED);
        verify(systemRefundExecutionFacade).scheduleAfterConfirm(PAYMENT_ID, approvalOutcome);
        verifyNoInteractions(paymentExpirationProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("PG가 FAILED면 확정 Processor의 PG_FAILED 경로로 닫혀 RESOLVED — 만료 Processor는 부르지 않는다")
    void resolvePgFailedThroughConfirmProcessor() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        PortOnePayment portOnePayment = portOne(PortOnePaymentStatus.FAILED);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment)).willReturn(PaymentApprovalOutcome.PG_FAILED);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESOLVED);
        verify(systemRefundExecutionFacade).scheduleAfterConfirm(PAYMENT_ID, PaymentApprovalOutcome.PG_FAILED);
        verifyNoInteractions(paymentExpirationProcessor);
    }

    @Test
    @DisplayName("PAID인데 금액·완료 시각이 비어 NOT_PAID면 1분 뒤 다시 묻는다")
    void retryWhenNotPaid() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        PortOnePayment portOnePayment = portOne(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment)).willReturn(PaymentApprovalOutcome.NOT_PAID);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESCHEDULED);
        verify(paymentDelayQueue).reschedule(PaymentDelayTask.paymentExpire(PAYMENT_ID), RECHECK_AT);
        verifyNoInteractions(paymentExpirationProcessor);
    }

    @Test
    @DisplayName("customData 주문 ID가 다르면(ORDER_MISMATCH) 자동 반영하지 않고 MANUAL_REVIEW — 재예약하지 않는다")
    void manualReviewWhenOrderMismatch() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        PortOnePayment portOnePayment = portOne(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment))
            .willReturn(PaymentApprovalOutcome.ORDER_MISMATCH);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentExpirationProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("잠금 아래에서 다른 흐름이 결제 상태를 가져가 INVALID_STATUS면 SKIPPED")
    void skipWhenInvalidStatus() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        PortOnePayment portOnePayment = portOne(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment))
            .willReturn(PaymentApprovalOutcome.INVALID_STATUS);

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.SKIPPED);
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- 상태 불명 · 장애 ----------

    @ParameterizedTest
    @EnumSource(value = PortOnePaymentStatus.class, names = {"PENDING", "VIRTUAL_ACCOUNT_ISSUED", "UNKNOWN"})
    @DisplayName("카드 결제에서 예상하지 않는 PG 상태는 실패로 단정하지 않고 1분 뒤 다시 묻는다")
    void retryWhenPgStatusUndetermined(PortOnePaymentStatus status) {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(status));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESCHEDULED);
        verify(paymentDelayQueue).reschedule(PaymentDelayTask.paymentExpire(PAYMENT_ID), RECHECK_AT);
        verifyNoInteractions(paymentConfirmProcessor, paymentExpirationProcessor);
    }

    @ParameterizedTest
    @EnumSource(value = CommonErrorCode.class, names = {"EXTERNAL_SERVICE_ERROR", "EXTERNAL_SERVICE_TIMEOUT"})
    @DisplayName("PortOne 조회가 5xx·timeout·연결 실패면 결제를 닫지 않고 1분 뒤 다시 묻는다")
    void retryWhenPortOneLookupFails(CommonErrorCode errorCode) {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willThrow(new BusinessException(errorCode));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESCHEDULED);
        verify(paymentDelayQueue).reschedule(PaymentDelayTask.paymentExpire(PAYMENT_ID), RECHECK_AT);
        verifyNoInteractions(paymentConfirmProcessor, paymentExpirationProcessor);
    }

    @Test
    @DisplayName("PortOne 조회에서 예상하지 못한 오류 코드가 오면 MANUAL_REVIEW — 결제를 닫지도 재예약하지도 않는다")
    void manualReviewWhenPortOneLookupFailsUnexpectedly() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentConfirmProcessor, paymentExpirationProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("재예약이 Redis 오류로 실패해도 예외를 던지지 않고 RESCHEDULED — 결제가 IN_PROGRESS로 남아 Fallback이 복구한다")
    void rescheduledEvenWhenRedisFails() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).reschedule(any(), any());

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESCHEDULED);
    }

    @Test
    @DisplayName("만료 시각부터 10분 직전까지는 미확정이면 1분 뒤 다시 묻는다")
    void retryJustBeforeRecheckWindowEnds() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(10).plusSeconds(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESCHEDULED);
        verify(paymentDelayQueue).reschedule(PaymentDelayTask.paymentExpire(PAYMENT_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("만료 시각부터 10분이 지나도 미확정이면 STALLED — 1분 재예약을 멈추고 Fallback 주기에 맡긴다")
    void stalledAfterRecheckWindow() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(10));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.STALLED);
        verifyNoInteractions(paymentDelayQueue, paymentExpirationProcessor);
    }

    // ---------- 외부 취소 ----------

    @ParameterizedTest
    @EnumSource(value = PortOnePaymentStatus.class, names = {"CANCELED", "PARTIAL_CANCELED"})
    @DisplayName("내부 결제가 IN_PROGRESS인데 PG에서 취소됐으면 MANUAL_REVIEW — 주문 종료 근거가 부족해 어느 Processor에도 넘기지 않는다")
    void manualReviewWhenPgCanceled(PortOnePaymentStatus status) {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(status));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentConfirmProcessor, paymentExpirationProcessor, systemRefundExecutionFacade,
            paymentDelayQueue);
    }

    // ---------- Processor 예외 분류 ----------

    @Test
    @DisplayName("만료 Processor가 락 대기 초과로 실패하면 1분 뒤 다시 묻는다 — 다음 실행은 GET부터 다시 본다")
    void retryWhenProcessorLockTimesOut() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.READY));
        given(paymentExpirationProcessor.expire(PAYMENT_ID))
            .willThrow(new CannotAcquireLockException("lock wait timeout"));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.RESCHEDULED);
        verify(paymentDelayQueue).reschedule(PaymentDelayTask.paymentExpire(PAYMENT_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("Processor의 예상 못한 오류는 MANUAL_REVIEW — 1분마다 같은 오류를 반복하지 않는다")
    void manualReviewWhenProcessorFailsUnexpectedly() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        PortOnePayment portOnePayment = portOne(PortOnePaymentStatus.PAID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOnePayment);
        given(paymentConfirmProcessor.process(ORDER_ID, portOnePayment))
            .willThrow(new IllegalStateException("예상 못한 상태"));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.MANUAL_REVIEW);
        verifyNoInteractions(paymentDelayQueue, systemRefundExecutionFacade);
    }

    @Test
    @DisplayName("Processor가 결제를 못 찾아 PAYMENT_NOT_FOUND를 던져도 PortOne 404로 보지 않는다 — 만료를 다시 부르지 않고 MANUAL_REVIEW")
    void processorNotFoundIsNotPortOneNotFound() {
        // given
        givenPayment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne(PortOnePaymentStatus.READY));
        given(paymentExpirationProcessor.expire(PAYMENT_ID))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // when
        PaymentExpirationOutcome outcome = facade.execute(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.MANUAL_REVIEW);
        verify(paymentExpirationProcessor).expire(anyLong());
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- 픽스처 ----------

    private Payment givenPayment(PaymentStatus status, LocalDateTime expiresAt) {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(PORTONE_PAYMENT_ID)
            .amount(AMOUNT)
            .status(status)
            .expiresAt(expiresAt)
            .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        given(paymentService.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
        return payment;
    }

    private static PortOnePayment portOne(PortOnePaymentStatus status) {
        return new PortOnePayment(PORTONE_PAYMENT_ID, "tx_1", status, AMOUNT, "KRW", NOW.minusMinutes(2), ORDER_ID,
            null, List.of());
    }
}
