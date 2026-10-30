package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiRequestMetrics {

    static final String REQUESTS = "recipick.ai.requests";
    static final String LATENCY = "recipick.ai.latency";
    static final String TOKENS = "recipick.ai.tokens";
    private static final String NO_FAILURE = "NONE";

    private final MeterRegistry meterRegistry;

    // 새 태그 조합이 첫 요청 때 1로 처음 나타나면 Prometheus의 increase·rate는 그 요청을 0으로 계산한다.
    // AI 요청은 드물어 첫 실패가 대시보드에서 사라질 수 있으므로, 결과 상태·실패 사유 조합을 미리 0으로 등록한다
    public AiRequestMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (AiRequestType requestType : AiRequestType.values()) {
            String type = requestType.name();
            latencyTimer(type);
            for (AiResultStatus status : AiResultStatus.values()) {
                if (status != AiResultStatus.FALLBACK) {
                    requestCounter(type, status.name(), NO_FAILURE);
                }
            }
            for (AiFailureType failureType : AiFailureType.values()) {
                requestCounter(type, AiResultStatus.FALLBACK.name(), failureType.name());
            }
        }
    }

    public void record(AiRequestType requestType, AiResultStatus resultStatus, AiResult<?> aiResult) {
        String type = requestType.name();
        AiUsage usage = aiResult.usage();

        String failure = aiResult.isSuccess() ? NO_FAILURE : aiResult.failureType().name();
        requestCounter(type, resultStatus.name(), failure).increment();
        latencyTimer(type).record(Duration.ofMillis(usage.latencyMs()));

        addTokens(type, usage.model(), "input", usage.inputTokens());
        addTokens(type, usage.model(), "output", usage.outputTokens());
        addTokens(type, usage.model(), "cache_read", usage.cacheReadTokens());
    }

    private Counter requestCounter(String type, String status, String failure) {
        return Counter.builder(REQUESTS)
            .tag("type", type)
            .tag("status", status)
            .tag("failure", failure)
            .register(meterRegistry);
    }

    private Timer latencyTimer(String type) {
        return Timer.builder(LATENCY)
            .tag("type", type)
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    // 모델명은 실행 중에 정해지는 값이라 미리 등록하지 않는다. 모델마다 첫 요청의 토큰은 증가량에서 빠질 수 있다
    private void addTokens(String type, String model, String kind, Integer tokens) {
        if (tokens == null || tokens == 0) {
            return;
        }
        Counter.builder(TOKENS)
            .tag("type", type)
            .tag("model", model)
            .tag("kind", kind)
            .register(meterRegistry)
            .increment(tokens);
    }
}
