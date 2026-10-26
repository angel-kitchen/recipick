package com.recipick.domain.payment.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 결제 지연 큐의 주기 실행만 모아 둔다. 스케줄링 자체는 공용 SchedulingConfig가 켜고, 이 빈은 test 프로필에서 만들지 않는다 —
// 테스트는 Worker.poll()·Fallback.sweep()을 직접 불러 실행 시점을 통제한다.
// 기본 스케줄러는 스레드 하나라 다른 @Scheduled 작업과 순서대로 돈다
@Profile("!test")
@Component
@RequiredArgsConstructor
public class PaymentScheduledTasks {

    private final PaymentDelayQueueWorker paymentDelayQueueWorker;
    private final PaymentFallbackScheduler paymentFallbackScheduler;

    // 이전 실행이 끝난 뒤 1초 쉬고 다시 본다(fixedDelay). 한 인스턴스 안에서는 겹쳐 돌지 않는다
    @Scheduled(fixedDelay = 1000)
    public void pollDelayQueue() {
        paymentDelayQueueWorker.poll();
    }

    // 1시간마다. 앱이 뜬 직후 1분 뒤 한 번 돌아 재시작 전 유실분을 바로 잡는다
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void sweepFallback() {
        paymentFallbackScheduler.sweep();
    }
}
