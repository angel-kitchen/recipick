package com.recipick.domain.member.service;

import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SecurityEvents;
import com.recipick.global.security.SessionStatusChecker;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionStatusService implements SessionStatusChecker {
    private final RedisRefreshTokenStore sessions;

    @Override
    public void requireActive(AuthPrincipal principal) {
        if (principal.sessionId() == null
                || !sessions.exists(principal.type().name(), principal.id(), principal.sessionId())) {
            SecurityEvents.rejected(SecurityEvents.Event.SESSION_REVOKED, principal.type(), principal.id());
            throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
        }
    }
}
