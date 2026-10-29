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

    // 두 번 호출한 기능은 이력 한 건에 합쳐 남긴다. 응답을 받지 못한 호출의 토큰은 비어 있으므로 받은 만큼만 더한다
    public AiUsage plus(AiUsage other) {
        return new AiUsage(
            model,
            sum(inputTokens, other.inputTokens),
            sum(outputTokens, other.outputTokens),
            sum(cacheReadTokens, other.cacheReadTokens),
            latencyMs + other.latencyMs);
    }

    private static Integer sum(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + b;
    }
}
