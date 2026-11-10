package com.recipick.domain.member.service;

import com.recipick.global.config.LoginProperties;
import com.recipick.global.security.SubjectType;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {
    public static final String MEMBER_COOKIE = "memberRefreshToken";
    public static final String ADMIN_COOKIE = "adminRefreshToken";
    private final LoginProperties properties;

    public ResponseCookie createRefreshCookie(String token, Duration expiration) {
        return createRefreshCookie(SubjectType.MEMBER, token, expiration);
    }

    public ResponseCookie createRefreshCookie(SubjectType type, String token, Duration expiration) {
        return ResponseCookie.from(type == SubjectType.MEMBER ? MEMBER_COOKIE : ADMIN_COOKIE, token)
                .httpOnly(true).secure(properties.refreshCookieSecure()).sameSite("Strict")
                .path(type == SubjectType.MEMBER ? "/api/auth" : "/api/admin/auth")
                .maxAge(expiration).build();
    }

    public ResponseCookie clearRefreshCookie() {
        return createRefreshCookie("", Duration.ZERO);
    }

    public ResponseCookie clearRefreshCookie(SubjectType type) {
        return createRefreshCookie(type, "", Duration.ZERO);
    }

    public ResponseCookie clearLegacyCookie() {
        return ResponseCookie.from("refreshToken", "").httpOnly(true).secure(properties.refreshCookieSecure())
                .sameSite("Strict").path("/api/auth").maxAge(Duration.ZERO).build();
    }
}
