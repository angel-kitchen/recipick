package com.recipick.domain.member.dto.response;

import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditTarget;
import java.time.LocalDateTime;
import java.util.Map;

public record AdminAuditDetailResponse(Long id, Long adminId, AdminAuditAction action,
        AdminAuditTarget targetType, Long targetId, Long martId, Map<String, Map<String, Object>> details,
        String ipAddress, LocalDateTime createdAt) {
    public static AdminAuditDetailResponse from(AdminAuditLog log) {
        return new AdminAuditDetailResponse(log.getId(), log.getAdmin().getId(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getMartId(), log.getDetails(),
                log.getIpAddress(), log.getCreatedAt());
    }

    @Override
    public String toString() {
        return "AdminAuditDetailResponse[REDACTED]";
    }
}
