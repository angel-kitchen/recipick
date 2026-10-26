package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.PaymentCancelFacade;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 1~6단계를 한 줄로 잇는 E2E. 웹훅 수신(실제 서명) → MySQL inbox → Redis 큐 → Worker → PortOne 재조회(mock) → 결제·환불 반영.
// Worker·Fallback의 @Scheduled는 test 프로필에서 꺼져 있어 poll()을 직접 불러 실행 시점을 정한다
class WebhookFlowIntegrationTest extends PaymentFlowIntegrationSupport {

    @Autowired
    PaymentCancelFacade paymentCancelFacade;

    @Test
    @DisplayName("Paid 웹훅 수신 → 큐 → Worker → PortOne PAID 재조회로 결제·주문이 확정되고 웹훅은 PROCESSED, 큐는 비워진다")
    void paidWebhookCompletesPayment() throws Exception {
        // given
        Flow flow = pendingPayment();
        String webhookId = "wh_flow_paid_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));

        // when — 수신
        postWebhook(webhookId, "Transaction.Paid", flow.portonePaymentId(), null).andExpect(status().isOk());

        // then — 요청 스레드는 저장과 큐 등록까지만 한다
        List<WebhookEvent> received = eventsOf(webhookId);
        assertThat(received).hasSize(1);
        WebhookEvent event = received.get(0);
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(scoreOf(PaymentDelayTask.webhookProcess(event.getId()))).isNotNull();
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);

        // when — Worker 한 번
        pollOnly(PaymentDelayTask.webhookProcess(event.getId()));

        // then
        WebhookEvent processed = reload(event);
        assertThat(processed.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(processed.getAttemptCount()).isEqualTo(1);
        assertThat(processed.getFinishedAt()).isNotNull();

        Payment payment = paymentOf(flow);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(flow.paidAt());
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        // markPaid 한 번 = ORDER 이력 1 + MART 이력 1
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId())).hasSize(2);
        assertThat(refundsOf(flow)).isEmpty();
        assertThat(scoreOf(PaymentDelayTask.webhookProcess(event.getId()))).isNull();
        verify(portOnePaymentClient, times(1)).getPayment(flow.portonePaymentId());
    }

    @Test
    @DisplayName("PortOne 조회가 일시 실패하면 RETRY_PENDING과 1분 뒤 재예약이 실제 Redis에 남고, 그 시각이 되면 두 번째 시도로 확정된다")
    void retryAfterTemporaryPortOneFailure() throws Exception {
        // given — 첫 조회는 timeout, 두 번째는 정상
        Flow flow = pendingPayment();
        String webhookId = "wh_flow_retry_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT))
            .willReturn(portOnePaid(flow));
        postWebhook(webhookId, "Transaction.Paid", flow.portonePaymentId(), null).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        PaymentDelayTask task = PaymentDelayTask.webhookProcess(event.getId());

        // when — 첫 시도
        Instant before = clock.instant();
        pollOnly(task);
        Instant after = clock.instant();

        // then — DB에 먼저 적고 큐에 1분 뒤로 다시 넣었다
        WebhookEvent retrying = reload(event);
        assertThat(retrying.getStatus()).isEqualTo(WebhookEventStatus.RETRY_PENDING);
        assertThat(retrying.getAttemptCount()).isEqualTo(1);
        assertThat(scoreOf(task)).isBetween(
            (double) before.plus(Duration.ofMinutes(1)).toEpochMilli(),
            (double) after.plus(Duration.ofMinutes(1)).toEpochMilli());
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);

        // when — 1분이 지난 것으로 보고 다시 돌린다
        makeDueNow(task);
        pollOnly(task);

        // then
        WebhookEvent processed = reload(event);
        assertThat(processed.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(processed.getAttemptCount()).isEqualTo(2);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(scoreOf(task)).isNull();
        verify(portOnePaymentClient, times(2)).getPayment(flow.portonePaymentId());
    }

    @Test
    @DisplayName("취소가 PortOne에서 REQUESTED로 끝나면 환불 재확인이 예약되고, 그 시각에 SUCCEEDED가 보이면 결제 CANCELED·환불 COMPLETED·주문 REFUNDED·재고 1회 복구로 마무리된다")
    void requestedCancelCompletesThroughReconcile() throws Exception {
        // given — 결제 완료 주문. PortOne 취소 API는 아직 처리 중이라고 답한다
        Flow flow = paidPaymentWithStock();
        String cancellationId = "cancel_flow_" + flow.orderId();
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willReturn(new PortOneCancelResult(
                PortOneCancelOutcome.REQUESTED, cancellationId, null, "cancellation REQUESTED"));

        // when — 회원 취소
        Instant before = clock.instant();
        PaymentCancelResult result = paymentCancelFacade.cancel(
            flow.memberId(), flow.paymentId(), "flow-cancel-" + flow.orderId(), CANCEL_REASON);

        // then — 202 PENDING. 환불은 REQUESTED로 취소 ID만 기록됐고, 5분 뒤 재확인이 실제 Redis에 있다
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.PENDING);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        Refund refund = refunds.get(0);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(cancellationId);
        PaymentDelayTask task = PaymentDelayTask.refundReconcile(refund.getId());
        assertThat(scoreOf(task)).isGreaterThanOrEqualTo((double) before.plus(Duration.ofMinutes(5)).toEpochMilli());
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);

        // when — 5분이 지난 것으로 보고 Worker를 돌린다. 그사이 PortOne 취소는 완료됐다
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(portOneWithCancellation(flow, cancellationId, PortOneCancellationStatus.SUCCEEDED));
        makeDueNow(task);
        pollOnly(task);

        // then
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        Refund completed = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(scoreOf(task)).isNull();
        // 재확인은 조회만 한다 — 취소 POST는 처음 한 번뿐이다
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }
}
