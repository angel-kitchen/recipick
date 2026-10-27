package com.recipick.global.security;

public record RefreshTokenDetails(AuthPrincipal principal, String tokenId, String sessionId, long refreshTokenVersion) {

    public RefreshTokenDetails(AuthPrincipal principal, String tokenId, String sessionId) {
        this(principal, tokenId, sessionId, 0);
    }
}
