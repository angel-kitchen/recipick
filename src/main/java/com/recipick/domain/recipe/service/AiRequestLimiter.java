package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.exception.AiErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.redis.RedisAiRequestCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 첫 요청부터 1분 동안 5번까지 허용하는 고정 구간 방식이다. 경계에서 잠깐 10번까지 몰릴 수 있지만 비용 상한으로는 충분하다
@Component
@RequiredArgsConstructor
public class AiRequestLimiter {

    private static final int MAX_REQUEST = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisAiRequestCounter counter;

    public void acquire(Long memberId) {
        if (counter.increment(memberId, WINDOW) > MAX_REQUEST) {
            throw new BusinessException(AiErrorCode.AI_REQUEST_LIMIT_EXCEEDED);
        }
    }
}
