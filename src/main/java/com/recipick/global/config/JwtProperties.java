package com.recipick.global.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration
) {
    public JwtProperties {
        if (accessTokenExpiration != null && (accessTokenExpiration.isZero() || accessTokenExpiration.isNegative())) {
            throw new IllegalArgumentException("액세스 토큰 유효기간은 양수여야 합니다.");
        }
        if (refreshTokenExpiration != null && (refreshTokenExpiration.isZero() || refreshTokenExpiration.isNegative())) {
            throw new IllegalArgumentException("리프레시 토큰 유효기간은 양수여야 합니다.");
        }
    }
}
