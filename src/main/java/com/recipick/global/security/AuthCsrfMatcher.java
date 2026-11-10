package com.recipick.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.web.util.matcher.RequestMatcher;

// 브라우저 쿠키를 발급·교체·폐기하는 인증 경로만 보호한다. Bearer API·PG 웹훅과 분리한다.
public final class AuthCsrfMatcher implements RequestMatcher {
    private static final Set<String> PATHS = Set.of(
            "/api/auth/signup", "/api/auth/login", "/api/auth/reissue", "/api/auth/logout",
            "/api/admin/auth/login", "/api/admin/auth/reissue", "/api/admin/auth/logout");

    @Override
    public boolean matches(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && PATHS.contains(request.getRequestURI().substring(request.getContextPath().length()));
    }
}
