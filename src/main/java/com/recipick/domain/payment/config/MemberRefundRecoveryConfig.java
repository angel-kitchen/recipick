package com.recipick.domain.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemberRefundRecoveryProperties.class)
public class MemberRefundRecoveryConfig {
}
