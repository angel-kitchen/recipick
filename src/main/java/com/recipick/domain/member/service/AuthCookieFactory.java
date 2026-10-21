package com.recipick.domain.member.service;

import com.recipick.global.config.LoginProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    private final LoginProperties properties;

    public ResponseCookie createRefreshCookie(String token, Duration expiration) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(expiration)
                .build();
    }

    public ResponseCookie clearRefreshCookie() {
        return createRefreshCookie("", Duration.ZERO);
    }
}
