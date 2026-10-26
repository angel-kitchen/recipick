package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.recipick.infra.redis.RedisDelayQueue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentDelayQueueTest {

    private static final Instant NOW = Instant.parse("2026-10-26T01:00:00Z");

    @Mock
    private RedisDelayQueue redisDelayQueue;

    @InjectMocks
    private PaymentDelayQueue paymentDelayQueue;

    @Test
    @DisplayName("등록과 재예약은 결제 큐 키와 member 문자열로 infra를 부른다")
    void delegatesWithQueueKey() {
        // when
        paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.webhookProcess(100L), NOW);
        paymentDelayQueue.reschedule(PaymentDelayTask.refundReconcile(55L), NOW.plusSeconds(600));

        // then
        verify(redisDelayQueue).enqueueIfEarlier(PaymentDelayQueue.QUEUE_KEY, "WEBHOOK_PROCESS:100", NOW);
        verify(redisDelayQueue).reschedule(PaymentDelayQueue.QUEUE_KEY, "REFUND_RECONCILE:55", NOW.plusSeconds(600));
    }

    @Test
    @DisplayName("꺼낸 member를 작업으로 바꾸고 형식이 깨진 것은 버린다")
    void popDueParsesAndDropsMalformed() {
        // given
        given(redisDelayQueue.popDue(PaymentDelayQueue.QUEUE_KEY, NOW, 10))
            .willReturn(List.of("WEBHOOK_PROCESS:100", "garbage", "REFUND_RECONCILE:55"));

        // when
        List<PaymentDelayTask> tasks = paymentDelayQueue.popDue(NOW, 10);

        // then
        assertThat(tasks).containsExactly(
            PaymentDelayTask.webhookProcess(100L), PaymentDelayTask.refundReconcile(55L));
    }

    @Test
    @DisplayName("꺼낼 작업이 없으면 빈 목록이다")
    void popDueEmpty() {
        // given
        given(redisDelayQueue.popDue(PaymentDelayQueue.QUEUE_KEY, NOW, 10)).willReturn(List.of());

        // when
        List<PaymentDelayTask> tasks = paymentDelayQueue.popDue(NOW, 10);

        // then
        assertThat(tasks).isEmpty();
    }
}
