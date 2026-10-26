package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.WebhookEventService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// "Redis 작업은 사라져도 되고 MySQL에서 다시 살아난다"를 실제로 증명한다.
// 큐 작업이 유실된 상태를 만들고 → Fallback Sweep → Worker → 최종 PROCESSED까지 이어지는지 본다
class PaymentRecoveryIntegrationTest extends PaymentFlowIntegrationSupport {

    @Autowired
    WebhookEventService webhookEventService;

    @Test
    @DisplayName("Worker가 큐에서 꺼낸 뒤 claim이 락 대기 초과로 실패하면 작업은 사라지고 이벤트는 RECEIVED로 남지만, Fallback이 다시 넣어 최종 처리된다")
    void claimLockTimeoutRecoveredByFallback() throws Exception {
        // given — 수신된 Paid 웹훅. 다른 트랜잭션이 그 행을 잠그고 놓지 않는다
        Flow flow = pendingPayment();
        String webhookId = "wh_flow_lock_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        postWebhook(webhookId, "Transaction.Paid", flow.portonePaymentId(), null).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        PaymentDelayTask task = PaymentDelayTask.webhookProcess(event.getId());

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                webhookEventRepository.findByIdForUpdate(event.getId());
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // when ① — Worker가 꺼냈지만 claim이 5초 기다리다 실패한다(Worker가 예외를 삼킨다)
            pollOnly(task);

            // then ① — 큐 작업은 사라졌고 DB는 미완료 그대로다. PortOne도 묻지 않았다
            assertThat(scoreOf(task)).isNull();
            WebhookEvent stuck = reload(event);
            assertThat(stuck.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
            assertThat(stuck.getAttemptCount()).isZero();
            assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
            verify(portOnePaymentClient, never()).getPayment(anyString());

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        // when ② — Fallback 기준(RECEIVED 1분)을 넘긴 뒤 Sweep
        moveWebhookUpdatedAt(event.getId(), Duration.ofMinutes(2));
        fallbackScheduler.sweep();

        // then ② — MySQL 상태를 보고 큐에 다시 넣었다
        assertThat(scoreOf(task)).isNotNull();

        // when ③
        pollOnly(task);

        // then ③
        WebhookEvent processed = reload(event);
        assertThat(processed.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(processed.getAttemptCount()).isEqualTo(1);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(scoreOf(task)).isNull();
    }

    @Test
    @DisplayName("Worker가 claim 뒤 죽어 PROCESSING으로 남고 큐 작업도 없으면, timeout이 지난 뒤 Fallback이 다시 넣고 새 Worker가 처리권 2번으로 끝낸다")
    void staleProcessingRecoveredByFallback() throws Exception {
        // given — 수신 → Worker A가 꺼내 claim(PROCESSING, 처리권 1)까지 하고 죽었다
        Flow flow = pendingPayment();
        String webhookId = "wh_flow_stale_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        postWebhook(webhookId, "Transaction.Paid", flow.portonePaymentId(), null).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        PaymentDelayTask task = PaymentDelayTask.webhookProcess(event.getId());
        removeTask(task);
        assertThat(webhookEventService.claim(
            event.getId(), LocalDateTime.now(clock), PaymentReconciliationFacade.PROCESSING_TIMEOUT)).isPresent();
        assertThat(reload(event).getStatus()).isEqualTo(WebhookEventStatus.PROCESSING);

        // when ① — PROCESSING_TIMEOUT(10분)을 넘긴 뒤 Sweep
        moveWebhookUpdatedAt(event.getId(), PaymentReconciliationFacade.PROCESSING_TIMEOUT.plusMinutes(1));
        fallbackScheduler.sweep();

        // then ①
        assertThat(scoreOf(task)).isNotNull();

        // when ② — 새 Worker
        pollOnly(task);

        // then ② — stale로 판정해 다시 잡았다(처리권 2). 멈춘 Worker A의 결과 기록은 이제 처리권 불일치로 막힌다
        WebhookEvent processed = reload(event);
        assertThat(processed.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(processed.getAttemptCount()).isEqualTo(2);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(scoreOf(task)).isNull();
        assertThat(webhookEventService.markProcessed(event.getId(), 1, LocalDateTime.now(clock))).isFalse();
    }
}
