package com.recipick.infra.ai.dto;

public record AiResult<T>(T content, AiFailureType failureType, AiUsage usage) {

    public static <T> AiResult<T> success(T content, AiUsage usage) {
        return new AiResult<>(content, null, usage);
    }

    public static <T> AiResult<T> failure(AiFailureType failureType, AiUsage usage) {
        return new AiResult<>(null, failureType, usage);
    }

    public boolean isSuccess() {
        return failureType == null;
    }
}
