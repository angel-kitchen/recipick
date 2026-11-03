package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.PaymentConfirmFacade;
import com.recipick.domain.payment.facade.PaymentExpirationProcessor;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 원화가 아닌 PG 결과가 실제 MySQL 위에서 결제·주문·재고·환불 어느 것도 바꾸지 않는지 본다.
// 판정 행렬은 PaymentVerifierTest, 결과별 분기는 각 Facade 단위 테스트가 맡고, 여기서는 확정 흐름 전체의 결과만 본다.
// PortOne만 Mock이다 — 결제 확정은 HTTP Controller가 아니라 PaymentConfirmFacade를 직접 부른다
class PaymentCurrencyMismatchIntegrationTest extends PaymentFlowIntegrationSupport {

    @Autowired
    PaymentConfirmFacade paymentConfirmFacade;

    @Autowired
    PaymentExpirationProcessor paymentExpirationProcessor;

    @Test
    @DisplayName("결제 대기 주문에 PG가 다른 통화의 PAID를 돌려주면 PAYMENT-013으로 거절하고 결제·주문·재고를 그대로 둔다")
    void foreignCurrencyDoesNotConfirmPendingPayment() {
        // given
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).plusMinutes(5));
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaidIn(flow, "USD"));

        // when
        Throwable thrown = catchThrowable(() ->
            paymentConfirmFacade.confirm(flow.memberId(), flow.orderId(), flow.portonePaymentId()));

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CURRENCY_MISMATCH));
        Payment payment = paymentOf(flow);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(payment.getPaidAt()).isNull();
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        assertThat(refundsOf(flow)).isEmpty();
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("만료로 닫힌 결제에 다른 통화의 늦은 PAID가 확인되면 SYSTEM 환불을 만들지 않고 결제는 FAILED 그대로다")
    void foreignCurrencyLateApprovalMakesNoSystemRefund() {
        // given — 만료 Processor가 먼저 결제를 FAILED로 닫고 주문·재고를 되돌린 상태
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).minusMinutes(1));
        assertThat(paymentExpirationProcessor.expire(flow.paymentId())).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaidIn(flow, "USD"));

        // when
        Throwable thrown = catchThrowable(() ->
            paymentConfirmFacade.confirm(flow.memberId(), flow.orderId(), flow.portonePaymentId()));

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CURRENCY_MISMATCH));
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(refundsOf(flow)).isEmpty();
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    // 주문 ID·금액은 맞고 통화만 다른 PAID 응답. 완료 시각은 만료 시각 안이라 통화 검증이 없었다면 승인됐을 결과다
    private PortOnePayment portOnePaidIn(Flow flow, String currency) {
        LocalDateTime paidAt = paymentOf(flow).getExpiresAt().minusSeconds(30);
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.PAID,
            AMOUNT, currency, paidAt, flow.orderId(), null);
    }
}
