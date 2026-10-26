package com.recipick.domain.payment.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

// 결제 지연 큐 Worker의 @Schedule를 켠다. test 프로필에서는 끈다 ㅡ 테스트는 Worker.poll() 을 직접 불러 실행 시점을 통제한다
@Profile("!test")
@Configuration
@EnableScheduling
public class PaymentSchedulingConfig {
}
