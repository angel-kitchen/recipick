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

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.service.MartRejectRequestResult;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.dto.MartRejectionPreparation;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.MartRejectionOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.AuthErrorCode;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MartRejectionProcessorTest {

    private static final Long ORDER_ID = 1L;
    private static final Long MART_ID = 3L;
    private static final Long OTHER_MART_ID = 4L;
    private static final Long ADMIN_ID = 17L;
    private static final String IDEMPOTENCY_KEY = "admin-key-1";
    private static final Long PAYMENT_ID = 10L;
    private static final Long OTHER_PAYMENT_ID = 20L;
    private static final Long REFUND_ID = 77L;
    private static final Long OTHER_REFUND_ID = 78L;
    private static final Long AMOUNT = 23800L;
    private static final String CANCELLATION_ID = "cancel_admin";
    private static final String REASON = "상품 준비가 어렵습니다.";
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 28, 14, 0, 0);
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 28, 14, 0, 40);
    private static final LocalDateTime EXTERNAL_CANCELED_AT = LocalDateTime.of(2026, 10, 28, 15, 30, 0);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 28, 13, 50, 0);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 0원 결제 거절의 취소 시각은 서버 시각이라 시계를 멈춰 둔다
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 14, 0, 30);

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private PointCommandService pointCommandService;

    @Mock
    private AdminAuditRecorder adminAuditRecorder;

    private MartRejectionProcessor processor;

    @BeforeEach
    void setUp() {
        // POINTS_ONLY 취소 시각을 고정된 Clock으로 검증하기 위해 직접 조립한다
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        processor = new MartRejectionProcessor(
            paymentService, orderService, orderFacade, pointCommandService, adminAuditRecorder, fixedClock);
    }

    @Test
    @DisplayName("SUCCEEDED면 결제 CANCELED·환불 COMPLETED로 바꾼 뒤 기존 환불 완료 경로로 주문을 닫고 포인트 복원 → 재고 복구 순으로 부른다")
    void completeWhenSucceeded() {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        stub(payment, refund);
        List<StockQuantity> items = List.of(new StockQuantity(100L, 2));
        // 주문을 닫는 시점에 결제·환불이 이미 바뀌어 있어야 한다 — 같은 트랜잭션 안의 순서를 고정한다
        given(orderFacade.markRefunded(ORDER_ID)).willAnswer(invocation -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            return items;
        });

        // when
        MartRejectionPaymentResult result =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT));

        // then
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isEqualTo(CANCELED_AT);
        InOrder order = inOrder(orderFacade, pointCommandService);
        order.verify(orderFacade).markRefunded(ORDER_ID);
        order.verify(pointCommandService).restoreUsageAndCancelEarning(ORDER_ID);
        order.verify(orderFacade).restoreStock(items);
        // 응답은 같은 트랜잭션·같은 시점의 결제·환불로 만든다
        assertThat(result.response().orderId()).isEqualTo(ORDER_ID);
        assertThat(result.response().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.response().paymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.response().canceledAt()).isEqualTo(CANCELED_AT);
        assertThat(result.response().refundId()).isEqualTo(REFUND_ID);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(result.response().refundAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("SUCCEEDED인데 취소 시각이 없으면 완료로 단정하지 않고 취소 ID만 남겨 PENDING이다")
    void pendingWhenSucceededWithoutCanceledAt() {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        stub(payment, refund);

        // when
        MartRejectionPaymentResult result =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, null));

        // then
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.PENDING);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("REQUESTED면 PG 비동기 처리 중이라 취소 ID만 남기고 PENDING이다")
    void pendingWhenRequested() {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        stub(payment, refund);

        // when
        MartRejectionPaymentResult result =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.REQUESTED, null));

        // then
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.PENDING);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("PortOne이 FAILED를 명시하면 결제만 PAID로 되돌리고 주문은 건드리지 않는다 — 거절된 주문은 정상 접수로 돌아가지 않는다")
    void failWhenPortOneFailed() {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        stub(payment, refund);

        // when
        MartRejectionPaymentResult result =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.FAILED, null));

        // then
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.PG_FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.PG_FAILED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("PortOne이 처리 전에 거절하면 취소 ID 없이 실패로 끝내고 결제만 PAID로 되돌린다")
    void failWhenRequestRejected() {
        // given — 400·401·403 응답에는 cancellation 객체가 없다
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        stub(payment, refund);

        // when
        MartRejectionPaymentResult result = processor.apply(PAYMENT_ID, REFUND_ID,
            new PortOneCancelResult(PortOneCancelOutcome.REJECTED, null, null, "HTTP 401 UNAUTHORIZED"));

        // then
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.REQUEST_REJECTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.REQUEST_REJECTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancelOutcome.class, names = {"MISMATCH", "UNCONFIRMED"})
    @DisplayName("결과를 알 수 없으면 실패로 단정하지 않고 아무것도 바꾸지 않은 채 PENDING이다")
    void keepPendingWhenResultUnknown(PortOneCancelOutcome outcome) {
        // given — 404·409·timeout·5xx는 취소 ID가 없다
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        stub(payment, refund);

        // when
        MartRejectionPaymentResult result = processor.apply(PAYMENT_ID, REFUND_ID,
            new PortOneCancelResult(outcome, null, null, "HTTP 504"));

        // then
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.PENDING);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("이미 완료된 환불과 CANCELED 결제면 다시 반영하지 않고 COMPLETED를 돌려준다 — 재고도 다시 복구하지 않는다")
    void returnCompletedWhenAlreadyCompleted() {
        // given — 웹훅이 먼저 끝냈다
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        payment.markCanceled(CANCELED_AT);
        refund.complete(CANCELLATION_ID, CANCELED_AT);
        stub(payment, refund);

        // when
        MartRejectionPaymentResult result =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT));

        // then
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.COMPLETED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("완료된 환불인데 결제가 CANCELED가 아니면 이미 반영된 것으로 덮지 않고 내부 오류로 멈춘다")
    void rejectCompletedRefundWithPaidPayment() {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        refund.complete(CANCELLATION_ID, CANCELED_AT);
        payment.restorePaid();
        stub(payment, refund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @ParameterizedTest
    @EnumSource(RefundFailureType.class)
    @DisplayName("이미 실패한 환불과 PAID 결제면 늦게 온 결과와 관계없이 바꾸지 않고 원래 실패 결과를 돌려준다")
    void returnOriginalFailureWhenFailedAndPaid(RefundFailureType failureType) {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        fail(refund, failureType);
        payment.restorePaid();
        stub(payment, refund);

        // when
        MartRejectionPaymentResult result =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT));

        // then
        assertThat(result.outcome()).isEqualTo(expectedOutcome(failureType));
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(failureType);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @ParameterizedTest
    @EnumSource(RefundFailureType.class)
    @DisplayName("이미 실패한 환불과 CANCELED 결제도 정상 조합이다 — 콘솔 전액 취소로 복구된 뒤의 상태")
    void returnOriginalFailureWhenFailedAndCanceled(RefundFailureType failureType) {
        // given — ADMIN 환불은 FAILED 이력으로 남고 결제는 외부 취소로 닫혔다
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        fail(refund, failureType);
        payment.restorePaid();
        payment.markExternallyCanceled(EXTERNAL_CANCELED_AT);
        stub(payment, refund);

        // when
        MartRejectionPaymentResult result =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.REQUESTED, null));

        // then
        assertThat(result.outcome()).isEqualTo(expectedOutcome(failureType));
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("실패한 환불인데 결제가 아직 CANCEL_REQUESTED면 내부 오류로 멈춘다")
    void rejectFailedRefundWithCancelRequestedPayment() {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        fail(refund, RefundFailureType.PG_FAILED);
        stub(payment, refund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.FAILED, null)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("진행 중 환불인데 결제가 CANCEL_REQUESTED가 아니면 반영하지 않고 내부 오류로 멈춘다")
    void rejectRequestedRefundWithPaidPayment() {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        payment.restorePaid();
        stub(payment, refund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @ParameterizedTest
    @EnumSource(value = RefundRequester.class, names = {"MEMBER", "SYSTEM", "EXTERNAL"})
    @DisplayName("마트 거절 환불이 아닌 환불을 넘기면 호출자 버그라 내부 오류다")
    void rejectNonAdminRefund(RefundRequester requester) {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = nonAdminRefund(requester);
        stub(payment, refund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("다른 결제의 환불을 넘기면 호출자 버그라 내부 오류다")
    void rejectRefundOfOtherPayment() {
        // given
        Payment payment = cancelRequestedPayment();
        Payment otherPayment = paidPayment(OTHER_PAYMENT_ID);
        otherPayment.requestCancel();
        Refund refund = adminRefund(otherPayment);
        stub(payment, refund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("환불에 이미 다른 취소 ID가 있으면 결제를 바꾸기 전에 내부 오류로 멈춘다 — 환불 하나는 취소 작업 하나다")
    void rejectDifferentExistingCancellationId() {
        // given — 처리 중 응답으로 받아 둔 ID와 다른 취소의 결과가 왔다
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        refund.recordRequested("cancel_previous");
        stub(payment, refund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cancel_previous");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("결과의 취소 ID가 다른 환불에 이미 묶여 있으면 반영하지 않고 내부 오류로 멈춘다")
    void rejectCancellationIdBoundToOtherRefund() {
        // given — 후보를 고른 뒤 잠금을 기다리는 사이 다른 경로가 같은 취소를 다른 환불에 먼저 묶었다
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        stub(payment, refund);
        Refund other = Refund.requestedByMember(payment, "단순 변심", "client-key-1", REQUESTED_AT);
        ReflectionTestUtils.setField(other, "id", OTHER_REFUND_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(other));

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verifyNoInteractions(orderFacade, pointCommandService);
    }


    @Test
    @DisplayName("결과의 취소 ID가 이미 이 환불에 묶여 있으면 그대로 반영한다 — 같은 환불의 재전송이다")
    void allowCancellationIdBoundToSameRefund() {
        // given — 처리 중 응답으로 ID를 먼저 받아 둔 환불에 완료 결과가 다시 온다
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        refund.recordRequested(CANCELLATION_ID);
        stub(payment, refund);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
        given(orderFacade.markRefunded(ORDER_ID)).willReturn(List.of());

        // when
        MartRejectionPaymentResult result =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT));

        // then
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.COMPLETED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("새 거절이면 주문 거절 → 결제 CANCEL_REQUESTED → ADMIN 환불 → 감사 로그 순으로 접수하고 실행이 필요한 PENDING을 돌려준다")
    void prepareNewRejection() {
        // given
        Payment payment = paidPayment(PAYMENT_ID);
        givenNewRejection(payment);
        given(orderService.requestMartRejection(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, REASON))
            .willReturn(MartRejectRequestResult.REJECTED);
        // 환불은 결제가 CANCEL_REQUESTED가 된 뒤에만 만들 수 있다
        given(paymentService.requestAdminRefund(payment, REASON, IDEMPOTENCY_KEY)).willAnswer(invocation -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
            return adminRefund(payment);
        });

        // when
        MartRejectionPreparation preparation = prepare(AdminRole.MART_ADMIN, MART_ID);

        // then
        assertThat(preparation.requiresExecution()).isTrue();
        assertThat(preparation.refundId()).isEqualTo(REFUND_ID);
        assertThat(preparation.result().outcome()).isEqualTo(MartRejectionOutcome.PENDING);
        assertThat(preparation.result().response().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(preparation.result().response().paymentStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(preparation.result().response().refundStatus()).isEqualTo(RefundStatus.REQUESTED);
        // 마트 범위는 잠금 전에 값으로 보고, 첫 잠금은 PAYMENT다. 주문 잠금은 requestMartRejection 안에서 그 뒤에 잡힌다
        InOrder order = inOrder(orderService, paymentService, adminAuditRecorder);
        order.verify(orderService).getMartIdOf(ORDER_ID);
        order.verify(paymentService).getByOrderIdForUpdate(ORDER_ID);
        order.verify(paymentService).findRefundByIdempotencyKey(IDEMPOTENCY_KEY);
        order.verify(orderService).requestMartRejection(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, REASON);
        order.verify(paymentService).requestAdminRefund(payment, REASON, IDEMPOTENCY_KEY);
        order.verify(adminAuditRecorder).record(any(AdminAuditCommand.class));
        // 재고는 접수 때 복구하지 않는다
        verifyNoInteractions(orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("새 거절의 감사 로그는 ORDER_REJECT 한 건이고 대상은 주문, 마트는 주문의 마트이며 변경 항목은 비어 있다")
    void recordAuditOnNewRejection() {
        // given
        Payment payment = paidPayment(PAYMENT_ID);
        givenNewRejection(payment);
        given(orderService.requestMartRejection(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, REASON))
            .willReturn(MartRejectRequestResult.REJECTED);
        given(paymentService.requestAdminRefund(payment, REASON, IDEMPOTENCY_KEY))
            .willAnswer(invocation -> adminRefund(payment));

        // when
        prepare(AdminRole.MART_ADMIN, MART_ID);

        // then
        ArgumentCaptor<AdminAuditCommand> captor = ArgumentCaptor.forClass(AdminAuditCommand.class);
        verify(adminAuditRecorder).record(captor.capture());
        AdminAuditCommand command = captor.getValue();
        assertThat(command.action()).isEqualTo(AdminAuditAction.ORDER_REJECT);
        assertThat(command.targetId()).isEqualTo(ORDER_ID);
        assertThat(command.martId()).isEqualTo(MART_ID);
        // 상태 전후는 주문 상태 이력에 남고, 거절 사유는 자유 입력 원문이라 감사 로그에 넣지 않는다
        assertThat(command.changes()).isEmpty();
    }

    @Test
    @DisplayName("SUPER_ADMIN은 토큰에 마트가 없어도 범위 검사를 통과하고 감사 로그 마트는 주문의 마트다")
    void prepareBySuperAdmin() {
        // given
        Payment payment = paidPayment(PAYMENT_ID);
        givenNewRejection(payment);
        given(orderService.requestMartRejection(AdminRole.SUPER_ADMIN, null, ADMIN_ID, ORDER_ID, REASON))
            .willReturn(MartRejectRequestResult.REJECTED);
        given(paymentService.requestAdminRefund(payment, REASON, IDEMPOTENCY_KEY))
            .willAnswer(invocation -> adminRefund(payment));

        // when
        MartRejectionPreparation preparation = prepare(AdminRole.SUPER_ADMIN, null);

        // then
        assertThat(preparation.requiresExecution()).isTrue();
        ArgumentCaptor<AdminAuditCommand> captor = ArgumentCaptor.forClass(AdminAuditCommand.class);
        verify(adminAuditRecorder).record(captor.capture());
        assertThat(captor.getValue().martId()).isEqualTo(MART_ID);
    }

    @Test
    @DisplayName("결제 전(IN_PROGRESS) 주문이면 PAYMENT-002이고 멱등 조회·주문 거절·환불·감사 로그 모두 하지 않는다")
    void rejectBeforePay() {
        // given
        given(orderService.getMartIdOf(ORDER_ID)).willReturn(MART_ID);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(inProgressPayment());

        // when
        Throwable thrown = catchThrowable(() -> prepare(AdminRole.MART_ADMIN, MART_ID));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_PAID);
        verify(paymentService, never()).findRefundByIdempotencyKey(anyString());
        verify(orderService, never()).requestMartRejection(any(), any(), any(), any(), any());
        verify(paymentService, never()).requestAdminRefund(any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @Test
    @DisplayName("다른 마트의 주문이면 AUTH-005이고 결제를 잠그거나 같은 키의 환불을 조회하지 않는다")
    void rejectOtherMartOrder() {
        // given
        given(orderService.getMartIdOf(ORDER_ID)).willReturn(OTHER_MART_ID);

        // when
        Throwable thrown = catchThrowable(() -> prepare(AdminRole.MART_ADMIN, MART_ID));

        // then
        assertErrorCode(thrown, AuthErrorCode.MART_ACCESS_DENIED);
        // 결제 상태도 멱등 결과도 다른 마트에 드러나지 않는다
        verify(paymentService, never()).getByOrderIdForUpdate(anyLong());
        verify(paymentService, never()).findRefundByIdempotencyKey(anyString());
        verify(orderService, never()).requestMartRejection(any(), any(), any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @Test
    @DisplayName("없는 주문이면 주문 조회의 ORDER-012로 끝나고 결제를 잠그거나 같은 키의 환불을 조회하지 않는다")
    void rejectMissingOrder() {
        // given
        given(orderService.getMartIdOf(ORDER_ID)).willThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        // when
        Throwable thrown = catchThrowable(() -> prepare(AdminRole.MART_ADMIN, MART_ID));

        // then
        assertErrorCode(thrown, OrderErrorCode.ORDER_NOT_FOUND);
        verify(paymentService, never()).getByOrderIdForUpdate(anyLong());
        verify(paymentService, never()).findRefundByIdempotencyKey(anyString());
        verifyNoInteractions(adminAuditRecorder);
    }

    @Test
    @DisplayName("같은 키의 환불이 진행 중이면 새 환불 없이 그 환불로 실행이 필요한 PENDING을 돌려준다")
    void replayRequestedRefund() {
        // given
        Payment payment = cancelRequestedPayment();
        givenReplay(payment, adminRefund(payment));

        // when
        MartRejectionPreparation preparation = prepare(AdminRole.MART_ADMIN, MART_ID);

        // then
        assertThat(preparation.requiresExecution()).isTrue();
        assertThat(preparation.refundId()).isEqualTo(REFUND_ID);
        assertThat(preparation.result().outcome()).isEqualTo(MartRejectionOutcome.PENDING);
        verify(orderService, never()).requestMartRejection(any(), any(), any(), any(), any());
        verify(paymentService, never()).requestAdminRefund(any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @Test
    @DisplayName("같은 키의 환불이 완료됐으면 실행 없이 COMPLETED를 그대로 돌려준다")
    void replayCompletedRefund() {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        payment.markCanceled(CANCELED_AT);
        refund.complete(CANCELLATION_ID, CANCELED_AT);
        givenReplay(payment, refund);

        // when
        MartRejectionPreparation preparation = prepare(AdminRole.MART_ADMIN, MART_ID);

        // then
        assertThat(preparation.requiresExecution()).isFalse();
        assertThat(preparation.result().outcome()).isEqualTo(MartRejectionOutcome.COMPLETED);
        assertThat(preparation.result().response().paymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(preparation.result().response().refundStatus()).isEqualTo(RefundStatus.COMPLETED);
        verify(orderService, never()).requestMartRejection(any(), any(), any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @ParameterizedTest
    @EnumSource(RefundFailureType.class)
    @DisplayName("같은 키의 환불이 실패했으면 실행 없이 원래 실패 종류를 돌려주고 새 환불을 만들지 않는다")
    void replayFailedRefund(RefundFailureType failureType) {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        fail(refund, failureType);
        payment.restorePaid();
        givenReplay(payment, refund);

        // when
        MartRejectionPreparation preparation = prepare(AdminRole.MART_ADMIN, MART_ID);

        // then
        assertThat(preparation.requiresExecution()).isFalse();
        assertThat(preparation.result().outcome()).isEqualTo(expectedOutcome(failureType));
        verify(orderService, never()).requestMartRejection(any(), any(), any(), any(), any());
        verify(paymentService, never()).requestAdminRefund(any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OTHER_REASON", "MEMBER_REFUND", "OTHER_PAYMENT"})
    @DisplayName("같은 키가 다른 사유·회원 환불·다른 결제의 환불을 가리키면 COMMON-011이다")
    void rejectReusedKeyWithDifferentPayload(String mismatch) {
        // given
        Payment payment = cancelRequestedPayment();
        Refund existing = switch (mismatch) {
            case "OTHER_REASON" ->
                refundWithId(Refund.requestedByAdmin(payment, "다른 사유", IDEMPOTENCY_KEY, REQUESTED_AT));
            case "MEMBER_REFUND" ->
                refundWithId(Refund.requestedByMember(payment, REASON, IDEMPOTENCY_KEY, REQUESTED_AT));
            case "OTHER_PAYMENT" -> {
                Payment other = paidPayment(OTHER_PAYMENT_ID);
                other.requestCancel();
                yield refundWithId(Refund.requestedByAdmin(other, REASON, IDEMPOTENCY_KEY, REQUESTED_AT));
            }
            default -> throw new IllegalArgumentException(mismatch);
        };
        givenReplay(payment, existing);

        // when
        Throwable thrown = catchThrowable(() -> prepare(AdminRole.MART_ADMIN, MART_ID));

        // then
        assertErrorCode(thrown, CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        verify(orderService, never()).requestMartRejection(any(), any(), any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @Test
    @DisplayName("다른 키로 취소가 진행 중(CANCEL_REQUESTED)이면 COMMON-006이고 주문 거절을 부르지 않는다")
    void rejectWhileCancelInProgress() {
        // given
        givenNewRejection(cancelRequestedPayment());

        // when
        Throwable thrown = catchThrowable(() -> prepare(AdminRole.MART_ADMIN, MART_ID));

        // then
        assertErrorCode(thrown, CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
        verify(orderService, never()).requestMartRejection(any(), any(), any(), any(), any());
        verify(paymentService, never()).requestAdminRefund(any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CANCELED", "FAILED"})
    @DisplayName("다른 키로 온 거절인데 결제가 PAID가 아니면 PAYMENT-007이고 주문 거절을 부르지 않는다")
    void rejectWhenPaymentNotPaid(PaymentStatus status) {
        // given
        givenNewRejection(finishedPayment(status));

        // when
        Throwable thrown = catchThrowable(() -> prepare(AdminRole.MART_ADMIN, MART_ID));

        // then
        assertErrorCode(thrown, PaymentErrorCode.INVALID_PAYMENT_STATUS);
        verify(orderService, never()).requestMartRejection(any(), any(), any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @ParameterizedTest
    @EnumSource(value = MartRejectRequestResult.class, names = {"NOT_REJECTABLE", "ALREADY_REJECTED"})
    @DisplayName("주문이 거절할 수 없거나 이미 거절됐으면(ADMIN 환불 실패 뒤 다른 키 포함) ORDER-015이고 환불을 만들지 않는다")
    void rejectWhenOrderNotRejectable(MartRejectRequestResult orderResult) {
        // given
        Payment payment = paidPayment(PAYMENT_ID);
        givenNewRejection(payment);
        given(orderService.requestMartRejection(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, REASON))
            .willReturn(orderResult);

        // when
        Throwable thrown = catchThrowable(() -> prepare(AdminRole.MART_ADMIN, MART_ID));

        // then
        assertErrorCode(thrown, OrderErrorCode.ORDER_NOT_REJECTABLE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentService, never()).requestAdminRefund(any(), any(), any());
        verifyNoInteractions(adminAuditRecorder);
    }

    @ParameterizedTest
    @CsvSource({
        "REQUESTED, PENDING",
        "COMPLETED, COMPLETED",
        "FAILED, PG_FAILED"
    })
    @DisplayName("현재 결과 조회는 환불 상태로 결과를 정한다 — 진행 중 PENDING, 완료 COMPLETED, 실패는 원래 실패 종류")
    void getCurrentResult(RefundStatus refundStatus, MartRejectionOutcome expected) {
        // given
        Payment payment = cancelRequestedPayment();
        Refund refund = adminRefund(payment);
        switch (refundStatus) {
            case COMPLETED -> {
                payment.markCanceled(CANCELED_AT);
                refund.complete(CANCELLATION_ID, CANCELED_AT);
            }
            case FAILED -> {
                fail(refund, RefundFailureType.PG_FAILED);
                payment.restorePaid();
            }
            case REQUESTED -> {
            }
        }
        given(paymentService.getById(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);

        // when
        MartRejectionPaymentResult result = processor.getCurrentResult(PAYMENT_ID, REFUND_ID);

        // then
        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.response().refundStatus()).isEqualTo(refundStatus);
        verify(paymentService, never()).getByIdForUpdate(anyLong());
    }

    // ---------- TX1 prepare — 0원 결제(POINTS_ONLY) 거절 ----------

    @Test
    @DisplayName("0원 결제의 새 거절은 PG·ADMIN 환불 없이 주문 거절 → 결제 CANCELED → 주문 환불 완료 → 포인트 복원 → 재고 복구 → 감사 로그까지 TX1에서 끝내고 COMPLETED다")
    void preparePointsOnlyRejection() {
        // given
        Payment payment = pointsOnlyPayment();
        givenPointsOnly(payment);
        given(orderService.requestMartRejection(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, REASON))
            .willReturn(MartRejectRequestResult.REJECTED);
        List<StockQuantity> items = List.of(new StockQuantity(100L, 2));
        given(orderFacade.markRefunded(ORDER_ID)).willReturn(items);

        // when
        MartRejectionPreparation preparation = prepare(AdminRole.MART_ADMIN, MART_ID);

        // then — 실행기를 부를 일이 없고, 취소 시각은 멈춘 시계의 서버 시각이며 환불 작업은 없다
        assertThat(preparation.requiresExecution()).isFalse();
        assertThat(preparation.result().outcome()).isEqualTo(MartRejectionOutcome.COMPLETED);
        assertThat(preparation.result().response().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(preparation.result().response().paymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(preparation.result().response().canceledAt()).isEqualTo(NOW);
        assertThat(preparation.result().response().refundId()).isNull();
        assertThat(preparation.result().response().refundStatus()).isNull();
        assertThat(preparation.result().response().refundAmount()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(NOW);
        InOrder order = inOrder(orderService, paymentService, orderFacade, pointCommandService, adminAuditRecorder);
        order.verify(orderService).getMartIdOf(ORDER_ID);
        order.verify(paymentService).getByOrderIdForUpdate(ORDER_ID);
        order.verify(orderService).requestMartRejection(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, REASON);
        order.verify(orderFacade).markRefunded(ORDER_ID);
        order.verify(pointCommandService).restoreUsageAndCancelEarning(ORDER_ID);
        order.verify(orderFacade).restoreStock(items);
        order.verify(adminAuditRecorder).record(any(AdminAuditCommand.class));
        verify(paymentService, never()).findRefundByIdempotencyKey(anyString());
        verify(paymentService, never()).requestAdminRefund(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("이미 거절된 0원 결제 주문을 다시 거절하면 자산·감사 로그를 다시 남기지 않고 COMPLETED다")
    void replayPointsOnlyRejection() {
        // given — 첫 거절이 이미 커밋돼 결제는 CANCELED, 주문은 마트 REJECTED다
        Payment payment = pointsOnlyPayment();
        payment.cancelPointsOnly(CANCELED_AT);
        givenPointsOnly(payment);
        given(orderService.requestMartRejection(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, REASON))
            .willReturn(MartRejectRequestResult.ALREADY_REJECTED);

        // when
        MartRejectionPreparation preparation = prepare(AdminRole.MART_ADMIN, MART_ID);

        // then
        assertThat(preparation.requiresExecution()).isFalse();
        assertThat(preparation.result().outcome()).isEqualTo(MartRejectionOutcome.COMPLETED);
        assertThat(preparation.result().response().canceledAt()).isEqualTo(CANCELED_AT);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
        verifyNoInteractions(orderFacade, pointCommandService, adminAuditRecorder);
        verify(paymentService, never()).requestAdminRefund(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("회원이 먼저 취소한 0원 결제 주문은 거절 성공으로 오인하지 않고 ORDER_NOT_REJECTABLE이다")
    void rejectPointsOnlyCanceledByMember() {
        // given — 회원 취소로 결제는 CANCELED지만 주문의 마트 상태는 REJECTED가 아니다
        Payment payment = pointsOnlyPayment();
        payment.cancelPointsOnly(CANCELED_AT);
        givenPointsOnly(payment);
        given(orderService.requestMartRejection(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, REASON))
            .willReturn(MartRejectRequestResult.NOT_REJECTABLE);

        // when
        Throwable thrown = catchThrowable(() -> prepare(AdminRole.MART_ADMIN, MART_ID));

        // then
        assertErrorCode(thrown, OrderErrorCode.ORDER_NOT_REJECTABLE);
        verifyNoInteractions(orderFacade, pointCommandService, adminAuditRecorder);
    }

    // 0원 결제는 환불 행이 없어 같은 키 조회를 하지 않는다. 주문의 마트는 MART_ID다
    private void givenPointsOnly(Payment payment) {
        given(orderService.getMartIdOf(ORDER_ID)).willReturn(MART_ID);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
    }

    private Payment pointsOnlyPayment() {
        Payment payment = Payment.pointsOnly(ORDER_ID, REQUESTED_AT.minusMinutes(30));
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    private MartRejectionPreparation prepare(AdminRole role, Long adminMartId) {
        return processor.prepare(role, adminMartId, ADMIN_ID, ORDER_ID, IDEMPOTENCY_KEY, REASON);
    }

    // 같은 키의 환불이 없는 거절 요청. 주문의 마트는 MART_ID다
    private void givenNewRejection(Payment payment) {
        given(orderService.getMartIdOf(ORDER_ID)).willReturn(MART_ID);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentService.findRefundByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(Optional.empty());
    }

    private void givenReplay(Payment payment, Refund existing) {
        given(orderService.getMartIdOf(ORDER_ID)).willReturn(MART_ID);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentService.findRefundByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(Optional.of(existing));
    }

    private void stub(Payment payment, Refund refund) {
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);
    }

    // 마트 거절 접수(TX1) 뒤의 결제. 이후 상태도 setter 대신 실제 전이 메서드로 만든다
    private Payment cancelRequestedPayment() {
        Payment payment = paidPayment(PAYMENT_ID);
        payment.requestCancel();
        return payment;
    }

    private Payment paidPayment(Long paymentId) {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_" + paymentId)
            .amount(AMOUNT)
            .status(PaymentStatus.PAID)
            .expiresAt(EXPIRES_AT)
            .build();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private Payment inProgressPayment() {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_" + PAYMENT_ID)
            .amount(AMOUNT)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(EXPIRES_AT)
            .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    // 다른 키로 거절이 올 때 PAID가 아닌 끝난 결제. 취소 완료 또는 결제 전 만료
    private Payment finishedPayment(PaymentStatus status) {
        return switch (status) {
            case CANCELED -> {
                Payment payment = cancelRequestedPayment();
                payment.markCanceled(CANCELED_AT);
                yield payment;
            }
            case FAILED -> {
                Payment payment = inProgressPayment();
                payment.fail(PaymentFailureReason.EXPIRED, null);
                yield payment;
            }
            default -> throw new IllegalArgumentException("끝난 결제 상태가 아닙니다: " + status);
        };
    }

    private static Refund refundWithId(Refund refund) {
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    private Refund adminRefund(Payment payment) {
        Refund refund = Refund.requestedByAdmin(payment, REASON, "admin-key-1", REQUESTED_AT);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    // 요청 주체마다 만들 수 있는 결제 상태가 달라 같은 결제 ID의 별도 결제로 만든다. 결제 ID가 같아야 요청 주체 검사만 걸린다
    private Refund nonAdminRefund(RefundRequester requester) {
        Payment payment = paidPayment(PAYMENT_ID);
        Refund refund = switch (requester) {
            case MEMBER -> {
                payment.requestCancel();
                yield Refund.requestedByMember(payment, "단순 변심", "client-key-1", REQUESTED_AT);
            }
            case SYSTEM -> Refund.requestedBySystem(payment, AMOUNT, "결제 확정 실패", "system-key-1", REQUESTED_AT);
            case EXTERNAL -> {
                payment.markExternallyCanceled(EXTERNAL_CANCELED_AT);
                yield Refund.completedByExternal(payment, "cancel_external", "콘솔 취소", REQUESTED_AT,
                    EXTERNAL_CANCELED_AT);
            }
            case ADMIN -> throw new IllegalArgumentException("ADMIN은 마트 거절 환불이다");
        };
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    // PortOne이 FAILED를 명시한 응답에는 취소 ID가 있고, 처리 전 거절 응답에는 없다
    private static void fail(Refund refund, RefundFailureType failureType) {
        switch (failureType) {
            case PG_FAILED -> refund.fail(CANCELLATION_ID, RefundFailureType.PG_FAILED, "cancellation FAILED");
            case REQUEST_REJECTED -> refund.fail(null, RefundFailureType.REQUEST_REJECTED, "HTTP 401 UNAUTHORIZED");
        }
    }

    private static MartRejectionOutcome expectedOutcome(RefundFailureType failureType) {
        return switch (failureType) {
            case PG_FAILED -> MartRejectionOutcome.PG_FAILED;
            case REQUEST_REJECTED -> MartRejectionOutcome.REQUEST_REJECTED;
        };
    }

    private static PortOneCancelResult result(PortOneCancelOutcome outcome, LocalDateTime canceledAt) {
        return new PortOneCancelResult(outcome, CANCELLATION_ID, canceledAt, "cancellation " + outcome);
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
