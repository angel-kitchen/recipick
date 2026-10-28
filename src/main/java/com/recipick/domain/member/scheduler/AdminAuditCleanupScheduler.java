package com.recipick.domain.member.scheduler;

import com.recipick.domain.member.service.AdminAuditCleanupService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminAuditCleanupScheduler {
    private final AdminAuditCleanupService service;
    private final Clock clock;

    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusYears(1);
        // 조건부 삭제라 중복 실행해도 안전하며, 각 묶음은 별도 트랜잭션으로 처리한다.
        while (service.deleteExpiredBatch(cutoff) == 1000) {
            // 남은 만료 기록을 다음 묶음에서 처리한다.
        }
    }
}
