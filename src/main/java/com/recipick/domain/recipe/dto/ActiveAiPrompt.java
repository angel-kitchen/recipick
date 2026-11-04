package com.recipick.domain.recipe.dto;

import com.recipick.domain.recipe.entity.AiPromptType;

// AI 호출에 쓸 지시문. version이 null이면 코드 기본 지시문이다
public record ActiveAiPrompt(
    AiPromptType type,
    Integer version,
    String content
) {
}
