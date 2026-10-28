package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.request.AdminAuditSearchRequest;
import com.recipick.domain.member.dto.response.AdminAuditDetailResponse;
import com.recipick.domain.member.dto.response.AdminAuditSummaryResponse;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.AdminAuditLogRepository;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.global.audit.AdminAuditRequestContext;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditQueryService {
    private final AdminAuditLogRepository logs;
    private final AdminRepository admins;
    private final AdminAuditRequestContext context;

    public PageResponse<AdminAuditSummaryResponse> search(AdminAuditSearchRequest request) {
        requireSuperAdmin();
        return PageResponse.from(logs.search(request.from(), request.to(), request.adminId(), request.action(),
                request.targetType(), request.toPageable()).map(AdminAuditSummaryResponse::from));
    }

    public AdminAuditDetailResponse get(Long id) {
        requireSuperAdmin();
        return AdminAuditDetailResponse.from(logs.findById(id)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.AUDIT_LOG_NOT_FOUND)));
    }

    private void requireSuperAdmin() {
        AuthPrincipal principal = context.requireAdmin();
        Admin admin = admins.findById(principal.id())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ACCESS_DENIED));
        if (!admin.isActive()) {
            throw new BusinessException(AuthErrorCode.INACTIVE_ADMIN);
        }
        if (admin.getRole() != AdminRole.SUPER_ADMIN || !"SUPER_ADMIN".equals(principal.role())
                || principal.martId() != null) {
            throw new BusinessException(AuthErrorCode.SUPER_ADMIN_ONLY);
        }
    }
}
