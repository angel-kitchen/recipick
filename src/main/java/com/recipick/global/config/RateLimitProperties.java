package com.recipick.global.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("recipick.rate-limit")
public record RateLimitProperties(
    boolean enabled,
    @NotNull
    Duration window,
    @Min(1)
    int authLimit,
    @Min(1)
    int apiLimit
) {
}
