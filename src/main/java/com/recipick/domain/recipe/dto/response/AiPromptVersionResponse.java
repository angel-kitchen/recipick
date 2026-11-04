package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.AiPrompt;
import com.recipick.domain.recipe.entity.AiPromptType;
import java.time.LocalDateTime;

public record AiPromptVersionResponse(
    Long promptId,
    AiPromptType type,
    int version,
    String content,
    String memo,
    boolean active,
    Long createdByAdminId,
    LocalDateTime createdAt
) {

    public static AiPromptVersionResponse from(AiPrompt prompt) {
        return new AiPromptVersionResponse(
            prompt.getId(),
            prompt.getPromptType(),
            prompt.getVersion(),
            prompt.getContent(),
            prompt.getMemo(),
            prompt.isActive(),
            prompt.getCreatedByAdminId(),
            prompt.getCreatedAt());
    }
}
