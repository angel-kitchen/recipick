package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.response.MembershipGrantResponse;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditField;
import java.util.Map;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipGrantService {

    private static final int GRANT_DAYS = 30;

    private final AdminRepository adminRepository;
    private final MembershipRepository membershipRepository;
    private final AdminAuditRecorder auditRecorder;

    public Admin getGrantAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ACCESS_DENIED));
        validateAdmin(admin);
        return admin;
    }

    // 호출자는 회원을 먼저 잠그고 지급 차단 조건을 확인한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipGrantResponse grant(Member member, Admin admin, LocalDateTime now) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("멤버십 지급은 쓰기 가능한 바깥 트랜잭션이 필요합니다.");
        }
        validateAdmin(admin);
        Membership membership = membershipRepository.save(Membership.builder()
                .member(member).grantedByAdmin(admin).type(MembershipType.PAID).source(MembershipSource.ADMIN_GRANT)
                .startAt(now).activatedAt(now).endAt(now.plusDays(GRANT_DAYS)).autoRenew(false).build());
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.MEMBERSHIP_GRANT, membership.getId(),
                member.getMartId(), Map.of(
                        AdminAuditField.TYPE, new AdminAuditChange(null, membership.getType()),
                        AdminAuditField.SOURCE, new AdminAuditChange(null, membership.getSource()),
                        AdminAuditField.START_AT, new AdminAuditChange(null, membership.getStartAt()),
                        AdminAuditField.END_AT, new AdminAuditChange(null, membership.getEndAt()),
                        AdminAuditField.AUTO_RENEW, new AdminAuditChange(null, membership.isAutoRenew()))));
        return new MembershipGrantResponse(membership.getId(), member.getId(), membership.getType().name(),
                membership.getSource().name(), membership.getStartAt(), membership.getEndAt(), admin.getId());
    }

    private void validateAdmin(Admin admin) {
        if (!admin.isActive()) {
            throw new BusinessException(AuthErrorCode.INACTIVE_ADMIN);
        }
        if (admin.getRole() != AdminRole.SUPER_ADMIN) {
            throw new BusinessException(AuthErrorCode.SUPER_ADMIN_ONLY);
        }
    }
}
