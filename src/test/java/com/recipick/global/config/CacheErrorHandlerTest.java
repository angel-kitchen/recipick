package com.recipick.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;

class CacheErrorHandlerTest {

    private final CacheErrorHandler handler = new CacheConfig().errorHandler();
    private final Cache cache = new ConcurrentMapCache("categories");
    private final RuntimeException redisDown = new RuntimeException("Redis connection refused");

    @Test
    @DisplayName("Redis 오류가 나도 캐시 조회·저장·삭제 예외를 밖으로 던지지 않는다")
    void cacheErrors_areSwallowed() {
        assertThatCode(() -> {
            handler.handleCacheGetError(redisDown, cache, "k");
            handler.handleCachePutError(redisDown, cache, "k", "v");
            handler.handleCacheEvictError(redisDown, cache, "k");
            handler.handleCacheClearError(redisDown, cache);
        }).doesNotThrowAnyException();
    }
}
