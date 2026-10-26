package com.recipick.infra.portone;

// PortOne 웹훅 설정. 시크릿은 API Secret과 다른 값이며 환경 변수나 application-local.yml에서만 읽고 로그·응답에 남기지 않는다.

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portone.webhook")
public record PortOneWebhookProperties(
    String secret
) {
}
