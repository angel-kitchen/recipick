package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.PaymentConfirmFacade;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 결제 승인 쪽 경쟁. 같은 웹훅의 동시 재전송은 webhook_id UNIQUE가, 확정 API와 승인 웹훅의 경쟁은 PAYMENT 잠금이 최종 방어선이다
class PaymentWebhookConcurrencyTest extends PaymentFlowIntegrationSupport {

    private static final int THREADS = 5;

    @Autowired
    PaymentConfirmFacade paymentConfirmFacade;

    @Autowired
    PaymentReconciliationFacade paymentReconciliationFacade;

    @Test
    @DisplayName("같은 webhook_id가 5건 동시에 들어와도 모두 200이고 행은 하나, 큐 작업도 하나다")
    void sameWebhookIdPostedConcurrently() throws Exception {
        // given
        Flow flow = pendingPayment();
        String webhookId = "wh_flow_dup_" + flow.orderId();
        List<Callable<Object>> posts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            posts.add(() -> postWebhook(webhookId, "Transaction.Paid", flow.portonePaymentId(), null)
                .andReturn().getResponse().getStatus());
        }

        // when
        List<Object> statuses = runConcurrently(posts);

        // then — 첫 저장만 남고 나머지는 UNIQUE 충돌을 "이미 받은 웹훅"으로 보고 200을 준다
        assertThat(statuses).containsOnly(200);
        List<WebhookEvent> rows = eventsOf(webhookId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(scoreOf(PaymentDelayTask.webhookProcess(rows.get(0).getId()))).isNotNull();
    }

    @Test
    @DisplayName("결제 확정 API 흐름과 Paid 웹훅 처리가 동시에 돌아도 승인은 한 번이다 — 결제 PAID, 주문 이력 2행, SYSTEM 환불 없음")
    void confirmApiAndPaidWebhookAtOnce() throws Exception {
        // given — 웹훅은 이미 수신됐고 Worker가 큐에서 꺼낸 상태다. PortOne은 두 쪽 모두에 PAID로 답한다
        Flow flow = pendingPayment();
        String webhookId = "wh_flow_race_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        postWebhook(webhookId, "Transaction.Paid", flow.portonePaymentId(), null).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        removeTask(PaymentDelayTask.webhookProcess(event.getId()));

        Callable<Object> confirm = () ->
            paymentConfirmFacade.confirm(flow.memberId(), flow.orderId(), flow.portonePaymentId());
        Callable<Object> webhook = () -> paymentReconciliationFacade.processWebhook(event.getId());

        // when
        List<Object> results = runConcurrently(List.of(confirm, webhook));

        // then — 확정 API는 APPROVED든 ALREADY_PAID든 예외 없이 응답하고, 웹훅은 PROCESSED로 끝난다
        assertThat(results.get(1)).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reload(event).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        // markPaid가 한 번만 실행됐다 = ORDER 이력 1 + MART 이력 1
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId())).hasSize(2);
        assertThat(refundsOf(flow)).isEmpty();
        verify(portOnePaymentClient, times(2)).getPayment(flow.portonePaymentId());
    }
}
