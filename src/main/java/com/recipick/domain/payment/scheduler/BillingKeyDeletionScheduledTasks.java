package com.recipick.domain.payment.scheduler;

import com.recipick.domain.payment.facade.BillingKeyDeletionRecoveryFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class BillingKeyDeletionScheduledTasks {
    private final BillingKeyDeletionRecoveryFacade recovery;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT30S",
        scheduler = BillingKeyDeletionSchedulingConfig.SCHEDULER)
    public void requestReplacedKeys() {
        try {
            recovery.requestReplacedOnce();
        } catch (RuntimeException e) {
            log.warn("교체 키 정리 스캔 실패: cause={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S",
        scheduler = BillingKeyDeletionSchedulingConfig.SCHEDULER)
    public void recoverDeletions() {
        try {
            recovery.recoverOnce();
        } catch (RuntimeException e) {
            log.warn("빌링키 삭제 복구 스캔 실패: cause={}", e.getClass().getSimpleName());
        }
    }
}
