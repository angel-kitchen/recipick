package com.recipick.global.security;

import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

// SecurityFilterChain에만 등록해 서블릿 필터로 중복 실행되지 않게 한다.
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final SecurityErrorResponseWriter responseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityRequestMatchers.PUBLIC.matches(request) || CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<String> headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (headers.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        AuthPrincipal principal;
        try {
            String header = headers.get(0);
            if (headers.size() != 1 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            }
            principal = tokenProvider.parseAccessToken(header.substring(7).trim());
        } catch (BusinessException exception) {
            SecurityContextHolder.clearContext();
            responseWriter.write(response, exception.getErrorCode());
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
