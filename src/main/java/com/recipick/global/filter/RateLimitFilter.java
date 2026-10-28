package com.recipick.global.filter;

import com.recipick.global.config.RateLimitProperties;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.security.SecurityErrorResponseWriter;
import com.recipick.infra.redis.RedisRateLimitStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(name = "recipick.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH_PREFIX = "/api/webhooks/";

    private final RateLimitProperties properties;
    private final RedisRateLimitStore store;
    private final SecurityErrorResponseWriter responseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.equals("/api/webhooks") || uri.startsWith(WEBHOOK_PATH_PREFIX);
    }
    private static final String AUTH_PATH_PREFIX ="/api/auth/";
    private static final String ADMIN_AUTH_PATH_PREFIX ="/api/admin/auth/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String uri = request.getRequestURI();
        boolean auth = uri.startsWith(AUTH_PATH_PREFIX) || uri.startsWith(ADMIN_AUTH_PATH_PREFIX);
        String policy = auth ? "auth" : "api";
        int limit = auth ? properties.authLimit() : properties.apiLimit();
        String key = "recipick:rate:" + policy + ":" + request.getRemoteAddr();



        long count;
        try{
            count = store.increment(key, properties.window());
        } catch (RuntimeException exception){
            log.warn("요청 수 제한 카운터 오류로 제한 없이 통과합니다.", exception);
            chain.doFilter(request, response);
            return;
        }
        if(count> limit){
            responseWriter.write(response, CommonErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }
}
