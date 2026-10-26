package com.recipick.domain.recipe.scheduler;

import com.recipick.domain.recipe.service.AutoCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCartCleanupScheduler {

    private static final int PREVIEW_RETENTION_DAYS = 7;

    private final AutoCartService autoCartService;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void deleteExpiredPreviews() {
        LocalDateTime before = LocalDateTime.now(clock).minusDays(PREVIEW_RETENTION_DAYS);
        int deleted = autoCartService.deleteExpiredPreviews(before);
        log.info("만료된 자동 구성 미리보기 정리: before={}, deleted={}", before, deleted);
    }
}
