package com.recipick.infra.portone;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * PortOne V2 API 연동 설정. API Secret은 application-local.yml에서만 읽고 로그·응답에 남기지 않는다.
 */
@Validated
@ConfigurationProperties("portone")
public record PortOneProperties(
        @NotBlank String apiSecret,
        @DefaultValue("https://api.portone.io") String baseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout
) {
}
