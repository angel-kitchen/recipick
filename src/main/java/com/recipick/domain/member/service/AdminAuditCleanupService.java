package com.recipick.domain.member.service;

import com.recipick.domain.member.repository.AdminAuditLogRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditCleanupService {
    private final AdminAuditLogRepository logs;

    @Transactional
    public int deleteExpiredBatch(LocalDateTime cutoff) {
        return logs.deleteExpiredBatch(cutoff);
    }
}
