package com.recipick.global.security;

public record RefreshTokenDetails(AuthPrincipal principal, String tokenId, String sessionId) {
}
