package com.recipick.domain.recipe.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {

    AI_REQUEST_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "AI-001", "AI 요청은 1분에 5번까지 할 수 있습니다. 잠시 후 다시 시도해 주세요."),
    AI_PROMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI-002", "AI 프롬프트 버전을 찾을 수 없습니다."),
    AI_PROMPT_VERSION_CONFLICT(HttpStatus.CONFLICT, "AI-003", "다른 관리자가 같은 종류의 프롬프트를 먼저 저장했습니다. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
