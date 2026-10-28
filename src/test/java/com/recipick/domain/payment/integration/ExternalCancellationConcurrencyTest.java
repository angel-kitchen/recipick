package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 외부 전액 취소의 경쟁. 같은 webhook_id 재전송은 webhook_id UNIQUE가 막으므로(PaymentWebhookConcurrencyTest),
// 여기서는 서로 다른 웹훅 두 건이 같은 결제의 같은 취소를 동시에 처리하는 더 강한 경우를 본다.
// 최종 방어선은 PAYMENT 잠금이다 — 뒤에 잠금을 얻은 쪽이 앞선 쪽이 커밋한 EXTERNAL 환불을 보고 이미 반영된 것으로 끝낸다
class ExternalCancellationConcurrencyTest extends PaymentFlowIntegrationSupport {

    @Autowired
    PaymentReconciliationFacade paymentReconciliationFacade;

    @Test
    @DisplayName("서로 다른 웹훅 두 건이 같은 콘솔 전액 취소를 동시에 처리해도 둘 다 PROCESSED이고 EXTERNAL 환불·재고 복구·주문 이력은 한 번이다")
    void twoWebhooksForSameConsoleCancellationAtOnce() throws Exception {
        // given — 두 웹훅은 이미 수신됐고 Worker가 큐에서 꺼낸 상태다. PortOne은 두 쪽 모두에 같은 완료 취소로 답한다
        Flow flow = paidPaymentWithStock();
        String cancellationId = "cancel_console_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(consoleSucceeded(flow, cancellationId));
        WebhookEvent first = receive("wh_external_a_" + flow.orderId(), flow, cancellationId);
        WebhookEvent second = receive("wh_external_b_" + flow.orderId(), flow, cancellationId);

        Callable<Object> processFirst = () -> paymentReconciliationFacade.processWebhook(first.getId());
        Callable<Object> processSecond = () -> paymentReconciliationFacade.processWebhook(second.getId());

        // when
        List<Object> results = runConcurrently(List.of(processFirst, processSecond));

        // then — 먼저 잠근 쪽이 반영하고, 다른 쪽은 이미 반영된 것으로 끝낸다
        assertThat(results).containsOnly(WebhookProcessOutcome.PROCESSED);
        assertThat(reload(first).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(reload(second).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);

        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getRequestedBy()).isEqualTo(RefundRequester.EXTERNAL);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        // 재고 +2가 한 번만(+4 아님), 외부 취소 이력도 한 번
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId()))
            .filteredOn(history -> OrderStatus.REFUNDED.name().equals(history.getToStatus()))
            .filteredOn(history -> CancelReasonCode.EXTERNAL_CANCELED.name().equals(history.getReason()))
            .hasSize(1);

        verify(portOnePaymentClient, times(2)).getPayment(flow.portonePaymentId());
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    // 실제 서명으로 수신해 행을 만들고, Worker가 꺼내 가지 않게 큐 작업은 지운다. 처리는 테스트가 직접 동시에 부른다
    private WebhookEvent receive(String webhookId, Flow flow, String cancellationId) throws Exception {
        postWebhook(webhookId, "Transaction.Cancelled", flow.portonePaymentId(), cancellationId)
            .andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        removeTask(PaymentDelayTask.webhookProcess(event.getId()));
        return event;
    }

    // 콘솔 화면에서 끝난 전액 취소 한 건이 든 조회 결과. 이 테스트만 쓰는 정책 픽스처라 공통 Support에 두지 않는다
    private PortOnePayment consoleSucceeded(Flow flow, String cancellationId) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        PortOneCancellation cancellation = new PortOneCancellation(cancellationId, PortOneCancellationStatus.SUCCEEDED,
            AMOUNT, now.minusMinutes(6), now.minusMinutes(1), PortOneCancellationTrigger.CONSOLE);
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.CANCELED,
            AMOUNT, "KRW", flow.paidAt(), flow.orderId(), null, List.of(cancellation));
    }
}
