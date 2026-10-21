package com.recipick.global.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

// record로 만들면 success 필드 접근자와 정적 메서드 success()가 충돌해 클래스로 둔다
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_MESSAGE = "요청이 처리되었습니다.";

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, SUCCESS_CODE, message, data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }
}
