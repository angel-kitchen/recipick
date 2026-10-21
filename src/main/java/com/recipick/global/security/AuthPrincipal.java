package com.recipick.global.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthPrincipal(SubjectType type, Long id, String role, Long martId) {

    public AuthPrincipal {
        if (type == null || id == null || id <= 0) {
            throw new IllegalArgumentException("인증 주체가 올바르지 않습니다.");
        }
        if (type == SubjectType.MEMBER && (role != null || martId != null)) {
            throw new IllegalArgumentException("회원 토큰에는 관리자 권한을 넣을 수 없습니다.");
        }
        if (type == SubjectType.ADMIN) {
            boolean superAdmin = "SUPER_ADMIN".equals(role) && martId == null;
            boolean martAdmin = "MART_ADMIN".equals(role) && martId != null && martId > 0;
            if (!superAdmin && !martAdmin) {
                throw new IllegalArgumentException("관리자 권한 범위가 올바르지 않습니다.");
            }
        }
    }

    public List<SimpleGrantedAuthority> authorities() {
        String authority = type == SubjectType.MEMBER ? "MEMBER" : role;
        return List.of(new SimpleGrantedAuthority("ROLE_" + authority));
    }
}
