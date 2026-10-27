package com.recipick.infra.ai.dto;

public enum AiFailureType {
    TIMEOUT,
    RATE_LIMITED,
    UNAVAILABLE,
    REFUSED,
    INVALID_RESPONSE
}
