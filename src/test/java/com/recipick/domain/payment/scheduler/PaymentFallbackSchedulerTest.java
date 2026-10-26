package com.recipick.domain.payment.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.WebhookEventService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

// Sweep이 어떤 기준 시각으로 묻고, 받은 ID를 어떤 작업으로 큐에 넣는지만 본다.
// 어떤 상태가 골라지는지는 WebhookEventRepositoryTest·RefundRepositoryTest가 실제 DB로 본다
@ExtendWith(MockitoExtension.class)
class PaymentFallbackSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW_INSTANT = Instant.parse("2026-10-26T04:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, SEOUL);
    private static final int BATCH = PaymentFallbackScheduler.BATCH_SIZE;

    @Mock
    private WebhookEventService webhookEventService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    private PaymentFallbackScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentFallbackScheduler(
            webhookEventService, paymentService, paymentDelayQueue, Clock.fixed(NOW_INSTANT, SEOUL));
    }

    @Test
    @DisplayName("상태별 기준 시각으로 미완료 웹훅을 묻고 받은 ID를 지금 시각의 WEBHOOK_PROCESS로 넣는다")
    void recoverWebhookEvents() {
        // given — RECEIVED 1분, RETRY_PENDING 10분, PROCESSING 10분(처리 timeout)
        given(webhookEventService.findRecoverableIds(
            NOW.minusMinutes(1), NOW.minusMinutes(10), NOW.minusMinutes(10), 0L, BATCH))
            .willReturn(List.of(3L, 7L));
        given(paymentService.findStaleMemberRefundIds(NOW.minusMinutes(5), 0L, BATCH)).willReturn(List.of());

        // when
        scheduler.sweep();

        // then
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.webhookProcess(3L), NOW_INSTANT);
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.webhookProcess(7L), NOW_INSTANT);
    }

    @Test
    @DisplayName("첫 재확인 시각(5분)이 지난 회원 REQUESTED 환불 ID를 지금 시각의 REFUND_RECONCILE로 넣는다")
    void recoverRefunds() {
        // given
        given(webhookEventService.findRecoverableIds(
            NOW.minusMinutes(1), NOW.minusMinutes(10), NOW.minusMinutes(10), 0L, BATCH))
            .willReturn(List.of());
        given(paymentService.findStaleMemberRefundIds(NOW.minusMinutes(5), 0L, BATCH)).willReturn(List.of(55L));

        // when
        scheduler.sweep();

        // then
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(55L), NOW_INSTANT);
    }

    @Test
    @DisplayName("한 번에 BATCH_SIZE건이 차면 마지막 ID 다음부터 이어 읽고, 덜 차면 멈춘다")
    void continueAfterFullBatch() {
        // given — 첫 묶음 1~100, 두 번째 묶음 101
        List<Long> firstBatch = LongStream.rangeClosed(1, BATCH).boxed().toList();
        given(webhookEventService.findRecoverableIds(
            NOW.minusMinutes(1), NOW.minusMinutes(10), NOW.minusMinutes(10), 0L, BATCH))
            .willReturn(firstBatch);
        given(webhookEventService.findRecoverableIds(
            NOW.minusMinutes(1), NOW.minusMinutes(10), NOW.minusMinutes(10), (long) BATCH, BATCH))
            .willReturn(List.of(101L));
        given(paymentService.findStaleMemberRefundIds(NOW.minusMinutes(5), 0L, BATCH)).willReturn(List.of());

        // when
        scheduler.sweep();

        // then
        verify(paymentDelayQueue, times(BATCH + 1)).enqueueIfEarlier(any(), any());
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.webhookProcess(101L), NOW_INSTANT);
    }

    @Test
    @DisplayName("웹훅을 먼저, 환불을 나중에 복구한다")
    void recoverWebhookEventsBeforeRefunds() {
        // given
        given(webhookEventService.findRecoverableIds(
            NOW.minusMinutes(1), NOW.minusMinutes(10), NOW.minusMinutes(10), 0L, BATCH))
            .willReturn(List.of(3L));
        given(paymentService.findStaleMemberRefundIds(NOW.minusMinutes(5), 0L, BATCH)).willReturn(List.of(55L));

        // when
        scheduler.sweep();

        // then
        InOrder inOrder = inOrder(paymentDelayQueue);
        inOrder.verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.webhookProcess(3L), NOW_INSTANT);
        inOrder.verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.refundReconcile(55L), NOW_INSTANT);
    }

    @Test
    @DisplayName("Redis 장애면 예외를 밖으로 던지지 않고 이번 Sweep을 멈춘다 — 남은 건은 다음 주기에 다시 한다")
    void stopSweepOnRedisFailure() {
        // given
        given(webhookEventService.findRecoverableIds(
            NOW.minusMinutes(1), NOW.minusMinutes(10), NOW.minusMinutes(10), 0L, BATCH))
            .willReturn(List.of(3L, 7L));
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.webhookProcess(3L), NOW_INSTANT);

        // when, then
        assertThatCode(() -> scheduler.sweep()).doesNotThrowAnyException();
        verify(paymentDelayQueue, never()).enqueueIfEarlier(PaymentDelayTask.webhookProcess(7L), NOW_INSTANT);
        verifyNoInteractions(paymentService);
    }
}
