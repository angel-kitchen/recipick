package com.recipick.infra.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore {

    private final StringRedisTemplate redisTemplate;

    public void save(String subjectType, Long subjectId, String tokenId, String token, Duration expiration) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            String key = "auth:refresh:" + subjectType + ":" + subjectId + ":" + tokenId;
            redisTemplate.opsForValue().set(key, HexFormat.of().formatHex(hash), expiration);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
