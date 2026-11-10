package com.recipick.domain.member.service;

import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AdminStatusChecker;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SecurityEvents;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatusService implements AdminStatusChecker {
    private final AdminRepository admins;

    @Override
    public void requireCurrent(AuthPrincipal principal) {
        var admin = admins.findById(principal.id())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.RELOGIN_REQUIRED));
        if (!admin.isActive() || !admin.getRole().name().equals(principal.role())
                || !Objects.equals(admin.getMartId(), principal.martId())) {
            SecurityEvents.rejected(SecurityEvents.Event.ADMIN_STATE_REJECTED, principal.type(), principal.id());
            throw new BusinessException(admin.isActive() ? AuthErrorCode.RELOGIN_REQUIRED : AuthErrorCode.INACTIVE_ADMIN);
        }
    }
}
