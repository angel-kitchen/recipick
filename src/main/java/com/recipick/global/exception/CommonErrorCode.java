package com.recipick.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON-001", "입력값이 올바르지 않습니다."),
    INVALID_REQUEST_FORMAT(HttpStatus.BAD_REQUEST, "COMMON-002", "요청 형식이 올바르지 않습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON-003", "필수 파라미터가 없습니다."),
    NOT_FOUND_PATH(HttpStatus.NOT_FOUND, "COMMON-004", "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-005", "지원하지 않는 메서드입니다."),
    DUPLICATE_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "COMMON-006", "같은 요청을 처리하고 있습니다. 잠시 후 다시 확인해 주세요."),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "COMMON-007", "Idempotency-Key 헤더가 필요합니다."),
    EXTERNAL_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-008", "외부 서비스 응답에 문제가 있습니다."),
    EXTERNAL_SERVICE_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-009", "외부 서비스 응답이 지연되고 있습니다."),
    LOCK_ACQUISITION_TIMEOUT(HttpStatus.CONFLICT, "COMMON-010", "다른 요청이 같은 자원을 처리하고 있습니다. 잠시 후 다시 시도해 주세요."),
    IDEMPOTENCY_KEY_PAYLOAD_MISMATCH(HttpStatus.CONFLICT, "COMMON-011", "같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON-012", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
