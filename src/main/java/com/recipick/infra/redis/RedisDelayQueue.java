package com.recipick.infra.redis;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Fallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

// Redis Sorted Set 하나를 지연 큐로 쓴다. member가 작업, score가 실행 시각(epoch millis)이다.
// 큐는 실행 트리거일 뿐이고 원본 상태는 DB가 갖는다. popDue는 꺼내면서 지우므로, 꺼낸 뒤 장애로 트리거가 유실되면
// Fallback Sweep(6단계)이 DB의 미완료 상태를 찾아 다시 등록한다.
@Component
@RequiredArgsConstructor
public class RedisDelayQueue {

    // ZADD LT: 없으면 추가, 있으면 새 시각이 더 이를 때만 갱신 (Redis 6.2+). 이미 잡힌 작업을 뒤로 미루지 않는다
    private static final DefaultRedisScript<Long> ENQUEUE_IF_EARLIER = new DefaultRedisScript<>("""
            return redis.call('ZADD', KEYS[1], 'LT', ARGV[1], ARGV[2])
            """, Long.class);

    // 조회와 제거를 한 스크립트에서 해 두 Worker가 같은 작업을 꺼내지 못하게 한다
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> POP_DUE = new DefaultRedisScript<>("""
            local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            if #due > 0 then
                redis.call('ZREM', KEYS[1], unpack(due))
            end
            return due
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    //신규 등록. 같은 작업이 이미 있으면 더 이른 시각만 남긴다 — 웹훅 수신·
    // Fallback 재등록이 쓴다.
    public void enqueueIfEarlier(String queueKey, String member, Instant executeAt) {
        redisTemplate.execute(ENQUEUE_IF_EARLIER, List.of(queueKey), score(executeAt), member);
    }

    // 실행 시각을 의도적으로 바꾼다(뒤로 미루기 포함) — Worker가 "N분 뒤 다시"로 정했을 때 쓴다.
    public void reschedule(String queueKey, String member, Instant executeAt) {
        redisTemplate.opsForZSet().add(queueKey, member, executeAt.toEpochMilli());
    }

    // 실행 시각이 지난 작업을 오래된 순으로 최대 batchSize개 꺼내고 큐에서 지운다. 없으면 빈 목록.
    @SuppressWarnings("unchecked")
    public List<String> popDue(String queueKey, Instant now, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 1 이상이어야 합니다: " + batchSize);
        }
        List<String> due = redisTemplate.execute(POP_DUE, List.of(queueKey), score(now), Integer.toString(batchSize));
        return due == null ? List.of() : due;
    }

    private static String score(Instant at) {
        return Long.toString(at.toEpochMilli());
    }
}
