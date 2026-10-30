package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AiRequestMetrics {

    static final String REQUESTS = "recipick.ai.requests";
    static final String LATENCY = "recipick.ai.latency";
    static final String TOKENS = "recipick.ai.tokens";
    private static final String NO_FAILURE = "NONE";

    private final MeterRegistry meterRegistry;

    public void record(AiRequestType requestType, AiResultStatus resultStatus, AiResult<?> aiResult) {
        String type = requestType.name();
        AiUsage usage = aiResult.usage();

        Counter.builder(REQUESTS)
            .tag("type", type)
            .tag("status", resultStatus.name())
            .tag("failure", aiResult.isSuccess() ? NO_FAILURE : aiResult.failureType().name())
            .register(meterRegistry)
            .increment();

        Timer.builder(LATENCY)
            .tag("type", type)
            .publishPercentileHistogram()
            .register(meterRegistry)
            .record(Duration.ofMillis(usage.latencyMs()));

        addTokens(type, usage.model(), "input", usage.inputTokens());
        addTokens(type, usage.model(), "output", usage.outputTokens());
        addTokens(type, usage.model(), "cache_read", usage.cacheReadTokens());
    }

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
