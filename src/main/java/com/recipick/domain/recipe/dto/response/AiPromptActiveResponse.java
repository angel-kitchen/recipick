package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.AiPromptType;

// activeVersion이 null이면 코드 기본 지시문을 쓴다
public record AiPromptActiveResponse(
    AiPromptType type,
    Integer activeVersion
) {
}
