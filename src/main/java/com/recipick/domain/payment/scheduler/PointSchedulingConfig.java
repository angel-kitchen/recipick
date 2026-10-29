package com.recipick.domain.payment.scheduler;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 포인트 만료 배치를 기본 스케줄러와 다른 스레드에서 돌리기 위한 설정. 기본 스케줄러는 스레드가 하나라 같은 스레드에서 배치를 돌리면
// 결제 지연 큐 폴링(1초 주기)이 배치가 끝날 때까지 멈춘다.
// 포인트 전용 스케줄러만 빈으로 만들면 Spring Boot가 기본 스케줄러(taskScheduler)를 만들지 않고, 하나뿐인 그 빈이 모든 @Scheduled
// 작업의 기본값이 된다. 그래서 Boot가 만들던 기본 스케줄러도 Boot가 주는 같은 빌더로 이름 그대로 함께 만든다 — 설정값은 그대로 이어받는다.
// 스케줄러를 지정하지 않은 기존 작업은 이름이 taskScheduler인 빈을, 포인트 만료만 @Scheduled(scheduler)로 전용 빈을 쓴다.
// 테스트는 예약 실행을 하지 않으므로 이 설정도 만들지 않는다(Boot 기본 스케줄러를 그대로 쓴다)
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class PointSchedulingConfig {

    public static final String POINT_EXPIRATION_TASK_SCHEDULER = "pointExpirationTaskScheduler";

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    // 하루 한 번 도는 배치라 스레드 하나면 충분하다. 만료 작업 자체를 병렬로 돌리려는 것이 아니다
    @Bean(name = POINT_EXPIRATION_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler pointExpirationTaskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder
            .poolSize(1)
            .threadNamePrefix("point-expiration-")
            .build();
    }
}
