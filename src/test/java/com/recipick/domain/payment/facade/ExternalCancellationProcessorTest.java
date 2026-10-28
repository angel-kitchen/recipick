package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.service.ExternalCancellationOutcome;
import com.recipick.domain.payment.service.ExternalCancellationResult;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// 잠금 아래 재검증과 상태 전이만 본다. PortOne 상태별 판정은 RefundCancellationRulesTest,
// 배송 상태별 재고 복구는 주문 쪽 테스트, 실제 잠금 경합과 롤백 원자성은 통합 테스트가 맡는다
@ExtendWith(MockitoExtension.class)
class ExternalCancellationProcessorTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Long OTHER_PAYMENT_ID = 20L;
    private static final Long ORDER_ID = 1L;
    private static final Long REFUND_ID = 7L;
    private static final Long AMOUNT = 23800L;
    private static final Long CHARGED_AMOUNT = 25000L;
    private static final String CANCELLATION_ID = "cancel_console";
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 27, 21, 0);
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 27, 21, 0, 10);
    private static final List<StockQuantity> RESTORE_ITEMS = List.of(new StockQuantity(100L, 2));

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderFacade orderFacade;

    private ExternalCancellationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ExternalCancellationProcessor(paymentService, orderFacade);
    }

    // ---------- 반영 ----------

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationTrigger.class, names = {"CONSOLE", "PORTONE_ADMIN"})
    @DisplayName("PAID 결제에 기존 환불이 없으면 결제 CANCELED → EXTERNAL 환불 기록 → 주문 종료 → 재고 복구 순으로 반영한다")
    void reconcile(PortOneCancellationTrigger trigger) {
        // given
        Payment payment = givenLocked(PaymentStatus.PAID);
        givenNoRefunds();
        String reason = "PortOne 외부 전액 취소(" + trigger + ")";
        given(orderFacade.closeByExternalCancel(ORDER_ID, reason)).willReturn(RESTORE_ITEMS);
        PortOneCancellation cancellation = new PortOneCancellation(CANCELLATION_ID,
            PortOneCancellationStatus.SUCCEEDED, AMOUNT, REQUESTED_AT, CANCELED_AT, trigger);

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, cancellation);

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RECONCILED);
        assertThat(result.reason()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
        InOrder inOrder = inOrder(paymentService, orderFacade);
        inOrder.verify(paymentService)
            .recordExternalRefund(payment, CANCELLATION_ID, reason, REQUESTED_AT, CANCELED_AT);
        inOrder.verify(orderFacade).closeByExternalCancel(ORDER_ID, reason);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
    }

    @Test
    @DisplayName("PortOne 요청 시각이 없으면 PG가 확인한 완료 시각을 환불 요청 시각으로 쓴다 — 서버 시각으로 채우지 않는다")
    void reconcileWithoutRequestedAt() {
        // given
        Payment payment = givenLocked(PaymentStatus.PAID);
        givenNoRefunds();
        PortOneCancellation cancellation = new PortOneCancellation(CANCELLATION_ID,
            PortOneCancellationStatus.SUCCEEDED, AMOUNT, null, CANCELED_AT, PortOneCancellationTrigger.CONSOLE);

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, cancellation);

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RECONCILED);
        verify(paymentService).recordExternalRefund(
            payment, CANCELLATION_ID, "PortOne 외부 전액 취소(CONSOLE)", CANCELED_AT, CANCELED_AT);
    }

    @Test
    @DisplayName("주문이 돌려줄 재고가 없으면 빈 목록을 그대로 재고 복구에 넘긴다 — 배송 시작 주문의 판단은 주문 쪽 몫")
    void reconcileWhenNothingToRestore() {
        // given
        givenLocked(PaymentStatus.PAID);
        givenNoRefunds();
        given(orderFacade.closeByExternalCancel(ORDER_ID, "PortOne 외부 전액 취소(CONSOLE)")).willReturn(List.of());

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RECONCILED);
        verify(orderFacade).restoreStock(List.of());
    }

    // ---------- 같은 취소 ID의 환불 ----------

    @Test
    @DisplayName("같은 취소 ID가 다른 결제의 환불에 연결돼 있으면 NOT_APPLICABLE")
    void notApplicableWhenSameCancellationBelongsToOtherPayment() {
        // given
        Payment payment = givenLocked(PaymentStatus.PAID);
        Refund other = refund(payment(OTHER_PAYMENT_ID, PaymentStatus.CANCELED), RefundStatus.COMPLETED,
            RefundRequester.EXTERNAL, CANCELLATION_ID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(other));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertNothingChanged(payment, PaymentStatus.PAID);
    }

    @ParameterizedTest
    @CsvSource({"CANCELED, MEMBER", "CANCELED, EXTERNAL", "FAILED, SYSTEM", "CANCELED, ADMIN"})
    @DisplayName("같은 취소 ID의 완료 환불이 결제 상태와 정상 조합이면 ALREADY_RECONCILED — 재고를 다시 복구하지 않는다")
    void alreadyReconciled(PaymentStatus paymentStatus, RefundRequester requestedBy) {
        // given
        Payment payment = givenLocked(paymentStatus);
        givenSameCancellation(refund(payment, RefundStatus.COMPLETED, requestedBy, CANCELLATION_ID));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.ALREADY_RECONCILED);
        assertNothingChanged(payment, paymentStatus);
    }

    @ParameterizedTest
    @CsvSource({"FAILED, MEMBER", "CANCELED, SYSTEM", "PAID, EXTERNAL", "PAID, ADMIN"})
    @DisplayName("같은 취소 ID의 완료 환불이 결제 상태·요청 주체와 맞지 않으면 NOT_APPLICABLE — 이상 데이터를 멱등 성공으로 덮지 않는다")
    void notApplicableWhenCompletedRefundMismatched(PaymentStatus paymentStatus, RefundRequester requestedBy) {
        // given
        Payment payment = givenLocked(paymentStatus);
        givenSameCancellation(refund(payment, RefundStatus.COMPLETED, requestedBy, CANCELLATION_ID));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertThat(result.reason()).contains("paymentStatus=" + paymentStatus, "requestedBy=" + requestedBy);
        assertNothingChanged(payment, paymentStatus);
    }

    @ParameterizedTest
    @CsvSource({"CANCEL_REQUESTED, MEMBER", "FAILED, SYSTEM", "CANCEL_REQUESTED, ADMIN"})
    @DisplayName("같은 취소 ID의 환불이 정상적으로 진행 중이면 RETRY — 다음 실행에서 기존 환불 경로로 연결된다")
    void retryWhenSameCancellationInProgress(PaymentStatus paymentStatus, RefundRequester requestedBy) {
        // given
        Payment payment = givenLocked(paymentStatus);
        givenSameCancellation(refund(payment, RefundStatus.REQUESTED, requestedBy, CANCELLATION_ID));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RETRY);
        assertNothingChanged(payment, paymentStatus);
    }

    @ParameterizedTest
    @CsvSource({"PAID, MEMBER", "CANCELED, SYSTEM", "PAID, ADMIN"})
    @DisplayName("같은 취소 ID의 진행 중 환불이 결제 상태와 맞지 않으면 재시도하지 않고 NOT_APPLICABLE")
    void notApplicableWhenSameCancellationInProgressMismatched(
        PaymentStatus paymentStatus, RefundRequester requestedBy) {
        // given
        Payment payment = givenLocked(paymentStatus);
        givenSameCancellation(refund(payment, RefundStatus.REQUESTED, requestedBy, CANCELLATION_ID));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertNothingChanged(payment, paymentStatus);
    }

    @Test
    @DisplayName("같은 취소 ID의 환불이 FAILED면 NOT_APPLICABLE — 실패 기록을 외부 취소 성공으로 조용히 바꾸지 않는다")
    void notApplicableWhenSameCancellationFailed() {
        // given
        Payment payment = givenLocked(PaymentStatus.PAID);
        givenSameCancellation(refund(payment, RefundStatus.FAILED, RefundRequester.MEMBER, CANCELLATION_ID));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertNothingChanged(payment, PaymentStatus.PAID);
    }

    // ---------- 진행 중인 기존 환불 ----------

    @ParameterizedTest
    @CsvSource({"CANCEL_REQUESTED, MEMBER", "FAILED, SYSTEM", "CANCEL_REQUESTED, ADMIN"})
    @DisplayName("취소 ID 없는 진행 중 환불이 결제 상태와 정상 조합이면 RETRY — EXTERNAL 환불을 만들지 않는다")
    void retryWhenRequestedRefundWithoutCancellationId(PaymentStatus paymentStatus, RefundRequester requestedBy) {
        // given
        Payment payment = givenLocked(paymentStatus);
        givenRequested(refund(payment, RefundStatus.REQUESTED, requestedBy, null));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RETRY);
        assertNothingChanged(payment, paymentStatus);
    }

    @ParameterizedTest
    @CsvSource({"PAID, MEMBER", "CANCELED, SYSTEM", "PAID, ADMIN"})
    @DisplayName("취소 ID 없는 진행 중 환불이 결제 상태와 맞지 않으면 NOT_APPLICABLE — 기다려도 정상화되지 않는다")
    void notApplicableWhenRequestedRefundMismatched(PaymentStatus paymentStatus, RefundRequester requestedBy) {
        // given
        Payment payment = givenLocked(paymentStatus);
        givenRequested(refund(payment, RefundStatus.REQUESTED, requestedBy, null));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertNothingChanged(payment, paymentStatus);
    }

    @Test
    @DisplayName("진행 중 환불에 다른 PortOne 취소 ID가 이미 있으면 외부 취소로 갈아 끼우지 않고 NOT_APPLICABLE")
    void notApplicableWhenRequestedRefundHasOtherCancellationId() {
        // given
        Payment payment = givenLocked(PaymentStatus.CANCEL_REQUESTED);
        givenRequested(refund(payment, RefundStatus.REQUESTED, RefundRequester.MEMBER, "cancel_api"));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertNothingChanged(payment, PaymentStatus.CANCEL_REQUESTED);
    }

    @Test
    @DisplayName("진행 중 환불이 여러 건이면 어느 것도 고르지 않고 NOT_APPLICABLE")
    void notApplicableWhenMultipleRequestedRefunds() {
        // given
        Payment payment = givenLocked(PaymentStatus.CANCEL_REQUESTED);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of(
            refund(payment, RefundStatus.REQUESTED, RefundRequester.MEMBER, null),
            refund(payment, RefundStatus.REQUESTED, RefundRequester.MEMBER, null)));
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID)).willReturn(Optional.empty());

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertNothingChanged(payment, PaymentStatus.CANCEL_REQUESTED);
    }

    // ---------- 실패한 마트 거절 뒤 콘솔 전액 취소 ----------

    @Test
    @DisplayName("실패한 마트 거절 환불이 한 건이면 결제 CANCELED·EXTERNAL 환불 기록 뒤 환불 완료 경로로 거절 주문을 닫고 재고를 복구한다")
    void closeRejectedOrderAfterFailedAdminRefund() {
        // given — 마트 거절 뒤 PG 취소가 실패해 결제만 PAID로 돌아갔고, 운영자가 콘솔에서 전액 취소했다
        Payment payment = givenLocked(PaymentStatus.PAID);
        givenNoRefunds();
        Refund failedAdmin = refund(payment, RefundStatus.FAILED, RefundRequester.ADMIN, "cancel_api");
        given(paymentService.findFailedAdminRefunds(PAYMENT_ID)).willReturn(List.of(failedAdmin));
        given(orderFacade.markRefunded(ORDER_ID)).willReturn(RESTORE_ITEMS);
        String reason = "PortOne 외부 전액 취소(CONSOLE)";

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RECONCILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
        InOrder inOrder = inOrder(paymentService, orderFacade);
        inOrder.verify(paymentService)
            .recordExternalRefund(payment, CANCELLATION_ID, reason, REQUESTED_AT, CANCELED_AT);
        inOrder.verify(orderFacade).markRefunded(ORDER_ID);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
        verify(orderFacade, never()).closeByExternalCancel(any(), any());
        // 실패한 마트 거절 환불은 시도 이력으로 그대로 남는다
        assertThat(failedAdmin.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(failedAdmin.getPortoneCancellationId()).isEqualTo("cancel_api");
    }

    @Test
    @DisplayName("실패한 마트 거절 환불이 여러 건이면 상태를 바꾸기 전에 NOT_APPLICABLE — 어느 거절의 복구인지 단정하지 않는다")
    void notApplicableWhenMultipleFailedAdminRefunds() {
        // given
        Payment payment = givenLocked(PaymentStatus.PAID);
        givenNoRefunds();
        given(paymentService.findFailedAdminRefunds(PAYMENT_ID)).willReturn(List.of(
            refund(payment, RefundStatus.FAILED, RefundRequester.ADMIN, "cancel_api_1"),
            refund(payment, RefundStatus.FAILED, RefundRequester.ADMIN, "cancel_api_2")));

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertThat(result.reason()).contains("실패한 마트 거절 환불이 여러 건");
        assertNothingChanged(payment, PaymentStatus.PAID);
    }

    @Test
    @DisplayName("실패한 마트 거절 환불이 없으면 기존 외부 취소 경로로 주문을 닫는다 — 환불 완료 경로를 쓰지 않는다")
    void closeByExternalCancelWithoutFailedAdminRefund() {
        // given
        givenLocked(PaymentStatus.PAID);
        givenNoRefunds();
        given(paymentService.findFailedAdminRefunds(PAYMENT_ID)).willReturn(List.of());
        given(orderFacade.closeByExternalCancel(ORDER_ID, "PortOne 외부 전액 취소(CONSOLE)")).willReturn(RESTORE_ITEMS);

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RECONCILED);
        verify(orderFacade, never()).markRefunded(any());
        verify(orderFacade).restoreStock(RESTORE_ITEMS);
    }

    // ---------- 결제 상태 ----------

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("기존 환불이 없어도 결제가 PAID가 아니면 NOT_APPLICABLE — 새 EXTERNAL 환불은 PAID 결제에만 만든다")
    void notApplicableWhenPaymentNotPaid(PaymentStatus status) {
        // given
        Payment payment = givenLocked(status);
        givenNoRefunds();

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, consoleSucceeded());

        // then
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.NOT_APPLICABLE);
        assertNothingChanged(payment, status);
    }

    // ---------- 호출자 버그 ----------

    @ParameterizedTest
    @EnumSource(value = PortOneCancellationTrigger.class, names = {"API", "CHARGEBACK", "UNKNOWN"})
    @DisplayName("자동 정합화 경로가 아닌 취소를 넘기면 환불을 조회하기 전에 예외로 막는다")
    void rejectNotAutoReconcilableTrigger(PortOneCancellationTrigger trigger) {
        // given
        Payment payment = givenLocked(PaymentStatus.PAID);
        PortOneCancellation cancellation = new PortOneCancellation(CANCELLATION_ID,
            PortOneCancellationStatus.SUCCEEDED, AMOUNT, REQUESTED_AT, CANCELED_AT, trigger);

        // when
        Throwable thrown = catchThrowable(() -> processor.apply(PAYMENT_ID, cancellation));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).findRefundByPortoneCancellationId(any());
        assertNothingChanged(payment, PaymentStatus.PAID);
    }

    @ParameterizedTest
    @MethodSource("unconfirmedCancellations")
    @DisplayName("완료가 확정되지 않은 외부 취소를 넘기면 환불을 조회하기 전에 예외로 막는다")
    void rejectUnconfirmedCancellation(PortOneCancellation cancellation) {
        // given
        Payment payment = givenLocked(PaymentStatus.PAID);

        // when
        Throwable thrown = catchThrowable(() -> processor.apply(PAYMENT_ID, cancellation));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).findRefundByPortoneCancellationId(any());
        assertNothingChanged(payment, PaymentStatus.PAID);
    }

    // 처리 중, ID 없음, 완료 시각 없음
    static Stream<PortOneCancellation> unconfirmedCancellations() {
        return Stream.of(
            new PortOneCancellation(CANCELLATION_ID, PortOneCancellationStatus.REQUESTED, AMOUNT, REQUESTED_AT,
                null, PortOneCancellationTrigger.CONSOLE),
            new PortOneCancellation(null, PortOneCancellationStatus.SUCCEEDED, AMOUNT, REQUESTED_AT,
                CANCELED_AT, PortOneCancellationTrigger.CONSOLE),
            new PortOneCancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, REQUESTED_AT,
                null, PortOneCancellationTrigger.CONSOLE));
    }

    @Test
    @DisplayName("같은 취소 ID의 환불이 없는데 취소 금액이 결제 금액과 다르면 진행 중 환불을 조회하기 전에 예외로 막는다")
    void rejectAmountMismatchWithoutExistingRefund() {
        // given — 새 EXTERNAL 환불은 결제 전액을 돌려준 취소에만 만든다
        Payment payment = givenLocked(PaymentStatus.PAID);
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        PortOneCancellation partial = new PortOneCancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED,
            10000L, REQUESTED_AT, CANCELED_AT, PortOneCancellationTrigger.CONSOLE);

        // when
        Throwable thrown = catchThrowable(() -> processor.apply(PAYMENT_ID, partial));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).findRequestedMemberRefunds(any());
        verify(paymentService, never()).findRequestedSystemRefund(any());
        assertNothingChanged(payment, PaymentStatus.PAID);
    }

    @Test
    @DisplayName("금액 불일치로 생긴 SYSTEM 환불은 결제 금액이 아니라 환불 금액으로 비교해 재전송을 ALREADY_RECONCILED로 끝낸다")
    void alreadyReconciledWhenSystemRefundAmountDiffersFromPayment() {
        // given — PG 청구액 25,000원을 돌려준 SYSTEM 환불. 결제 금액은 서버가 기대한 23,800원이다
        Payment payment = givenLocked(PaymentStatus.FAILED);
        givenSameCancellation(refund(payment, RefundStatus.COMPLETED, RefundRequester.SYSTEM, CANCELLATION_ID,
            CHARGED_AMOUNT));
        PortOneCancellation charged = new PortOneCancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED,
            CHARGED_AMOUNT, REQUESTED_AT, CANCELED_AT, PortOneCancellationTrigger.CONSOLE);

        // when
        ExternalCancellationResult result = processor.apply(PAYMENT_ID, charged);

        // then — 첫 전달이 PROCESSED였던 같은 웹훅의 재전송도 같은 결과로 끝난다
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.ALREADY_RECONCILED);
        assertNothingChanged(payment, PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("같은 취소 ID의 환불이 있는데 취소 금액이 그 환불 금액과 다르면 예외로 막는다")
    void rejectAmountMismatchWithExistingRefund() {
        // given — 판정 규칙을 통과했다면 나올 수 없는 호출이다
        Payment payment = givenLocked(PaymentStatus.FAILED);
        givenSameCancellation(refund(payment, RefundStatus.COMPLETED, RefundRequester.SYSTEM, CANCELLATION_ID,
            CHARGED_AMOUNT));
        PortOneCancellation mismatched = new PortOneCancellation(CANCELLATION_ID,
            PortOneCancellationStatus.SUCCEEDED, 24000L, REQUESTED_AT, CANCELED_AT,
            PortOneCancellationTrigger.CONSOLE);

        // when
        Throwable thrown = catchThrowable(() -> processor.apply(PAYMENT_ID, mismatched));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).findRequestedMemberRefunds(any());
        verify(paymentService, never()).findRequestedSystemRefund(any());
        assertNothingChanged(payment, PaymentStatus.FAILED);
    }

    // ---------- 준비 ----------

    private Payment givenLocked(PaymentStatus status) {
        Payment payment = payment(PAYMENT_ID, status);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        return payment;
    }

    private void givenNoRefunds() {
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID)).willReturn(List.of());
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID)).willReturn(Optional.empty());
    }

    private void givenSameCancellation(Refund refund) {
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.of(refund));
    }

    // 실제 조회처럼 회원·마트 거절 환불은 목록으로, SYSTEM 환불은 결제당 한 건으로 돌려준다
    private void givenRequested(Refund refund) {
        given(paymentService.findRefundByPortoneCancellationId(CANCELLATION_ID)).willReturn(Optional.empty());
        RefundRequester requestedBy = refund.getRequestedBy();
        given(paymentService.findRequestedMemberRefunds(PAYMENT_ID))
            .willReturn(requestedBy == RefundRequester.MEMBER ? List.of(refund) : List.of());
        given(paymentService.findRequestedSystemRefund(PAYMENT_ID))
            .willReturn(requestedBy == RefundRequester.SYSTEM ? Optional.of(refund) : Optional.empty());
        given(paymentService.findRequestedAdminRefunds(PAYMENT_ID))
            .willReturn(requestedBy == RefundRequester.ADMIN ? List.of(refund) : List.of());
    }

    // 상태를 바꾸지 않는 결과는 결제·환불·주문·재고 어디에도 흔적을 남기지 않아야 한다
    private void assertNothingChanged(Payment payment, PaymentStatus expectedStatus) {
        assertThat(payment.getStatus()).isEqualTo(expectedStatus);
        verify(paymentService, never()).recordExternalRefund(any(), any(), any(), any(), any());
        verifyNoInteractions(orderFacade);
    }

    private static PortOneCancellation consoleSucceeded() {
        return new PortOneCancellation(CANCELLATION_ID, PortOneCancellationStatus.SUCCEEDED, AMOUNT, REQUESTED_AT,
            CANCELED_AT, PortOneCancellationTrigger.CONSOLE);
    }

    private static Payment payment(Long paymentId, PaymentStatus status) {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_" + paymentId)
            .amount(AMOUNT)
            .status(status)
            .expiresAt(REQUESTED_AT.minusMinutes(30))
            .build();
        // DB를 거치지 않아 id가 비어 있다. 다른 결제의 환불인지 비교하려면 JPA가 채웠을 값이 있어야 한다
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private static Refund refund(Payment payment, RefundStatus status, RefundRequester requestedBy,
                                 String cancellationId) {
        return refund(payment, status, requestedBy, cancellationId, AMOUNT);
    }

    // 상태·취소 ID를 자유롭게 맞추려고 빌더와 필드 주입을 쓴다. 전이 규칙은 RefundTest가 따로 검증한다.
    // 금액 불일치로 생긴 SYSTEM 환불처럼 환불 금액이 결제 금액과 다른 경우를 만들려고 금액도 받는다
    private static Refund refund(Payment payment, RefundStatus status, RefundRequester requestedBy,
                                 String cancellationId, Long amount) {
        Refund refund = Refund.builder()
            .payment(payment)
            .amount(amount)
            .reason("테스트 환불")
            .status(status)
            .requestedBy(requestedBy)
            .requestedAt(REQUESTED_AT)
            .build();
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        ReflectionTestUtils.setField(refund, "portoneCancellationId", cancellationId);
        return refund;
    }
}
