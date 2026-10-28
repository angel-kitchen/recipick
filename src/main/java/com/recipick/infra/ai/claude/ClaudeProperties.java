package com.recipick.infra.ai.claude;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("ai.claude")
public record ClaudeProperties(
        @NotBlank String apiKey,
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("15s") Duration timeout,
        @DefaultValue("2") int maxRetries,
        @DefaultValue("1024") long maxTokens
) {
}
