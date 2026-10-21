package com.recipick.global.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security.cors")
public record CorsProperties(List<String> allowedOrigins) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            if (origin.contains("*") || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()) {
                throw new IllegalArgumentException("CORS 허용 주소는 와일드카드 없는 http(s) Origin이어야 합니다.");
            }
        }
    }
}
