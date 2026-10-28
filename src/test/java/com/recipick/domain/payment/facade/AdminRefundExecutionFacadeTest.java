package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.dto.AdminRefundExecution;
import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.service.MartRejectionOutcome;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

// 마트 거절 환불 실행의 판정 행렬을 본다. 상태 전이는 MartRejectionProcessorTest, 외부 취소 판정은 RefundCancellationRulesTest가 맡는다.
// 가장 중요한 검증은 PortOne POST 횟수다 — 보내면 안 되는 경로는 never, 보내야 하는 경로는 정확히 한 번
@ExtendWith(MockitoExtension.class)
class AdminRefundExecutionFacadeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW_INSTANT = Instant.parse("2026-10-28T06:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, SEOUL);
    private static final Instant RECHECK_AT = NOW_INSTANT.plus(Duration.ofMinutes(10));
    private static final LocalDateTime REQUESTED_AT = NOW.minusMinutes(1);
    private static final LocalDateTime AFTER_REQUEST = REQUESTED_AT.plusSeconds(1);
    private static final LocalDateTime BEFORE_REQUEST = REQUESTED_AT.minusDays(1);
    private static final Long PAYMENT_ID = 10L;
    private static final Long REFUND_ID = 77L;
    private static final Long AMOUNT = 23800L;
    private static final String PORTONE_PAYMENT_ID = "pay_1";
    private static final String SERVER_KEY = "recipick-refund-77";
    private static final String REASON = "상품 준비가 어렵습니다.";
    private static final String CANCELLATION_ID = "cancel_admin";
    private static final String OTHER_CANCELLATION_ID = "cancel_other";
    private static final String MEMBER_CANCELLATION_ID = "cancel_member";
    private static final String CONSOLE_CANCELLATION_ID = "cancel_console";

    @Mock
    private PaymentService paymentService;

    @Mock
    private PortOnePaymentClient portOnePaymentClient;

    @Mock
    private MartRejectionProcessor martRejectionProcessor;

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    private AdminRefundExecutionFacade facade;

    @BeforeEach
    void setUp() {
        facade = new AdminRefundExecutionFacade(paymentService, portOnePaymentClient, martRejectionProcessor,
            paymentDelayQueue, Clock.fixed(NOW_INSTANT, SEOUL));
    }

    // ---------- 실행하지 않는 경우 ----------

    @Test
    @DisplayName("환불이 없으면 SKIPPED — PortOne을 묻지 않는다")
    void skipWhenRefundMissing() {
        // given
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.empty());

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.SKIPPED);
        assertThat(execution.appliedResult()).isEmpty();
        verifyNoInteractions(portOnePaymentClient, martRejectionProcessor);
    }

    @Test
    @DisplayName("회원 환불이 들어오면 SKIPPED — 등록 쪽 버그라 취소를 요청하지 않는다")
    void skipNonAdminRefund() {
        // given
        Refund refund = Refund.requestedByMember(cancelRequestedPayment(), "단순 변심", "client-key-1", REQUESTED_AT);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(refund));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, martRejectionProcessor);
    }

    @ParameterizedTest
    @EnumSource(value = RefundStatus.class, names = {"COMPLETED", "FAILED"})
    @DisplayName("이미 끝난 마트 거절 환불은 SKIPPED — Worker에 오래된 작업이 남아 있어도 정상 종료한다")
    void skipWhenAlreadyFinished(RefundStatus status) {
        // given
        Refund refund = givenRequestedRefund();
        if (status == RefundStatus.COMPLETED) {
            refund.complete(CANCELLATION_ID, NOW.minusSeconds(30));
        } else {
            refund.fail(CANCELLATION_ID, RefundFailureType.PG_FAILED, "cancellation FAILED");
        }

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.SKIPPED);
        verifyNoInteractions(portOnePaymentClient, martRejectionProcessor);
    }

    @Test
    @DisplayName("스냅샷 조회가 일시적 DB 장애면 PortOne을 묻지 않고 10분 뒤 재실행을 예약한다")
    void rescheduleWhenSnapshotHitsTransientFailure() {
        // given
        given(paymentService.findRefundWithPayment(REFUND_ID)).willThrow(new QueryTimeoutException("query timeout"));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.adminRefundExecute(REFUND_ID), RECHECK_AT);
        verifyNoInteractions(portOnePaymentClient, martRejectionProcessor);
    }

    @Test
    @DisplayName("스냅샷 조회가 예상하지 못한 오류면 MANUAL_REVIEW — 다시 해도 같은 결과라 예약하지 않는다")
    void manualReviewWhenSnapshotFails() {
        // given
        given(paymentService.findRefundWithPayment(REFUND_ID)).willThrow(new IllegalStateException("bug"));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(portOnePaymentClient, martRejectionProcessor, paymentDelayQueue);
    }

    // ---------- 전제 ----------

    @Test
    @DisplayName("진행 중 환불인데 결제가 CANCEL_REQUESTED가 아니면 MANUAL_REVIEW — DB 정합성이 깨져 PortOne을 부르지 않는다")
    void manualReviewWhenPaymentNotCancelRequested() {
        // given
        Refund refund = givenRequestedRefund();
        refund.getPayment().restorePaid();

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoInteractions(portOnePaymentClient, martRejectionProcessor, paymentDelayQueue);
    }

    @Test
    @DisplayName("PortOne 조회가 실패하면 실패로 단정하지 않고 POST 없이 10분 뒤 마트 거절 환불 실행을 예약한다")
    void rescheduleWhenGetFails() {
        // given
        givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.adminRefundExecute(REFUND_ID), RECHECK_AT);
        verify(paymentDelayQueue, never()).enqueueIfEarlier(eq(PaymentDelayTask.systemRefundExecute(REFUND_ID)), any());
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    // ---------- 외부 취소 ----------

    @Test
    @DisplayName("운영자가 콘솔에서 먼저 전액 취소했으면 새 POST 없이 그 취소로 이 환불을 끝낸다")
    void applyMatchedConsoleCancellation() {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.CANCELED, consoleCancellation(PortOneCancellationStatus.SUCCEEDED));
        givenApplied(refund, MartRejectionOutcome.COMPLETED);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertThat(execution.appliedResult()).map(MartRejectionPaymentResult::outcome)
            .contains(MartRejectionOutcome.COMPLETED);
        assertThat(appliedCancellationId()).isEqualTo(CONSOLE_CANCELLATION_ID);
        verifyNoPost();
    }

    @Test
    @DisplayName("콘솔 취소가 아직 처리 중이면 POST 없이 재실행을 예약한다")
    void rescheduleWhenExternalCancellationPending() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID, consoleCancellation(PortOneCancellationStatus.REQUESTED));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("살아 있는 콘솔 취소가 둘이면 MANUAL_REVIEW — 어느 쪽인지 고르지 않는다")
    void manualReviewWhenExternalCancellationConflicts() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.CANCELED,
            consoleCancellation(PortOneCancellationStatus.SUCCEEDED),
            new PortOneCancellation("cancel_console_2", PortOneCancellationStatus.SUCCEEDED, AMOUNT, AFTER_REQUEST,
                NOW.minusSeconds(10), PortOneCancellationTrigger.CONSOLE));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor, paymentDelayQueue);
    }

    // ---------- 받아 둔 취소 ID ----------

    @Test
    @DisplayName("받아 둔 취소 ID가 있으면 그 취소만 반영한다 — 묶인 ID 조회·시각 비교 없이 다른 취소로 갈아타지 않는다")
    void trackStoredCancellationOnly() {
        // given
        Refund refund = givenRequestedRefund();
        refund.recordRequested(CANCELLATION_ID);
        givenPortOne(PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AFTER_REQUEST),
            api(OTHER_CANCELLATION_ID, PortOneCancellationStatus.FAILED, AFTER_REQUEST));
        givenApplied(refund, MartRejectionOutcome.COMPLETED);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertThat(appliedCancellationId()).isEqualTo(CANCELLATION_ID);
        verify(paymentService, never()).findBoundCancellationIds(anyLong(), anyLong());
        verifyNoPost();
    }

    @Test
    @DisplayName("받아 둔 취소 ID가 조회 결과에 아직 없으면 POST 없이 재실행을 예약한다")
    void rescheduleWhenStoredCancellationNotVisible() {
        // given
        Refund refund = givenRequestedRefund();
        refund.recordRequested(CANCELLATION_ID);
        givenPortOne(PortOnePaymentStatus.PAID);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    // ---------- 취소 ID 없는 환불의 후보 판정 ----------

    @Test
    @DisplayName("과거 회원 취소의 실패한 취소는 다른 환불에 묶여 있어 후보에서 빠지고, 새로 한 번 POST한다")
    void postAfterExcludingBoundFailedCancellation() {
        // given
        Refund refund = givenRequestedRefund();
        givenBound(MEMBER_CANCELLATION_ID);
        givenPortOne(PortOnePaymentStatus.PAID,
            api(MEMBER_CANCELLATION_ID, PortOneCancellationStatus.FAILED, BEFORE_REQUEST));
        givenPost(PortOneCancelOutcome.SUCCEEDED);
        givenApplied(refund, MartRejectionOutcome.COMPLETED);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verify(portOnePaymentClient).cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT);
        assertThat(appliedCancellationId()).isEqualTo(CANCELLATION_ID);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationStatus.class, names = {"REQUESTED", "UNKNOWN", "SUCCEEDED"})
    @DisplayName("다른 환불에 묶인 API 취소가 실패로 끝나지 않았으면 MANUAL_REVIEW — 반영도 POST도 하지 않는다")
    void manualReviewWhenBoundCancellationActive(PortOneCancellationStatus status) {
        // given
        givenRequestedRefund();
        givenBound(MEMBER_CANCELLATION_ID);
        givenPortOne(PortOnePaymentStatus.PAID, api(MEMBER_CANCELLATION_ID, status, BEFORE_REQUEST));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("이 환불 이후의 API 취소가 하나면 POST 응답을 못 받은 그 취소로 보고 반영한다")
    void applySingleCurrentCancellation() {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AFTER_REQUEST));
        givenApplied(refund, MartRejectionOutcome.COMPLETED);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertThat(appliedCancellationId()).isEqualTo(CANCELLATION_ID);
        verifyNoPost();
    }

    @Test
    @DisplayName("요청 시각이 이 환불과 같은 API 취소도 이 환불 이후로 본다")
    void treatSameRequestedAtAsCurrent() {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, REQUESTED_AT));
        givenApplied(refund, MartRejectionOutcome.COMPLETED);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertThat(appliedCancellationId()).isEqualTo(CANCELLATION_ID);
        verifyNoPost();
    }

    @Test
    @DisplayName("이 환불 이후의 API 취소가 둘이면 MANUAL_REVIEW — 어느 것이 이 환불의 취소인지 고르지 않는다")
    void manualReviewWhenTwoCurrentCancellations() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID,
            api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AFTER_REQUEST),
            api(OTHER_CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AFTER_REQUEST.plusSeconds(1)));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("주인 없는 API 취소에 요청 시각이 없으면 MANUAL_REVIEW — 이 환불 전후를 가를 수 없다")
    void manualReviewWhenUnboundCancellationHasNoRequestedAt() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID, api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, null));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("주인 없는 API 취소에 ID가 없으면 MANUAL_REVIEW — 환불에 묶을 근거가 없다")
    void manualReviewWhenUnboundCancellationHasNoId() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID, api(null, PortOneCancellationStatus.REQUESTED, AFTER_REQUEST));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("이 환불보다 먼저 요청된 주인 없는 API 취소가 실패로 끝났으면 무시하고 한 번 POST한다")
    void postWhenOnlyPastUnboundFailed() {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID,
            api(OTHER_CANCELLATION_ID, PortOneCancellationStatus.FAILED, BEFORE_REQUEST));
        givenPost(PortOneCancelOutcome.SUCCEEDED);
        givenApplied(refund, MartRejectionOutcome.COMPLETED);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        verify(portOnePaymentClient).cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationStatus.class, names = {"REQUESTED", "UNKNOWN", "SUCCEEDED"})
    @DisplayName("이 환불보다 먼저 요청된 주인 없는 API 취소가 실패로 끝나지 않았으면 MANUAL_REVIEW — 과거 취소를 묶지도 새로 취소하지도 않는다")
    void manualReviewWhenPastUnboundActive(PortOneCancellationStatus status) {
        // given — 과거 회원 취소가 PG에서는 접수·성공했지만 경합으로 환불 행에 ID가 남지 못했다
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID, api(OTHER_CANCELLATION_ID, status, BEFORE_REQUEST));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("이 환불 이후 후보가 하나여도 다른 환불에 묶인 API 취소가 살아 있으면 MANUAL_REVIEW — 반영도 POST도 하지 않는다")
    void manualReviewWhenCurrentAndBoundActive() {
        // given
        givenRequestedRefund();
        givenBound(MEMBER_CANCELLATION_ID);
        givenPortOne(PortOnePaymentStatus.PAID,
            api(MEMBER_CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, BEFORE_REQUEST),
            api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AFTER_REQUEST));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("이 환불 이후 후보가 하나여도 과거의 주인 없는 API 취소가 살아 있으면 MANUAL_REVIEW — 반영도 POST도 하지 않는다")
    void manualReviewWhenCurrentAndPastActive() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID,
            api(OTHER_CANCELLATION_ID, PortOneCancellationStatus.UNKNOWN, BEFORE_REQUEST),
            api(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AFTER_REQUEST));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("고른 취소의 금액이 환불과 다르면 MANUAL_REVIEW — 전액 취소만 반영한다")
    void manualReviewWhenCurrentAmountMismatch() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PARTIAL_CANCELED, new PortOneCancellation(CANCELLATION_ID,
            PortOneCancellationStatus.SUCCEEDED, 1000L, AFTER_REQUEST, NOW.minusSeconds(10),
            PortOneCancellationTrigger.API));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    // ---------- POST ----------

    @Test
    @DisplayName("취소 내역이 없고 PG가 PAID면 고정 서버 키·거절 사유·환불 금액으로 정확히 한 번 POST한다")
    void postOnceWhenNoCancellation() {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID);
        givenPost(PortOneCancelOutcome.SUCCEEDED);
        givenApplied(refund, MartRejectionOutcome.COMPLETED);

        // when
        facade.execute(REFUND_ID);

        // then
        verify(portOnePaymentClient).cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT);
    }

    @ParameterizedTest
    @EnumSource(value = PortOnePaymentStatus.class, names = {"CANCELED", "PARTIAL_CANCELED"})
    @DisplayName("이 환불의 취소가 없는데 PG가 PAID가 아니면 MANUAL_REVIEW — 근거 없이 취소하지 않는다")
    void manualReviewWhenPgNotPaid(PortOnePaymentStatus status) {
        // given
        givenRequestedRefund();
        givenPortOne(status);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        verifyNoPost();
        verifyNoInteractions(martRejectionProcessor);
    }

    @Test
    @DisplayName("POST가 처리 중이면 TX2 PENDING을 결과에 담아 RESCHEDULED로 돌려주고 10분 뒤 재실행을 예약한다")
    void rescheduleWhenPostRequested() {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID);
        givenPost(PortOneCancelOutcome.REQUESTED);
        givenApplied(refund, MartRejectionOutcome.PENDING);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        assertThat(execution.appliedResult()).map(MartRejectionPaymentResult::outcome)
            .contains(MartRejectionOutcome.PENDING);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.adminRefundExecute(REFUND_ID), RECHECK_AT);
    }

    @Test
    @DisplayName("POST가 성공하고 TX2가 완료하면 RESOLVED와 반영 결과를 돌려준다")
    void resolveWhenPostSucceeded() {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID);
        givenPost(PortOneCancelOutcome.SUCCEEDED);
        givenApplied(refund, MartRejectionOutcome.COMPLETED);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertThat(execution.appliedResult()).map(MartRejectionPaymentResult::outcome)
            .contains(MartRejectionOutcome.COMPLETED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @ParameterizedTest
    @EnumSource(value = MartRejectionOutcome.class, names = {"PG_FAILED", "REQUEST_REJECTED"})
    @DisplayName("PortOne이 실패·거절로 답해 TX2가 실패로 끝내면 RESOLVED와 그 실패 결과를 돌려준다")
    void resolveWhenPostFailed(MartRejectionOutcome failure) {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID);
        givenPost(failure == MartRejectionOutcome.PG_FAILED
            ? PortOneCancelOutcome.FAILED
            : PortOneCancelOutcome.REJECTED);
        givenApplied(refund, failure);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertThat(execution.appliedResult()).map(MartRejectionPaymentResult::outcome).contains(failure);
        verifyNoInteractions(paymentDelayQueue);
    }

    // ---------- PG 성공 + 로컬 실패 ----------

    @ParameterizedTest
    @EnumSource(value = MartRejectionOutcome.class, names = {"PG_FAILED", "REQUEST_REJECTED"})
    @DisplayName("POST는 성공했는데 TX2가 이미 실패한 환불을 돌려주면 MANUAL_REVIEW — 실패 결과를 응답용으로 넘기지 않는다")
    void manualReviewWhenPostSucceededButRefundFailed(MartRejectionOutcome failure) {
        // given — 잠금을 기다리는 사이 다른 경로가 이 환불을 실패로 끝냈다
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID);
        givenPost(PortOneCancelOutcome.SUCCEEDED);
        givenApplied(refund, failure);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertThat(execution.appliedResult()).isEmpty();
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("조회로 본 취소는 성공인데 TX2가 이미 실패한 환불을 돌려주면 MANUAL_REVIEW — 실패 결과를 넘기지 않는다")
    void manualReviewWhenExistingSucceededButRefundFailed() {
        // given
        Refund refund = givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.CANCELED,
            api(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AFTER_REQUEST));
        givenApplied(refund, MartRejectionOutcome.PG_FAILED);

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertThat(execution.appliedResult()).isEmpty();
        verifyNoPost();
    }

    @Test
    @DisplayName("TX2가 바인딩 경합·상태 불일치로 내부 오류를 던지면 MANUAL_REVIEW — 다시 해도 같아 예약하지 않는다")
    void manualReviewWhenApplyThrowsIllegalState() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID);
        givenPost(PortOneCancelOutcome.SUCCEEDED);
        given(martRejectionProcessor.apply(eq(PAYMENT_ID), eq(REFUND_ID), any()))
            .willThrow(new IllegalStateException("PortOne 취소 ID가 다른 환불에 이미 연결돼 있습니다."));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.MANUAL_REVIEW);
        assertThat(execution.appliedResult()).isEmpty();
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("POST 뒤 TX2가 일시적 DB 장애면 재실행을 예약한다 — 다음 실행의 GET이 그 취소를 찾아 다시 POST하지 않는다")
    void rescheduleWhenApplyHitsTransientFailure() {
        // given
        givenRequestedRefund();
        givenPortOne(PortOnePaymentStatus.PAID);
        givenPost(PortOneCancelOutcome.SUCCEEDED);
        given(martRejectionProcessor.apply(eq(PAYMENT_ID), eq(REFUND_ID), any()))
            .willThrow(new QueryTimeoutException("lock wait timeout"));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.adminRefundExecute(REFUND_ID), RECHECK_AT);
    }

    // ---------- 마무리·예약 ----------

    @Test
    @DisplayName("요청 뒤 3시간이 지나도 미확정이면 STALLED — 10분 재예약을 멈추고 환불은 REQUESTED로 둔다")
    void stallAfterRecheckWindow() {
        // given
        Refund refund = givenRequestedRefund(NOW.minusHours(3));
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.STALLED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        verifyNoInteractions(paymentDelayQueue);
    }

    @Test
    @DisplayName("재실행 예약이 Redis 오류로 실패해도 예외 없이 RESCHEDULED를 돌려준다 — 환불이 REQUESTED라 Fallback이 다시 넣는다")
    void keepRescheduledWhenRedisFails() {
        // given
        givenRequestedRefund();
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(any(), any());

        // when
        AdminRefundExecution execution = facade.execute(REFUND_ID);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
    }

    @Test
    @DisplayName("거절 접수 직후 안전망은 10분 뒤 마트 거절 환불 실행으로 등록한다")
    void scheduleSafetyNet() {
        // when
        facade.scheduleSafetyNet(REFUND_ID);

        // then
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.adminRefundExecute(REFUND_ID), RECHECK_AT);
        verifyNoInteractions(paymentService, portOnePaymentClient, martRejectionProcessor);
    }

    @Test
    @DisplayName("안전망 등록이 Redis 오류로 실패해도 예외를 던지지 않는다 — 거절 접수 결과를 바꾸지 않는다")
    void scheduleSafetyNetSwallowsRedisFailure() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(any(), any());

        // when · then
        assertThatCode(() -> facade.scheduleSafetyNet(REFUND_ID)).doesNotThrowAnyException();
    }

    // ---------- 픽스처 ----------

    private Refund givenRequestedRefund() {
        return givenRequestedRefund(REQUESTED_AT);
    }

    private Refund givenRequestedRefund(LocalDateTime requestedAt) {
        Refund refund = Refund.requestedByAdmin(cancelRequestedPayment(), REASON, "admin-key-1", requestedAt);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        given(paymentService.findRefundWithPayment(REFUND_ID)).willReturn(Optional.of(refund));
        return refund;
    }

    // 거절 접수(TX1) 뒤의 결제. setter 대신 실제 전이 메서드로 만든다
    private Payment cancelRequestedPayment() {
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(PORTONE_PAYMENT_ID)
            .amount(AMOUNT)
            .status(PaymentStatus.PAID)
            .expiresAt(NOW.minusMinutes(30))
            .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.requestCancel();
        return payment;
    }

    private void givenBound(String... cancellationIds) {
        given(paymentService.findBoundCancellationIds(PAYMENT_ID, REFUND_ID)).willReturn(Set.of(cancellationIds));
    }

    private void givenPortOne(PortOnePaymentStatus status, PortOneCancellation... cancellations) {
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(new PortOnePayment(PORTONE_PAYMENT_ID,
            "tx_1", status, AMOUNT, "KRW", NOW.minusHours(1), 1L, null, List.of(cancellations)));
    }

    private void givenPost(PortOneCancelOutcome outcome) {
        LocalDateTime canceledAt = outcome == PortOneCancelOutcome.SUCCEEDED ? NOW.minusSeconds(5) : null;
        String cancellationId = outcome == PortOneCancelOutcome.REJECTED ? null : CANCELLATION_ID;
        given(portOnePaymentClient.cancelPaymentByAdmin(PORTONE_PAYMENT_ID, SERVER_KEY, REASON, AMOUNT))
            .willReturn(new PortOneCancelResult(outcome, cancellationId, canceledAt, "cancellation " + outcome));
    }

    // TX2는 MartRejectionProcessorTest가 검증한다. 여기서는 결과 종류만 정해 실행 판정을 본다
    private void givenApplied(Refund refund, MartRejectionOutcome outcome) {
        given(martRejectionProcessor.apply(eq(PAYMENT_ID), eq(REFUND_ID), any()))
            .willReturn(new MartRejectionPaymentResult(outcome, MartRejectionResponse.of(refund.getPayment(), refund)));
    }

    private String appliedCancellationId() {
        ArgumentCaptor<PortOneCancelResult> result = ArgumentCaptor.forClass(PortOneCancelResult.class);
        verify(martRejectionProcessor).apply(eq(PAYMENT_ID), eq(REFUND_ID), result.capture());
        return result.getValue().cancellationId();
    }

    private void verifyNoPost() {
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    private static PortOneCancellation api(String id, PortOneCancellationStatus status, LocalDateTime requestedAt) {
        LocalDateTime canceledAt = status == PortOneCancellationStatus.SUCCEEDED ? NOW.minusSeconds(30) : null;
        return new PortOneCancellation(id, status, AMOUNT, requestedAt, canceledAt, PortOneCancellationTrigger.API);
    }

    // 콘솔 화면에서 운영자가 요청한 전액 취소. 성공이면 PG가 확인한 취소 시각이 있다
    private static PortOneCancellation consoleCancellation(PortOneCancellationStatus status) {
        LocalDateTime canceledAt = status == PortOneCancellationStatus.SUCCEEDED ? NOW.minusSeconds(20) : null;
        return new PortOneCancellation(CONSOLE_CANCELLATION_ID, status, AMOUNT, AFTER_REQUEST, canceledAt,
            PortOneCancellationTrigger.CONSOLE);
    }
}
