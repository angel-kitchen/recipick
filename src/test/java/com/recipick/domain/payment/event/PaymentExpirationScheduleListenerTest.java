package com.recipick.domain.payment.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

// 예약 시각 변환과 Redis 장애 정책만 본다. 커밋 뒤에만 실행되고 롤백이면 실행되지 않는 것은 통합 테스트가 맡는다
@ExtendWith(MockitoExtension.class)
class PaymentExpirationScheduleListenerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long PAYMENT_ID = 10L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 27, 22, 5);
    private static final Instant EXPIRES_AT_INSTANT = Instant.parse("2026-10-27T13:05:00Z");

    @Mock
    private PaymentDelayQueue paymentDelayQueue;

    private PaymentExpirationScheduleListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentExpirationScheduleListener(paymentDelayQueue,
            Clock.fixed(Instant.parse("2026-10-27T13:00:00Z"), SEOUL));
    }

    @Test
    @DisplayName("결제 만료 시각(한국 시간)을 그대로 실행 시각으로 PAYMENT_EXPIRE를 신규 등록한다")
    void scheduleAtExpiresAt() {
        // when
        listener.schedule(new PaymentCreatedEvent(PAYMENT_ID, EXPIRES_AT));

        // then
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.paymentExpire(PAYMENT_ID), EXPIRES_AT_INSTANT);
    }

    @Test
    @DisplayName("Redis 등록이 실패해도 예외를 밖으로 던지지 않는다 — 주문·결제는 커밋됐고 Fallback이 다시 등록한다")
    void swallowRedisFailure() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
            .given(paymentDelayQueue).enqueueIfEarlier(any(), any());

        // when, then
        assertThatCode(() -> listener.schedule(new PaymentCreatedEvent(PAYMENT_ID, EXPIRES_AT)))
            .doesNotThrowAnyException();
        verify(paymentDelayQueue).enqueueIfEarlier(PaymentDelayTask.paymentExpire(PAYMENT_ID), EXPIRES_AT_INSTANT);
    }
}
