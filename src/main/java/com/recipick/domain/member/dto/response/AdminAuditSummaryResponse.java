package com.recipick.domain.member.dto.response;

import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditTarget;
import java.time.LocalDateTime;

public record AdminAuditSummaryResponse(Long id, Long adminId, AdminAuditAction action,
        AdminAuditTarget targetType, Long targetId, Long martId, LocalDateTime createdAt) {
    public static AdminAuditSummaryResponse from(AdminAuditLog log) {
        return new AdminAuditSummaryResponse(log.getId(), log.getAdmin().getId(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getMartId(), log.getCreatedAt());
    }
}
