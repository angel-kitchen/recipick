package com.recipick.infra.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisLoginAttemptStore {

    private static final DefaultRedisScript<Long> FAILURE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'locked') == '1' then return 1 end
            local failures = redis.call('HINCRBY', KEYS[1], 'failures', 1)
            if failures >= tonumber(ARGV[1]) then
                redis.call('HSET', KEYS[1], 'locked', '1')
                redis.call('PEXPIRE', KEYS[1], ARGV[3])
                return 1
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> SUCCESS = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'locked') == '1' then return 0 end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String subjectType, String loginId) {
        return "1".equals(redisTemplate.opsForHash().get(key(subjectType, loginId), "locked"));
    }

    public boolean recordFailure(String subjectType, String loginId, int maxFailures,
            Duration failureExpiration, Duration lockDuration) {
        Long result = redisTemplate.execute(FAILURE, List.of(key(subjectType, loginId)),
                Integer.toString(maxFailures), Long.toString(failureExpiration.toMillis()),
                Long.toString(lockDuration.toMillis()));
        return requireResult(result) == 1;
    }

    public boolean clearIfUnlocked(String subjectType, String loginId) {
        Long result = redisTemplate.execute(SUCCESS, List.of(key(subjectType, loginId)));
        return requireResult(result) == 1;
    }

    private long requireResult(Long result) {
        if (result == null) {
            throw new IllegalStateException("Redis 로그인 처리 결과가 없습니다.");
        }
        return result;
    }

    private String key(String subjectType, String loginId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(loginId.getBytes(StandardCharsets.UTF_8));
            return "auth:login:" + subjectType + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
