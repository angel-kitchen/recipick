package com.recipick.global.security;

import java.time.Instant;

public record RefreshTokenDetails(AuthPrincipal principal, String tokenId, String sessionId,
        long refreshTokenVersion, Instant sessionExpiresAt) {
}
