package com.recipick.domain.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 카드 등록 기능의 운영 스위치. 운영 기본값은 false(application.yml)라 환경변수로만 켠다.
// 실채널 차단은 이 값과 무관하게 BillingKeyIssuanceVerifier가 코드로 한다 — 플래그는 보조 스위치다
// @param registrationEnabled false면 POST /api/billing-keys/issue-requests·POST /api/billing-keys가 503(BILLING-008)
@ConfigurationProperties("recipick.billing-key")
public record BillingKeyProperties(
    boolean registrationEnabled
) {
}
