package com.recipick.domain.recipe.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record AiRequestLogAdminResponse(
    Long aiRequestId,
    Long memberId,
    AiRequestType requestType,
    String rawText,
    // DB에 JSON으로 저장한 분석 결과를 문자열로 감싸지 않고 JSON 그대로 내보낸다
    @JsonRawValue
    String parsedResult,
    List<Long> matchedRecipeIds,
    Long autoCartRequestId,
    AiResultStatus resultStatus,
    boolean success,
    String failureReason,
    String model,
    Integer inputTokens,
    Integer outputTokens,
    Integer cacheReadTokens,
    int latencyMs,
    LocalDateTime createdAt
) {

    public static AiRequestLogAdminResponse from(AiRequestLog log) {
        return new AiRequestLogAdminResponse(
            log.getId(),
            log.getMemberId(),
            log.getRequestType(),
            log.getRawText(),
            log.getParsedResult(),
            toRecipeIds(log.getMatchedRecipeIds()),
            log.getAutoCartRequest() == null ? null : log.getAutoCartRequest().getId(),
            log.getResultStatus(),
            log.isSuccess(),
            log.getFailureReason(),
            log.getModel(),
            log.getInputTokens(),
            log.getOutputTokens(),
            log.getCacheReadTokens(),
            log.getLatencyMs(),
            log.getCreatedAt());
    }

    private static List<Long> toRecipeIds(String matchedRecipeIds) {
        if (matchedRecipeIds == null) {
            return List.of();
        }
        return Arrays.stream(matchedRecipeIds.split(","))
            .map(Long::valueOf)
            .toList();
    }
}
