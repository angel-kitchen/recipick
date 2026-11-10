package com.recipick.global.security;

import lombok.extern.slf4j.Slf4j;

// 성공 업무와 함께 롤백되는 감사 기록과 달리 실패 보안 이벤트만 최소 정보로 남긴다.
@Slf4j
public final class SecurityEvents {
    private SecurityEvents() {
    }

    public enum Event {
        TOKEN_REJECTED, SESSION_REVOKED, ADMIN_STATE_REJECTED, PASSWORD_CHECK_FAILED,
        PASSWORD_CHECK_LOCKED, REFRESH_REJECTED, AUTH_STORE_UNAVAILABLE
    }

    public static void rejected(Event event, SubjectType type, Long subjectId) {
        // 비밀번호·토큰·쿠키·검색어·이메일·전화번호·예외 메시지는 기록하지 않는다.
        log.warn("보안 이벤트 event={} subjectType={} subjectId={}", event, type, subjectId);
    }
}
