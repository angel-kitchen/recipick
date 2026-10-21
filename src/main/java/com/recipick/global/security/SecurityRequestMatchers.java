package com.recipick.global.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class SecurityRequestMatchers {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    public static final RequestMatcher PUBLIC = new OrRequestMatcher(List.of(
            PATHS.matcher(HttpMethod.POST, "/api/auth/signup"),
            PATHS.matcher(HttpMethod.POST, "/api/auth/login"),
            PATHS.matcher(HttpMethod.POST, "/api/auth/reissue"),
            PATHS.matcher(HttpMethod.POST, "/api/admin/auth/login"),
            PATHS.matcher(HttpMethod.POST, "/api/service-area/check"),
            PATHS.matcher("/api/webhooks/**"),
            PATHS.matcher(HttpMethod.GET, "/api/config/**")
    ));

    private SecurityRequestMatchers() {
    }
}
