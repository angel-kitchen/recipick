package com.recipick.domain.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 운영 플래그 구현. 기동 때 바인딩된 설정값을 그대로 돌려준다
@Component
@RequiredArgsConstructor
public class PropertiesMembershipBillingGate implements MembershipBillingGate {

    private final MembershipBillingProperties properties;

    @Override
    public boolean newChargeEnabled() {
        return properties.enabled();
    }
}
