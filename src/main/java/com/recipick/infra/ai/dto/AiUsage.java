package com.recipick.infra.ai.dto;

public record AiUsage(
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer cacheReadTokens,
        long latencyMs
) {

    public static AiUsage failed(String model, long latencyMs) {
        return new AiUsage(model, null, null, null, latencyMs);
    }
}
