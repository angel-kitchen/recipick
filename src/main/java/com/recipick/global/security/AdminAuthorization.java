package com.recipick.global.security;

import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthorization {

    public void checkMartAccess(Long targetMartId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BusinessException(AuthErrorCode.LOGIN_REQUIRED);
        }
        if (principal.type() != SubjectType.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        if (targetMartId == null || targetMartId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (!"SUPER_ADMIN".equals(principal.role()) && !targetMartId.equals(principal.martId())) {
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }
    }
}
