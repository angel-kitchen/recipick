package com.recipick.global.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security.login")
public record LoginProperties(
        @Min(1) int maxFailures,
        @NotNull Duration failureExpiration,
        @NotNull Duration lockDuration,
        boolean refreshCookieSecure
) {
    public LoginProperties {
        if ((failureExpiration != null && failureExpiration.toMillis() < 1)
                || (lockDuration != null && lockDuration.toMillis() < 1)) {
            throw new IllegalArgumentException("로그인 제한 시간은 1밀리초 이상이어야 합니다.");
        }
    }
}
