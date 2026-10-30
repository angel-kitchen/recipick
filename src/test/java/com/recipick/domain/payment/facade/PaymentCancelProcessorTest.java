package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.service.MemberCancelRequestResult;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.PaymentCancelPreparation;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import java.time.Clock;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCancelProcessorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 19, 8, 30);
    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long REFUND_ID = 55L;
    private static final String KEY = "client-key-1";
    private static final String REASON = "단순 변심";
    private static final String CANCELLATION_ID = "cancel_1";
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 23, 19, 8, 40);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 21, 10, 5);
    private static final List<StockQuantity> RESTORE_ITEMS = List.of(new StockQuantity(100L, 2));

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private PointCommandService pointCommandService;

    private PaymentCancelProcessor processor;

    @BeforeEach
    void setUp() {
        // Clock은 mock이 아니라 @InjectMocks로는 못 넣는다. 시계를 멈춰 두고 직접 조립한다
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        processor = new PaymentCancelProcessor(
            paymentService, orderService, orderFacade, pointCommandService, fixedClock);
    }

    // ---------- TX1 prepare — 결제 전 취소 (1-4a 그대로) ----------

    @Test
    @DisplayName("본인의 IN_PROGRESS 결제면 FAILED(CANCELED_BEFORE_PAY)로 바꾸고 주문 종료 → 포인트 복원 → 재고 복구 순으로 부른 뒤 TX1에서 끝난다")
    void prepareCancelsInProgress() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(orderFacade.closeByPaymentFailure(ORDER_ID, CancelReasonCode.MEMBER_CANCELED, REASON))
            .willReturn(RESTORE_ITEMS);

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — PG를 거치지 않으니 거래번호는 비어 있고 PortOne 호출도 없다. 응답은 TX1 안에서 만들어진다
        assertThat(preparation.requiresPortOneCall()).isFalse();
        assertThat(preparation.finishedResult().outcome()).isEqualTo(PaymentCancelOutcome.CANCELED_BEFORE_PAY);
        PaymentCancelResponse response = preparation.finishedResult().response();
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo(PaymentFailureReason.CANCELED_BEFORE_PAY);
        assertThat(response.canceledAt()).isNull();
        assertThat(response.refundStatus()).isNull();
        assertThat(response.refundAmount()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.CANCELED_BEFORE_PAY);
        assertThat(payment.getPortoneTransactionId()).isNull();
        InOrder inOrder = inOrder(orderFacade, pointCommandService);
        inOrder.verify(orderFacade).closeByPaymentFailure(ORDER_ID, CancelReasonCode.MEMBER_CANCELED, REASON);
        inOrder.verify(pointCommandService).restoreUsageAndCancelEarning(ORDER_ID);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
        verifyNoMoreInteractions(orderFacade);
        verify(paymentService, never()).requestMemberRefund(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("다른 회원의 결제면 존재를 숨기고 PAYMENT_NOT_FOUND다. 상태도 바꾸지 않고 키 조회도 하지 않는다")
    void prepareRejectsOtherMembersPayment() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(99L);

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_FOUND);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        verify(paymentService, never()).findRefundByIdempotencyKey(anyString());
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("이미 결제 전 취소된 결제라도 남의 것이면 멱등 200이 아니라 PAYMENT_NOT_FOUND다")
    void prepareRejectsOtherMembersCanceledPayment() {
        // given — 소유자 확인이 멱등 판정보다 앞이어야 남의 결제가 취소됐는지를 응답 차이로 알 수 없다
        given(paymentService.getByIdForUpdate(PAYMENT_ID))
            .willReturn(failedPayment(PaymentFailureReason.CANCELED_BEFORE_PAY));
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(99L);

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_FOUND);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("이미 FAILED(CANCELED_BEFORE_PAY)면 아무것도 바꾸지 않고 CANCELED_BEFORE_PAY로 끝난다")
    void prepareReturnsCanceledBeforePayAgain() {
        // given
        Payment payment = failedPayment(PaymentFailureReason.CANCELED_BEFORE_PAY);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — 두 번째 요청이 재고를 또 올리면 안 된다
        assertThat(preparation.finishedResult().outcome()).isEqualTo(PaymentCancelOutcome.CANCELED_BEFORE_PAY);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentFailureReason.class, names = {"EXPIRED", "PG_FAILED", "AMOUNT_MISMATCH"})
    @DisplayName("다른 사유로 실패한 결제는 취소된 게 아니므로 멱등 200이 아니라 INVALID_PAYMENT_STATUS다")
    void prepareRejectsFailedForOtherReason(PaymentFailureReason reason) {
        // given
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(failedPayment(reason));
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.INVALID_PAYMENT_STATUS);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("이미 취소 완료된 결제에 다른 키로 오면 INVALID_PAYMENT_STATUS다")
    void prepareRejectsCanceled() {
        // given
        Payment payment = payment(PaymentStatus.CANCELED);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.INVALID_PAYMENT_STATUS);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("다른 키로 취소가 진행 중이면 DUPLICATE_REQUEST_IN_PROGRESS고 새 환불을 만들지 않는다")
    void prepareRejectsCancelRequestedByOtherKey() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
        verify(paymentService, never()).requestMemberRefund(any(), anyString(), anyString());
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    // ---------- TX1 prepare — 결제 후 취소 접수 ----------

    @Test
    @DisplayName("PAID 결제면 주문을 취소 요청으로 바꾼 뒤 결제도 CANCEL_REQUESTED로 바꾸고 환불 행을 만들어 PortOne 호출 인자를 돌려준다")
    void prepareRequestsCancelAfterPay() {
        // given
        Payment payment = payment(PaymentStatus.PAID);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(orderService.markMemberCancelRequested(ORDER_ID, REASON)).willReturn(MemberCancelRequestResult.REQUESTED);
        given(paymentService.requestMemberRefund(payment, REASON, KEY))
            .willAnswer(invocation -> memberRefund(payment, KEY, NOW));

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — 잠금 순서대로 주문(ORDERS)이 먼저, 그다음 환불 행이다
        assertThat(preparation.requiresPortOneCall()).isTrue();
        assertThat(preparation.refundId()).isEqualTo(REFUND_ID);
        assertThat(preparation.portonePaymentId()).isEqualTo("pay_1");
        assertThat(preparation.amount()).isEqualTo(23800L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        InOrder inOrder = inOrder(orderService, paymentService);
        inOrder.verify(orderService).markMemberCancelRequested(ORDER_ID, REASON);
        inOrder.verify(paymentService).requestMemberRefund(payment, REASON, KEY);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("마트가 이미 준비를 시작했으면 ORDER_ALREADY_PREPARING이고 결제·환불은 손대지 않는다")
    void prepareRejectsWhenMartPreparing() {
        // given
        Payment payment = payment(PaymentStatus.PAID);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(orderService.markMemberCancelRequested(ORDER_ID, REASON))
            .willReturn(MemberCancelRequestResult.MART_ALREADY_PREPARING);

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.ORDER_ALREADY_PREPARING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentService, never()).requestMemberRefund(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("주문이 PAID가 아니라고 답하면 INVALID_PAYMENT_STATUS고 결제·환불은 손대지 않는다")
    void prepareRejectsWhenOrderStatusInvalid() {
        // given — 결제는 PAID인데 주문이 아닌 경우. 데이터 불일치라 결제 쪽에서 억지로 맞추지 않는다
        Payment payment = payment(PaymentStatus.PAID);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(orderService.markMemberCancelRequested(ORDER_ID, REASON))
            .willReturn(MemberCancelRequestResult.INVALID_ORDER_STATUS);

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.INVALID_PAYMENT_STATUS);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentService, never()).requestMemberRefund(any(), anyString(), anyString());
    }

    // ---------- TX1 prepare — 같은 키 재요청 ----------

    @Test
    @DisplayName("같은 키의 환불이 COMPLETED면 PortOne을 부르지 않고 CANCELED로 끝난다")
    void replayCompleted() {
        // given
        Payment payment = payment(PaymentStatus.CANCELED);
        Refund refund = memberRefund(payment, KEY, NOW.minusMinutes(10));
        refund.complete(CANCELLATION_ID, CANCELED_AT);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.findRefundByIdempotencyKey(KEY)).willReturn(Optional.of(refund));

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — 응답은 기존 환불 행으로 만든다
        assertThat(preparation.requiresPortOneCall()).isFalse();
        assertThat(preparation.finishedResult().outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        assertThat(preparation.finishedResult().response().refundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(preparation.finishedResult().response().refundAmount()).isEqualTo(23800L);
        verify(orderService, never()).markMemberCancelRequested(anyLong(), anyString());
        verify(paymentService, never()).requestMemberRefund(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @CsvSource({
        "PG_FAILED, PG_FAILED",
        "REQUEST_REJECTED, REQUEST_REJECTED"
    })
    @DisplayName("같은 키의 환불이 FAILED면 실패 종류대로 원래 결과(409 / 500)를 재현하고 PortOne은 부르지 않는다")
    void replayFailed(RefundFailureType failureType, PaymentCancelOutcome expected) {
        // given — 되돌린 결제는 PAID다
        Payment payment = payment(PaymentStatus.PAID);
        Refund refund = memberRefund(payment, KEY, NOW.minusMinutes(10));
        refund.fail(failureType == RefundFailureType.PG_FAILED ? CANCELLATION_ID : null, failureType, "detail");
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.findRefundByIdempotencyKey(KEY)).willReturn(Optional.of(refund));

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — PAID 결제인데 새 취소 요청을 시작하지 않는다. 같은 키는 같은 환불 작업이다
        assertThat(preparation.requiresPortOneCall()).isFalse();
        assertThat(preparation.finishedResult().outcome()).isEqualTo(expected);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderService, never()).markMemberCancelRequested(anyLong(), anyString());
    }

    @Test
    @DisplayName("같은 키의 환불이 REQUESTED이고 취소 ID가 없고 3시간 안이면 같은 서버 키로 PortOne을 다시 부른다")
    void replayRequestedRetriesWithinWindow() {
        // given — 2시간 59분 전 요청. 응답을 못 받은 채 서버가 죽었던 경우다
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusHours(2).minusMinutes(59));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.findRefundByIdempotencyKey(KEY)).willReturn(Optional.of(refund));

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — 새 환불 행을 만들지 않고 기존 행의 ID로 부른다
        assertThat(preparation.requiresPortOneCall()).isTrue();
        assertThat(preparation.refundId()).isEqualTo(REFUND_ID);
        assertThat(preparation.portonePaymentId()).isEqualTo("pay_1");
        assertThat(preparation.amount()).isEqualTo(23800L);
        verify(paymentService, never()).requestMemberRefund(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("같은 키의 환불이 REQUESTED여도 3시간이 지났으면 재호출 없이 PENDING이다 — 정합화(1-5) 몫")
    void replayRequestedAfterWindow() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusHours(3));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.findRefundByIdempotencyKey(KEY)).willReturn(Optional.of(refund));

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — REQUESTED여도 refundAmount는 이번 환불 작업의 금액(전액)이다
        assertThat(preparation.requiresPortOneCall()).isFalse();
        assertThat(preparation.finishedResult().outcome()).isEqualTo(PaymentCancelOutcome.PENDING);
        assertThat(preparation.finishedResult().response().paymentStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(preparation.finishedResult().response().refundStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(preparation.finishedResult().response().refundAmount()).isEqualTo(23800L);
    }

    @Test
    @DisplayName("같은 키의 환불에 취소 ID가 이미 있으면 PortOne이 작업을 만든 것이라 재호출 없이 PENDING이다")
    void replayRequestedWithCancellationId() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusMinutes(1));
        refund.recordRequested(CANCELLATION_ID);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.findRefundByIdempotencyKey(KEY)).willReturn(Optional.of(refund));

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then
        assertThat(preparation.requiresPortOneCall()).isFalse();
        assertThat(preparation.finishedResult().outcome()).isEqualTo(PaymentCancelOutcome.PENDING);
    }

    @Test
    @DisplayName("같은 키가 다른 결제의 환불에 쓰였으면 IDEMPOTENCY_KEY_PAYLOAD_MISMATCH다")
    void replayRejectsDifferentPayment() {
        // given
        Payment payment = payment(PaymentStatus.PAID);
        Payment otherPayment = payment(99L, PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(otherPayment, KEY, NOW.minusMinutes(1));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.findRefundByIdempotencyKey(KEY)).willReturn(Optional.of(refund));

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderService, never()).markMemberCancelRequested(anyLong(), anyString());
    }

    @Test
    @DisplayName("같은 키인데 사유가 다르면 IDEMPOTENCY_KEY_PAYLOAD_MISMATCH다")
    void replayRejectsDifferentReason() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusMinutes(1));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.findRefundByIdempotencyKey(KEY)).willReturn(Optional.of(refund));

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, "배송 지연"));

        // then
        assertErrorCode(thrown, CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
    }

    @Test
    @DisplayName("같은 키가 회원 환불이 아닌 행(SYSTEM 등)에 쓰였으면 IDEMPOTENCY_KEY_PAYLOAD_MISMATCH다")
    void replayRejectsNonMemberRefund() {
        // given — idempotency_key는 REFUND 전체에서 UNIQUE라 다른 종류의 환불 행이 걸릴 수 있다
        Payment payment = payment(PaymentStatus.PAID);
        Refund systemRefund = Refund.requestedBySystem(payment, 23800L, REASON, KEY, NOW.minusMinutes(1));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.findRefundByIdempotencyKey(KEY)).willReturn(Optional.of(systemRefund));

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        verify(orderService, never()).markMemberCancelRequested(anyLong(), anyString());
    }

    // ---------- TX2 apply ----------

    @Test
    @DisplayName("SUCCEEDED면 결제 CANCELED·환불 COMPLETED로 바꾸고 주문 환불 완료 → 포인트 복원 → 재고 복구 순으로 부른다")
    void applySucceeded() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusSeconds(10));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);
        given(orderFacade.markRefunded(ORDER_ID)).willReturn(RESTORE_ITEMS);

        // when
        PaymentCancelResult result = processor.apply(PAYMENT_ID, REFUND_ID, succeeded(CANCELED_AT));

        // then — 시각은 둘 다 PG가 준 값이다. 응답도 같은 트랜잭션 안의 값이다
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        assertThat(result.response().paymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.response().canceledAt()).isEqualTo(CANCELED_AT);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(result.response().refundAmount()).isEqualTo(23800L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isEqualTo(CANCELED_AT);
        InOrder inOrder = inOrder(orderFacade, pointCommandService);
        inOrder.verify(orderFacade).markRefunded(ORDER_ID);
        inOrder.verify(pointCommandService).restoreUsageAndCancelEarning(ORDER_ID);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("SUCCEEDED인데 취소 시각이 없으면 완료로 단정하지 않고 취소 ID만 남기고 PENDING이다")
    void applySucceededWithoutCanceledAt() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusSeconds(10));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        PaymentCancelResult result = processor.apply(PAYMENT_ID, REFUND_ID, succeeded(null));

        // then
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.PENDING);
        assertThat(result.response().paymentStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(result.response().refundAmount()).isEqualTo(23800L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        verifyNoInteractions(orderFacade, orderService, pointCommandService);
    }

    @Test
    @DisplayName("REQUESTED면 취소 ID만 남기고 상태는 그대로 두며 PENDING이다")
    void applyRequested() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusSeconds(10));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        PaymentCancelResult result = processor.apply(PAYMENT_ID, REFUND_ID,
            new PortOneCancelResult(PortOneCancelOutcome.REQUESTED, CANCELLATION_ID, null, "cancellation REQUESTED"));

        // then — 202 응답에도 refundAmount는 전액이다 (결제 조회의 COMPLETED 합계와 다른 뜻)
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.PENDING);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(result.response().refundAmount()).isEqualTo(23800L);
        assertThat(result.response().canceledAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        verifyNoInteractions(orderFacade, orderService, pointCommandService);
    }

    @Test
    @DisplayName("FAILED면 환불 FAILED(PG_FAILED)에 취소 ID를 남기고 결제·주문을 PAID로 되돌린 뒤 PG_FAILED를 돌려준다")
    void applyFailed() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusSeconds(10));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        PaymentCancelResult result = processor.apply(PAYMENT_ID, REFUND_ID,
            new PortOneCancelResult(PortOneCancelOutcome.FAILED, CANCELLATION_ID, null, "cancellation FAILED"));

        // then — 예외가 아니라 값으로 돌려줘야 Facade가 커밋 뒤에 409를 던질 수 있다
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.PG_FAILED);
        assertThat(result.response().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.PG_FAILED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getFailureReason()).isEqualTo("cancellation FAILED");
        verify(orderService).restorePaidAfterMemberCancelFailure(ORDER_ID);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("400·401·403 거절이면 환불 FAILED(REQUEST_REJECTED)에 취소 ID 없이 기록하고 PAID로 되돌린 뒤 REQUEST_REJECTED를 돌려준다")
    void applyRejected() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusSeconds(10));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        PaymentCancelResult result = processor.apply(PAYMENT_ID, REFUND_ID,
            new PortOneCancelResult(PortOneCancelOutcome.REJECTED, null, null, "HTTP 401 UNAUTHORIZED"));

        // then
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.REQUEST_REJECTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.REQUEST_REJECTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        assertThat(refund.getFailureReason()).isEqualTo("HTTP 401 UNAUTHORIZED");
        verify(orderService).restorePaidAfterMemberCancelFailure(ORDER_ID);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancelOutcome.class, names = {"MISMATCH", "UNCONFIRMED"})
    @DisplayName("불일치·결과 불명이면 아무것도 바꾸지 않고 PENDING이다 — 돈이 나갔는데 주문이 살아나는 사고를 막기 위해")
    void applyLeavesStateWhenUnknown(PortOneCancelOutcome portOneOutcome) {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(payment, KEY, NOW.minusSeconds(10));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        PaymentCancelResult result = processor.apply(PAYMENT_ID, REFUND_ID,
            new PortOneCancelResult(portOneOutcome, null, null, "HTTP 409 PAYMENT_ALREADY_CANCELLED"));

        // then
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.PENDING);
        assertThat(result.response().paymentStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        verifyNoInteractions(orderFacade, orderService, pointCommandService);
    }

    @Test
    @DisplayName("환불이 이미 COMPLETED면 받은 결과와 관계없이 다시 바꾸지 않고 CANCELED다")
    void applyWhenRefundAlreadyCompleted() {
        // given — 같은 키의 동시 재요청이 먼저 완료시킨 뒤 이쪽은 처리 중 409(UNCONFIRMED)를 받은 경우
        Payment payment = payment(PaymentStatus.CANCELED);
        Refund refund = memberRefund(payment, KEY, NOW.minusSeconds(10));
        refund.complete(CANCELLATION_ID, CANCELED_AT);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        PaymentCancelResult result = processor.apply(PAYMENT_ID, REFUND_ID,
            new PortOneCancelResult(PortOneCancelOutcome.UNCONFIRMED, null, null,
                "HTTP 409 IDEMPOTENCY_OUTSTANDING_REQUEST"));

        // then
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verifyNoInteractions(orderFacade, orderService, pointCommandService);
    }

    @Test
    @DisplayName("환불이 이미 FAILED면 실패 종류대로 원래 결과를 돌려주고 다시 바꾸지 않는다")
    void applyWhenRefundAlreadyFailed() {
        // given
        Payment payment = payment(PaymentStatus.PAID);
        Refund refund = memberRefund(payment, KEY, NOW.minusSeconds(10));
        refund.fail(null, RefundFailureType.REQUEST_REJECTED, "HTTP 401 UNAUTHORIZED");
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        PaymentCancelResult result = processor.apply(PAYMENT_ID, REFUND_ID, succeeded(CANCELED_AT));

        // then
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.REQUEST_REJECTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        verifyNoInteractions(orderFacade, orderService, pointCommandService);
    }

    @Test
    @DisplayName("환불 행이 다른 결제의 것이면 호출자 버그라 IllegalStateException이고 아무것도 바꾸지 않는다")
    void applyRejectsRefundOfOtherPayment() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Payment otherPayment = payment(99L, PaymentStatus.CANCEL_REQUESTED);
        Refund refund = memberRefund(otherPayment, KEY, NOW.minusSeconds(10));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        Throwable thrown = catchThrowable(() -> processor.apply(PAYMENT_ID, REFUND_ID, succeeded(CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        verifyNoInteractions(orderFacade, orderService, pointCommandService);
    }

    @Test
    @DisplayName("같은 결제의 환불이라도 회원 환불이 아니면(SYSTEM 등) 호출자 버그라 IllegalStateException이다")
    void applyRejectsNonMemberRefund() {
        // given
        Payment payment = payment(PaymentStatus.CANCEL_REQUESTED);
        Refund systemRefund = Refund.requestedBySystem(payment, 23800L, REASON, "system-key", NOW.minusSeconds(10));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(systemRefund);

        // when
        Throwable thrown = catchThrowable(() -> processor.apply(PAYMENT_ID, REFUND_ID, succeeded(CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(systemRefund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        verifyNoInteractions(orderFacade, orderService, pointCommandService);
    }

    // ---------- TX1 prepare — 0원 결제(POINTS_ONLY) 취소 ----------

    @Test
    @DisplayName("본인의 0원 결제면 PG·환불 행 없이 주문 취소 접수 → 결제 CANCELED → 주문 환불 완료 → 포인트 복원 → 재고 복구까지 TX1에서 끝낸다")
    void prepareCancelsPointsOnly() {
        // given
        Payment payment = pointsOnlyPayment();
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(orderService.markMemberCancelRequested(ORDER_ID, REASON)).willReturn(MemberCancelRequestResult.REQUESTED);
        given(orderFacade.markRefunded(ORDER_ID)).willReturn(RESTORE_ITEMS);

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — 취소 시각은 멈춘 시계의 서버 시각이고 환불 작업은 없다
        assertThat(preparation.requiresPortOneCall()).isFalse();
        assertThat(preparation.finishedResult().outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        PaymentCancelResponse response = preparation.finishedResult().response();
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(response.canceledAt()).isEqualTo(NOW);
        assertThat(response.refundStatus()).isNull();
        assertThat(response.refundAmount()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(NOW);
        InOrder inOrder = inOrder(orderService, orderFacade, pointCommandService);
        inOrder.verify(orderService).markMemberCancelRequested(ORDER_ID, REASON);
        inOrder.verify(orderFacade).markRefunded(ORDER_ID);
        inOrder.verify(pointCommandService).restoreUsageAndCancelEarning(ORDER_ID);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
        verify(paymentService, never()).findRefundByIdempotencyKey(anyString());
        verify(paymentService, never()).requestMemberRefund(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("이미 취소된 0원 결제에 다시 요청하면 키와 관계없이 자산을 다시 움직이지 않고 같은 CANCELED 결과를 돌려준다")
    void prepareReplaysCanceledPointsOnly() {
        // given — 첫 취소가 이미 커밋됐다
        Payment payment = pointsOnlyPayment();
        payment.cancelPointsOnly(CANCELED_AT);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        PaymentCancelPreparation preparation = processor.prepare(MEMBER_ID, PAYMENT_ID, "client-key-2", REASON);

        // then
        assertThat(preparation.requiresPortOneCall()).isFalse();
        assertThat(preparation.finishedResult().outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        assertThat(preparation.finishedResult().response().canceledAt()).isEqualTo(CANCELED_AT);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
        verify(orderService, never()).markMemberCancelRequested(anyLong(), anyString());
        verifyNoInteractions(orderFacade, pointCommandService);
        verify(paymentService, never()).findRefundByIdempotencyKey(anyString());
    }

    @Test
    @DisplayName("마트가 준비를 시작한 0원 결제 주문은 ORDER_ALREADY_PREPARING이고 결제·포인트·재고를 바꾸지 않는다")
    void prepareRejectsPointsOnlyWhenMartPreparing() {
        // given
        Payment payment = pointsOnlyPayment();
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(orderService.markMemberCancelRequested(ORDER_ID, REASON))
            .willReturn(MemberCancelRequestResult.MART_ALREADY_PREPARING);

        // when
        Throwable thrown = catchThrowable(() -> processor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.ORDER_ALREADY_PREPARING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getCanceledAt()).isNull();
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    private Payment pointsOnlyPayment() {
        Payment payment = Payment.pointsOnly(ORDER_ID, NOW.minusMinutes(10));
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    private Payment payment(PaymentStatus status) {
        return payment(PAYMENT_ID, status);
    }

    private Payment payment(Long paymentId, PaymentStatus status) {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(status)
            .expiresAt(EXPIRES_AT)
            .build();
        // 같은 키 재요청이 환불 행의 결제 ID와 비교하므로 id가 있어야 한다
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    // fail()은 IN_PROGRESS에서만 허용되므로 실제 전이를 거쳐 만든다
    private Payment failedPayment(PaymentFailureReason reason) {
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        payment.fail(reason, null);
        return payment;
    }

    // requestedByMember는 CANCEL_REQUESTED 결제만 받으므로, 다른 상태의 결제에 붙일 때는 잠시 바꿔서 만든다
    private Refund memberRefund(Payment payment, String key, LocalDateTime requestedAt) {
        PaymentStatus original = payment.getStatus();
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.CANCEL_REQUESTED);
        Refund refund = Refund.requestedByMember(payment, REASON, key, requestedAt);
        ReflectionTestUtils.setField(payment, "status", original);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    private static PortOneCancelResult succeeded(LocalDateTime canceledAt) {
        return new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, CANCELLATION_ID, canceledAt,
            "cancellation SUCCEEDED");
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
