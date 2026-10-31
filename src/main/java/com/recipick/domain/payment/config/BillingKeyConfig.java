package com.recipick.domain.payment.config;

import ch.qos.logback.classic.LoggerContext;
import com.recipick.domain.payment.service.BillingKeyIssuanceVerifier;
import com.recipick.infra.portone.PortOneProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 카드 등록 설정 바인딩, 순수 검증기 빈, 빌링키 원문 로그 차단 필터 등록(실패 시 기동 실패).
// 검증기는 Spring을 모르는 클래스라 단위 테스트가 직접 만든다
@Configuration
@EnableConfigurationProperties(BillingKeyProperties.class)
public class BillingKeyConfig {

    @Bean
    public BillingKeyIssuanceVerifier billingKeyIssuanceVerifier(PortOneProperties properties) {
        return new BillingKeyIssuanceVerifier(properties.storeId(), properties.channelKey());
    }

    // Logback(기본 로깅)의 TurboFilter로 단다 — appender보다 앞에서 판정하므로 콘솔·JSON 어느 출력에도 닿지 않는다.
    // 한 JVM에 컨텍스트가 여럿 뜨는 테스트에서도 한 번만 단다. 원문 차단은 카드 등록의 필수 조건이라, 달 수 없으면
    // 경고로 넘어가지 않고 기동을 실패시킨다
    @PostConstruct
    void registerBillingKeyLogMasking() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            throw new IllegalStateException("Logback이 아니라 빌링키 원문 로그 차단 필터를 달 수 없습니다: "
                + LoggerFactory.getILoggerFactory().getClass().getName());
        }
        boolean registered = context.getTurboFilterList().stream()
            .anyMatch(filter -> filter instanceof BillingKeyLogMaskingFilter);
        if (registered) {
            return;
        }
        BillingKeyLogMaskingFilter filter = new BillingKeyLogMaskingFilter();
        filter.setContext(context);
        filter.start();
        context.addTurboFilter(filter);
    }
}
