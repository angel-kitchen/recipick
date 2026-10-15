package com.recipick.global.response;

import com.recipick.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        List<FieldError> errors,
        LocalDateTime timestamp
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(false, errorCode.getCode(), errorCode.getMessage(), errors, LocalDateTime.now());
    }

    public record FieldError(String field, String reason) {
    }
}
