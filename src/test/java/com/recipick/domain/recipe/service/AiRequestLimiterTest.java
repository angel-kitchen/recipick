package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.recipe.exception.AiErrorCode;
import com.recipick.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class AiRequestLimiterTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9501L;
    private static final String KEY = "ai:request:" + MEMBER_ID;

    @Autowired
    AiRequestLimiter aiRequestLimiter;
    @Autowired
    StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(KEY);
    }

    @Test
    @DisplayName("1분 안에 5번까지는 허용하고 6번째 요청은 AI-001로 거절한다")
    void acquire_rejectsSixthRequestInWindow() {
        // given
        for (int i = 0; i < 5; i++) {
            aiRequestLimiter.acquire(MEMBER_ID);
        }

        // when
        Throwable thrown = catchThrowable(() -> aiRequestLimiter.acquire(MEMBER_ID));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AiErrorCode.AI_REQUEST_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("첫 요청에서 1분 만료를 걸어 구간이 지나면 다시 요청할 수 있다")
    void acquire_setsExpirationOnFirstRequest() {
        // when
        aiRequestLimiter.acquire(MEMBER_ID);

        // then
        Long ttlMillis = redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS);
        assertThat(ttlMillis).isBetween(1L, 60_000L);
    }

    @Test
    @DisplayName("같은 회원이 10건을 동시에 요청해도 5건만 통과하고 나머지는 AI-001로 거절한다")
    void acquire_concurrent() throws Exception {
        // given
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger passed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        // when
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    aiRequestLimiter.acquire(MEMBER_ID);
                    passed.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == AiErrorCode.AI_REQUEST_LIMIT_EXCEEDED) {
                        rejected.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // then
        assertThat(passed.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(threads - 5);
    }
}
