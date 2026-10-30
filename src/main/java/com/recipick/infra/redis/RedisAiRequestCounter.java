package com.recipick.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisAiRequestCounter {

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public long increment(Long memberId, Duration window) {
        Long count = redisTemplate.execute(INCREMENT, List.of(key(memberId)),
            Long.toString(window.toMillis()));
        if (count == null) {
            throw new IllegalArgumentException("Redis AI 요청 수 집계 결과가 없습니다.");
        }
        return count;
    }

    private String key(Long memberId) {
        return "ai:request:" + memberId;
    }
}
