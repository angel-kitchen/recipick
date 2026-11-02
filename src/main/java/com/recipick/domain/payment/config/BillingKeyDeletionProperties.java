package com.recipick.domain.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("payment.billing-key-deletion")
public record BillingKeyDeletionProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("false") boolean withdrawalEnabled,
    @DefaultValue("60s") Duration readTimeout
) {
    public BillingKeyDeletionProperties {
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("읽기 제한 시간은 양수여야 합니다");
        }
    }

    public boolean withdrawalReady() {
        return enabled && withdrawalEnabled;
    }
}
