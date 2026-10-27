package com.recipick.domain.member.scheduler;

import com.recipick.domain.member.service.TrialBlockService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrialBlockCleanupScheduler {
    private final TrialBlockService service;
    private final Clock clock;

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now(clock);
        // 조건부 삭제는 중복 실행해도 안전하며 각 호출은 별도 트랜잭션으로 최대 1,000건만 삭제한다.
        while (service.deleteExpiredBatch(now) == 1000) {
            // 남은 만료 기록을 다음 묶음에서 처리한다.
        }
    }
}
