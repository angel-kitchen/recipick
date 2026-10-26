package com.recipick.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

// 실제 Redis(Testcontainers)로 ZADD LT·원자 pop을 확인한다. 테스트마다 다른 키를 써서 서로 섞이지 않는다
class RedisDelayQueueTest extends IntegrationTestSupport {

    private static final Instant BASE = Instant.parse("2026-10-26T01:00:00Z");

    @Autowired
    private RedisDelayQueue delayQueue;

    @Autowired
    private StringRedisTemplate redis;

    private String key;

    @BeforeEach
    void setUp() {
        key = "test:delay-queue:" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        redis.delete(key);
    }

    @Test
    @DisplayName("없는 작업은 실행 시각(epoch millis)을 score로 추가한다")
    void enqueueNew() {
        // when
        delayQueue.enqueueIfEarlier(key, "WEBHOOK_PROCESS:1", BASE);

        // then
        assertThat(scoreOf("WEBHOOK_PROCESS:1")).isEqualTo(BASE.toEpochMilli());
    }

    @Test
    @DisplayName("이미 있는 작업은 더 이른 시각만 받아들인다 — 늦은 시각으로는 바뀌지 않는다")
    void enqueueKeepsEarlier() {
        // given
        delayQueue.enqueueIfEarlier(key, "WEBHOOK_PROCESS:1", BASE);

        // when — 더 늦은 시각
        delayQueue.enqueueIfEarlier(key, "WEBHOOK_PROCESS:1", BASE.plusSeconds(60));

        // then
        assertThat(scoreOf("WEBHOOK_PROCESS:1")).isEqualTo(BASE.toEpochMilli());

        // when — 더 이른 시각
        delayQueue.enqueueIfEarlier(key, "WEBHOOK_PROCESS:1", BASE.minusSeconds(60));

        // then
        assertThat(scoreOf("WEBHOOK_PROCESS:1")).isEqualTo(BASE.minusSeconds(60).toEpochMilli());
    }

    @Test
    @DisplayName("reschedule은 시각을 뒤로 미루는 것도 그대로 반영한다 — Worker의 backoff용")
    void rescheduleReplaces() {
        // given
        delayQueue.enqueueIfEarlier(key, "REFUND_RECONCILE:5", BASE);

        // when
        delayQueue.reschedule(key, "REFUND_RECONCILE:5", BASE.plusSeconds(600));

        // then
        assertThat(scoreOf("REFUND_RECONCILE:5")).isEqualTo(BASE.plusSeconds(600).toEpochMilli());
        assertThat(redis.opsForZSet().size(key)).isEqualTo(1);
    }

    @Test
    @DisplayName("popDue는 실행 시각이 지난 작업만 오래된 순으로 꺼내고 큐에서 지운다 — 같은 시각은 포함")
    void popDueReturnsDueAndRemoves() {
        // given
        delayQueue.enqueueIfEarlier(key, "late", BASE.plusSeconds(10));
        delayQueue.enqueueIfEarlier(key, "second", BASE);
        delayQueue.enqueueIfEarlier(key, "first", BASE.minusSeconds(5));

        // when
        List<String> due = delayQueue.popDue(key, BASE, 10);

        // then
        assertThat(due).containsExactly("first", "second");
        assertThat(redis.opsForZSet().range(key, 0, -1)).containsExactly("late");

        // when — 두 번째 pop은 빈 목록. 꺼낸 작업이 다시 나오지 않는다
        List<String> again = delayQueue.popDue(key, BASE, 10);

        // then
        assertThat(again).isEmpty();
    }

    @Test
    @DisplayName("popDue는 batchSize만큼만 꺼내고 나머지는 다음 호출로 넘긴다")
    void popDueRespectsBatchSize() {
        // given
        delayQueue.enqueueIfEarlier(key, "a", BASE.minusSeconds(3));
        delayQueue.enqueueIfEarlier(key, "b", BASE.minusSeconds(2));
        delayQueue.enqueueIfEarlier(key, "c", BASE.minusSeconds(1));

        // when
        List<String> firstBatch = delayQueue.popDue(key, BASE, 2);
        List<String> secondBatch = delayQueue.popDue(key, BASE, 2);

        // then
        assertThat(firstBatch).containsExactly("a", "b");
        assertThat(secondBatch).containsExactly("c");
    }

    @Test
    @DisplayName("batchSize가 0 이하면 호출자 버그라 바로 거절한다")
    void rejectNonPositiveBatchSize() {
        // when, then
        assertThatThrownBy(() -> delayQueue.popDue(key, BASE, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private double scoreOf(String member) {
        Double score = redis.opsForZSet().score(key, member);
        assertThat(score).isNotNull();
        return score;
    }
}
