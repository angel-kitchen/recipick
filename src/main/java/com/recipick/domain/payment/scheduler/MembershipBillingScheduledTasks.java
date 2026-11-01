package com.recipick.domain.payment.scheduler;

import com.recipick.domain.payment.facade.MembershipChargeRecoveryFacade;
import com.recipick.domain.payment.facade.TrialConversionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 멤버십 자동결제의 주기 실행만 모아 둔다. 이 빈은 test 프로필에서 만들지 않는다 —
// 테스트는 runOnce()·recoverOnce()를 직접 불러 실행 시점을 통제한다. 두 작업은 전용 스케줄러(스레드 1개)에서 순서대로 돈다.
// 분산 락 없음 — 두 서버가 같은 대상을 집어도 MEMBER 잠금·재확인·회차 UNIQUE·선점 TX로 한 건만 처리된다(K-3)
@Profile("!test")
@Component
@RequiredArgsConstructor
public class MembershipBillingScheduledTasks {

    private final TrialConversionFacade trialConversionFacade;
    private final MembershipChargeRecoveryFacade recoveryFacade;

    // 5분마다. 플래그는 Facade가 본다(OFF면 즉시 0)
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000,
        scheduler = MembershipBillingSchedulingConfig.MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public void convertTrials() {
        trialConversionFacade.runOnce();
    }

    // 5분마다. 플래그 무관 — 이미 생긴 결제의 정합화는 멈추지 않는다
    @Scheduled(fixedDelay = 300_000, initialDelay = 180_000,
        scheduler = MembershipBillingSchedulingConfig.MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public void recoverCharges() {
        recoveryFacade.recoverOnce();
    }
}
