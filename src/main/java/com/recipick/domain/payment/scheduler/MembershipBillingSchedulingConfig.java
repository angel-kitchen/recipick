package com.recipick.domain.payment.scheduler;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 멤버십 자동결제 작업(체험 전환·월 갱신·결제 복구·갱신 회차 정리) 전용 스케줄러. PG 호출이 섞여 한 번에 수십 초가 걸릴 수 있어 기본 스케줄러
// (결제 지연 큐 1초 폴링)와 포인트 만료 스케줄러의 스레드를 막지 않도록 따로 둔다.
// 기본 taskScheduler·pointExpirationTaskScheduler·applicationTaskExecutor는 PointSchedulingConfig가 이미 만들므로 여기서는
// 전용 빈 하나만 추가하고 기존 설정은 손대지 않는다. 테스트는 이 설정을 만들지 않는다(Facade를 직접 부른다)
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class MembershipBillingSchedulingConfig {

    public static final String MEMBERSHIP_BILLING_TASK_SCHEDULER = "membershipBillingTaskScheduler";

    // 스레드 둘: PG 지연이 걸린 청구 작업 하나가 정합화·정리 작업까지 줄 세우지 않게 한다. 다만 두 청구 작업이 두 스레드를
    // 모두 잡으면 정리도 늦어질 수 있다 — 자원 격리 보장이 아니다. 같은 대상의 중복 처리는 스레드 수가 아니라 DB 잠금이 막는다
    @Bean(name = MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler membershipBillingTaskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder
            .poolSize(2)
            .threadNamePrefix("membership-billing-")
            .build();
    }
}
