package com.recipick.domain.member.service;

import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminAuditLogRepository;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.audit.AdminAuditRequestContext;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.security.AuthPrincipal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditService implements AdminAuditRecorder {

    private final AdminAuditLogRepository auditRepository;
    private final AdminRepository adminRepository;
    private final AdminAuditRequestContext requestContext;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AdminAuditCommand command) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("감사 기록은 업무의 쓰기 가능한 트랜잭션 안에서 호출해야 합니다.");
        }
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        AuthPrincipal principal = requestContext.requireAdmin();
        Admin admin = adminRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ACCESS_DENIED));
        if (!admin.isActive()) {
            throw new BusinessException(AuthErrorCode.INACTIVE_ADMIN);
        }
        if (!admin.getRole().name().equals(principal.role())
                || !Objects.equals(admin.getMartId(), principal.martId())) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        if (admin.getRole() == AdminRole.MART_ADMIN) {
            if (!command.action().martAdminAllowed()) {
                throw new BusinessException(AuthErrorCode.SUPER_ADMIN_ONLY);
            }
            if (!Objects.equals(admin.getMartId(), command.martId())) {
                throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
            }
        }
        // 기록 실패가 업무 커밋 뒤에 발견되지 않도록 같은 트랜잭션에서 INSERT를 확인한다.
        auditRepository.saveAndFlush(AdminAuditLog.create(admin, command, requestContext.clientIp()));
    }
}
