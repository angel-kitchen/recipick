package com.recipick.domain.recipe.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {

    AI_REQUEST_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "AI-001", "AI 요청은 1분에 5번까지 할 수 있습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
