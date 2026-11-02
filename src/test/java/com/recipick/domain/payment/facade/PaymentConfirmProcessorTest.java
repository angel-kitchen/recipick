package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PaymentVerifier;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmProcessorTest {

    private static final Long ORDER_ID = 1L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 21, 10, 5);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 21, 10, 3);
    private static final List<StockQuantity> RESTORE_ITEMS = List.of(new StockQuantity(100L, 2));

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentVerifier paymentVerifier;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private PointCommandService pointCommandService;

    @InjectMocks
    private PaymentConfirmProcessor processor;

    @Test
    @DisplayName("APPROVED면 결제를 PAID로 바꾸고 주문 markPaid를 PG 결제 시각으로 부른 뒤 포인트 사용을 확정하고 마지막에 장바구니를 정리한다")
    void approved() {
        // given
        Payment payment = inProgressPayment();
        PortOnePayment portOne = paidPortOne(23800L);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(PaymentApprovalOutcome.APPROVED);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.APPROVED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPortoneTransactionId()).isEqualTo("tx_1");
        assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
        InOrder inOrder = inOrder(orderService, pointCommandService, orderFacade);
        inOrder.verify(orderService).markPaid(ORDER_ID, PAID_AT);
        inOrder.verify(pointCommandService).confirmUsage(ORDER_ID);
        inOrder.verify(orderFacade).removePaidItemsFromCart(ORDER_ID);
        verify(pointCommandService, never()).restoreUsageAndCancelEarning(any());
        verify(paymentService, never()).requestSystemRefund(any(), any(), any());
        verify(orderFacade, never()).closeByPaymentFailure(any(), any(), any());
    }

    @Test
    @DisplayName("PG_FAILED면 결제를 FAILED(PG_FAILED)로 바꾸고 환불 없이 주문 종료 → 포인트 복원 → 재고 복구 순으로 부른다")
    void pgFailed() {
        // given
        Payment payment = inProgressPayment();
        PortOnePayment portOne = new PortOnePayment("pay_1", "tx_1", PortOnePaymentStatus.FAILED,
            null, "KRW", null, ORDER_ID, "card declined | E001");
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(PaymentApprovalOutcome.PG_FAILED);
        given(orderFacade.closeByPaymentFailure(ORDER_ID, CancelReasonCode.PAYMENT_FAILED, "card declined | E001"))
            .willReturn(RESTORE_ITEMS);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.PG_FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.PG_FAILED);
        InOrder inOrder = inOrder(orderFacade, pointCommandService);
        inOrder.verify(orderFacade).closeByPaymentFailure(ORDER_ID, CancelReasonCode.PAYMENT_FAILED, "card declined | E001");
        inOrder.verify(pointCommandService).restoreUsageAndCancelEarning(ORDER_ID);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
        verify(paymentService, never()).requestSystemRefund(any(), any(), any());
        verify(orderService, never()).markPaid(any(), any());
        verify(orderFacade, never()).removePaidItemsFromCart(any());
    }

    @Test
    @DisplayName("AMOUNT_MISMATCH면 FAILED(AMOUNT_MISMATCH) → PG 청구액으로 SYSTEM 환불 → 주문 종료 → 포인트 복원 → 재고 복구")
    void amountMismatch() {
        // given
        Payment payment = inProgressPayment();
        PortOnePayment portOne = paidPortOne(20000L);
        String detail = "결제 금액 불일치 — 주문 23800원, PG 결제 20000원";
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(PaymentApprovalOutcome.AMOUNT_MISMATCH);
        given(orderFacade.closeByPaymentFailure(ORDER_ID, CancelReasonCode.AMOUNT_MISMATCH, detail))
            .willReturn(RESTORE_ITEMS);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.AMOUNT_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.AMOUNT_MISMATCH);
        InOrder inOrder = inOrder(paymentService, orderFacade, pointCommandService);
        inOrder.verify(paymentService).requestSystemRefund(payment, 20000L, detail);
        inOrder.verify(orderFacade).closeByPaymentFailure(ORDER_ID, CancelReasonCode.AMOUNT_MISMATCH, detail);
        inOrder.verify(pointCommandService).restoreUsageAndCancelEarning(ORDER_ID);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
        verify(orderService, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("EXPIRED면 FAILED(EXPIRED) → SYSTEM 환불 → 주문 종료(PAYMENT_TIMEOUT) → 포인트 복원 → 재고 복구")
    void expired() {
        // given
        Payment payment = inProgressPayment();
        PortOnePayment portOne = paidPortOne(23800L);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(PaymentApprovalOutcome.EXPIRED);
        given(orderFacade.closeByPaymentFailure(ORDER_ID, CancelReasonCode.PAYMENT_TIMEOUT, "결제 대기 5분 만료"))
            .willReturn(RESTORE_ITEMS);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.EXPIRED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.EXPIRED);
        InOrder inOrder = inOrder(paymentService, orderFacade, pointCommandService);
        inOrder.verify(paymentService).requestSystemRefund(payment, 23800L, "결제 대기 5분 만료");
        inOrder.verify(orderFacade).closeByPaymentFailure(ORDER_ID, CancelReasonCode.PAYMENT_TIMEOUT, "결제 대기 5분 만료");
        inOrder.verify(pointCommandService).restoreUsageAndCancelEarning(ORDER_ID);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
        verify(orderService, never()).markPaid(any(), any());
        verify(orderFacade, never()).removePaidItemsFromCart(any());
    }

    @Test
    @DisplayName("만료 뒤 늦은 승인(FAILED + PG PAID)은 SYSTEM 환불만 만들고 결제·주문·포인트·재고는 건드리지 않는다")
    void lateApprovalAfterClosed() {
        // given
        Payment payment = payment(PaymentStatus.FAILED);
        PortOnePayment portOne = paidPortOne(23800L);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(PaymentApprovalOutcome.ALREADY_CLOSED);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentService).requestSystemRefund(payment, 23800L, "종료된 결제의 늦은 승인");
        verifyNoInteractions(orderService, orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("늦은 승인인데 PG 금액이 없으면 환불 기록을 보류하고 아무것도 바꾸지 않는다")
    void lateApprovalWithoutAmount() {
        // given
        Payment payment = payment(PaymentStatus.FAILED);
        PortOnePayment portOne = paidPortOne(null);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(PaymentApprovalOutcome.ALREADY_CLOSED);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
        verify(paymentService, never()).requestSystemRefund(any(), any(), any());
        verifyNoInteractions(orderService, orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("FAILED 결제라도 PG가 PAID가 아니면 늦은 승인 환불을 만들지 않는다")
    void closedButPgNotPaid() {
        // given
        Payment payment = payment(PaymentStatus.FAILED);
        PortOnePayment portOne = new PortOnePayment("pay_1", "tx_1", PortOnePaymentStatus.FAILED,
            23800L, "KRW", null, ORDER_ID, "card declined");
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(PaymentApprovalOutcome.ALREADY_CLOSED);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
        verify(paymentService, never()).requestSystemRefund(any(), any(), any());
        verifyNoInteractions(orderService, orderFacade, pointCommandService);
    }

    @Test
    @DisplayName("CANCELED 결제에 PG PAID가 와도 취소 흐름 몫이라 환불을 만들지 않는다")
    void canceledIsNotLateApproval() {
        // given
        Payment payment = payment(PaymentStatus.CANCELED);
        PortOnePayment portOne = paidPortOne(23800L);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(PaymentApprovalOutcome.ALREADY_CLOSED);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
        verify(paymentService, never()).requestSystemRefund(any(), any(), any());
        verifyNoInteractions(orderService, orderFacade, pointCommandService);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentApprovalOutcome.class,
        names = {"ALREADY_PAID", "INVALID_STATUS", "NOT_PAID", "ORDER_MISMATCH"})
    @DisplayName("변경 없는 결과는 결제 상태도 협력자 호출도 그대로다")
    void noChangeOutcomes(PaymentApprovalOutcome given) {
        // given
        Payment payment = inProgressPayment();
        PortOnePayment portOne = paidPortOne(23800L);
        given(paymentService.getByOrderIdForUpdate(ORDER_ID)).willReturn(payment);
        given(paymentVerifier.verify(payment, portOne)).willReturn(given);

        // when
        PaymentApprovalOutcome outcome = processor.process(ORDER_ID, portOne);

        // then
        assertThat(outcome).isEqualTo(given);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        verify(paymentService, never()).requestSystemRefund(any(), any(), any());
        verifyNoInteractions(orderService, orderFacade, pointCommandService);
    }

    private Payment inProgressPayment() {
        return payment(PaymentStatus.IN_PROGRESS);
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(status)
            .expiresAt(EXPIRES_AT)
            .build();
    }

    private PortOnePayment paidPortOne(Long totalAmount) {
        return new PortOnePayment("pay_1", "tx_1", PortOnePaymentStatus.PAID,
            totalAmount, "KRW", PAID_AT, ORDER_ID, null);
    }
}
