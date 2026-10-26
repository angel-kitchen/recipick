package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.PaymentCancelFacade;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 결제 후 취소 API(#157)의 TX2와 Transaction.Cancelled 웹훅의 경쟁. 가장 위험한 순서를 래치로 고정한다:
// 취소 API가 TX1을 커밋하고 PortOne 응답을 기다리는 사이 웹훅이 먼저 환불을 끝내고, 그 뒤 TX2가 같은 결과로 들어온다
class RefundWebhookConcurrencyTest extends PaymentFlowIntegrationSupport {

    @Autowired
    PaymentCancelFacade paymentCancelFacade;

    @Test
    @DisplayName("취소 API가 PortOne 응답을 기다리는 사이 Cancelled 웹훅이 먼저 환불을 끝내도, 늦게 온 TX2는 멱등이라 환불·재고 복구는 한 번이다")
    void cancelApiTx2AndCancelledWebhookAtOnce() throws Exception {
        // given — PortOne 취소 POST는 웹훅 처리가 끝날 때까지 응답을 붙잡아 둔다
        Flow flow = paidPaymentWithStock();
        String cancellationId = "cancel_race_" + flow.orderId();
        String webhookId = "wh_flow_cancel_" + flow.orderId();
        LocalDateTime canceledAt = LocalDateTime.now(clock).withNano(0);
        CountDownLatch inPortOne = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willAnswer(invocation -> {
                inPortOne.countDown();
                if (!resume.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("웹훅 처리를 기다리다 시간 초과");
                }
                return new PortOneCancelResult(
                    PortOneCancelOutcome.SUCCEEDED, cancellationId, canceledAt, "cancellation SUCCEEDED");
            });
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(portOneWithCancellation(flow, cancellationId, PortOneCancellationStatus.SUCCEEDED));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // when ① — 취소 API 시작. TX1이 커밋되고 PortOne 호출에서 멈춘다
            Future<PaymentCancelResult> cancel = executor.submit(() -> paymentCancelFacade.cancel(
                flow.memberId(), flow.paymentId(), "race-cancel-" + flow.orderId(), CANCEL_REASON));
            assertThat(inPortOne.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
            Refund requested = refundsOf(flow).get(0);
            assertThat(requested.getStatus()).isEqualTo(RefundStatus.REQUESTED);
            assertThat(requested.getPortoneCancellationId()).isNull();

            // when ② — 그사이 Cancelled 웹훅이 도착해 Worker가 먼저 처리한다
            postWebhook(webhookId, "Transaction.Cancelled", flow.portonePaymentId(), cancellationId)
                .andExpect(status().isOk());
            WebhookEvent event = eventsOf(webhookId).get(0);
            pollOnly(PaymentDelayTask.webhookProcess(event.getId()));

            // then ② — 웹훅이 취소 ID 없는 회원 환불을 찾아 완료하고 재고를 돌려놨다
            assertThat(reload(event).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
            assertThat(refundRepository.findById(requested.getId()).orElseThrow().getStatus())
                .isEqualTo(RefundStatus.COMPLETED);
            assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);

            // when ③ — PortOne 응답이 돌아와 TX2가 같은 취소 결과로 들어온다
            resume.countDown();
            PaymentCancelResult result = cancel.get(15, TimeUnit.SECONDS);

            // then ③ — 이미 끝난 환불이라 바꾸지 않고 그 결과를 돌려준다
            assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }

        // then — 최종 상태. 재고 +2가 한 번만(+4 아님), REFUNDED 이력도 한 번
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refunds.get(0).getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId()))
            .filteredOn(history -> OrderStatus.REFUNDED.name().equals(history.getToStatus()))
            .hasSize(1);
        // 확정 결과라 환불 재확인을 예약하지 않았다
        assertThat(scoreOf(PaymentDelayTask.refundReconcile(refunds.get(0).getId()))).isNull();
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }
}
