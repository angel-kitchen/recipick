package com.recipick.global.audit;

import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AdminAuditRequestContext {

    public AuthPrincipal requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BusinessException(AuthErrorCode.LOGIN_REQUIRED);
        }
        if (principal.type() != SubjectType.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return principal;
    }

    public String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        // 전달 헤더를 직접 해석하지 않고, 신뢰 프록시 설정 이후의 Servlet 접속 주소만 사용한다.
        String address = attributes.getRequest().getRemoteAddr();
        if (address == null || address.length() > 45 || !isNumericAddress(address)) {
            return null;
        }
        try {
            return InetAddress.getByName(address).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private boolean isNumericAddress(String address) {
        return address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
                || (address.contains(":") && address.matches("[0-9a-fA-F:.]+"));
    }
}
