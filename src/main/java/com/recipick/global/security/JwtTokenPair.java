package com.recipick.global.security;

import java.time.Duration;

public record JwtTokenPair(
        String accessToken,
        String refreshToken,
        String refreshTokenId,
        Duration accessExpiration,
        Duration refreshExpiration
) {
    @Override
    public String toString() {
        return "JwtTokenPair[REDACTED]";
    }
}
