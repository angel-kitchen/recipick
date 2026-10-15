package com.recipick.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH-001", "로그인이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-003", "만료된 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-004", "접근 권한이 없습니다."),
    MART_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-005", "소속 마트의 데이터만 처리할 수 있습니다."),
    SUPER_ADMIN_ONLY(HttpStatus.FORBIDDEN, "AUTH-006", "전체 관리자만 처리할 수 있습니다."),
    INVALID_MEMBER_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-007", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_ADMIN_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-008", "아이디 또는 비밀번호가 올바르지 않습니다."),
    INACTIVE_ADMIN(HttpStatus.FORBIDDEN, "AUTH-009", "비활성화된 관리자 계정입니다."),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "AUTH-010", "웹훅 검증에 실패했습니다."),
    RELOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH-011", "다시 로그인해 주세요."),
    LOGIN_LOCKED(HttpStatus.FORBIDDEN, "AUTH-012", "로그인 시도가 많아 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
