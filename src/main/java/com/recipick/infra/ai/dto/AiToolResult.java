package com.recipick.infra.ai.dto;

// Tool이 처리하지 못한 경우도 예외 대신 사유를 담아 돌려줘, AI가 사용자에게 자연어로 안내하게 한다
public record AiToolResult<T>(T content, String errorReason) {

    public static <T> AiToolResult<T> success(T content) {
        return new AiToolResult<>(content, null);
    }

    public static <T> AiToolResult<T> error(String reason) {
        return new AiToolResult<>(null, reason);
    }

    public boolean isError() {
        return errorReason != null;
    }
}
