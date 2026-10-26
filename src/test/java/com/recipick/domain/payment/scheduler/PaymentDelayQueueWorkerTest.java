package com.recipick.domain.payment.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Worker는 꺼내서 넘기기만 한다. 큐의 원자성은 RedisDelayQueueTest, 정합화 판정은 PaymentReconciliationFacadeTest가 맡는다
@ExtendWith(MockitoExtension.class)
class PaymentDelayQueueWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-26T04:00:00Z");

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    @Mock
    private PaymentReconciliationFacade paymentReconciliationFacade;

    private PaymentDelayQueueWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PaymentDelayQueueWorker(
            paymentDelayQueue, paymentReconciliationFacade, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("실행 시각이 된 WEBHOOK_PROCESS 작업을 꺼낸 순서대로 정합화 Facade에 넘긴다")
    void dispatchWebhookTasksInOrder() {
        // given
        given(paymentDelayQueue.popDue(NOW, PaymentDelayQueueWorker.BATCH_SIZE))
            .willReturn(List.of(PaymentDelayTask.webhookProcess(1L), PaymentDelayTask.webhookProcess(2L)));

        // when
        worker.poll();

        // then
        InOrder inOrder = inOrder(paymentReconciliationFacade);
        inOrder.verify(paymentReconciliationFacade).processWebhook(1L);
        inOrder.verify(paymentReconciliationFacade).processWebhook(2L);
    }

    @Test
    @DisplayName("한 작업이 예외를 던져도 같은 배치의 다음 작업은 계속 처리한다")
    void continueAfterFailure() {
        // given
        given(paymentDelayQueue.popDue(NOW, PaymentDelayQueueWorker.BATCH_SIZE))
            .willReturn(List.of(PaymentDelayTask.webhookProcess(1L), PaymentDelayTask.webhookProcess(2L)));
        given(paymentReconciliationFacade.processWebhook(1L)).willThrow(new IllegalStateException("boom"));

        // when
        worker.poll();

        // then
        verify(paymentReconciliationFacade).processWebhook(2L);
    }

    @Test
    @DisplayName("REFUND_RECONCILE은 아직 처리하지 않는다(6단계) — Facade를 부르지 않고 버린다")
    void dropRefundReconcileForNow() {
        // given
        given(paymentDelayQueue.popDue(NOW, PaymentDelayQueueWorker.BATCH_SIZE))
            .willReturn(List.of(PaymentDelayTask.refundReconcile(5L)));

        // when
        worker.poll();

        // then
        verifyNoInteractions(paymentReconciliationFacade);
    }

    @Test
    @DisplayName("꺼낼 작업이 없으면 아무것도 하지 않는다")
    void nothingDue() {
        // given
        given(paymentDelayQueue.popDue(NOW, PaymentDelayQueueWorker.BATCH_SIZE)).willReturn(List.of());

        // when
        worker.poll();

        // then
        verifyNoInteractions(paymentReconciliationFacade);
    }
}
