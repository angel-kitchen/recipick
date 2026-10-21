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
public class RedisRefreshTokenStore {

    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void save(String subjectType, Long subjectId, String sessionId, String token, Duration expiration) {
        redisTemplate.opsForValue().set(key(subjectType, subjectId, sessionId), hash(token), expiration);
    }

    public boolean rotate(String subjectType, Long subjectId, String sessionId,
            String oldToken, String newToken, Duration expiration) {
        Long result = redisTemplate.execute(ROTATE, List.of(key(subjectType, subjectId, sessionId)),
                hash(oldToken), hash(newToken), Long.toString(expiration.toMillis()));
        if (result == null) {
            throw new IllegalStateException("Redis 토큰 교체 결과가 없습니다.");
        }
        return result == 1;
    }

    public void delete(String subjectType, Long subjectId, String sessionId) {
        // 회전 중에도 같은 세션 키를 삭제하므로 로그아웃 뒤 새 토큰이 남지 않는다.
        redisTemplate.delete(key(subjectType, subjectId, sessionId));
    }

    private String key(String subjectType, Long subjectId, String sessionId) {
        return "auth:refresh:" + subjectType + ":" + subjectId + ":" + sessionId;
    }

    private String hash(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
