package com.recipick.domain.member.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recipick.domain.member.service.AdminAuditCleanupService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminAuditCleanupSchedulerTest {
    @Test
    @DisplayName("한국 시간 기준 1년 전을 고정해 묶음 삭제를 반복하고 남은 건수가 적으면 종료한다")
    void deletesInBatchesWithOneCutoff() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDateTime cutoff = LocalDateTime.of(2025, 10, 28, 9, 0);
        AdminAuditCleanupService service = mock(AdminAuditCleanupService.class);
        given(service.deleteExpiredBatch(cutoff)).willReturn(1000, 1000, 2);
        // when
        new AdminAuditCleanupScheduler(service, clock).cleanup();
        // then
        verify(service, times(3)).deleteExpiredBatch(cutoff);
    }
}
