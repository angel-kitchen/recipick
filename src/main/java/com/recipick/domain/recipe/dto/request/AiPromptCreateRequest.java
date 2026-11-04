package com.recipick.domain.recipe.dto.request;

import com.recipick.domain.recipe.entity.AiPromptType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiPromptCreateRequest(
    @NotNull AiPromptType type,
    @NotBlank @Size(max = 10000) String content,
    @Size(max = 200) String memo
) {
}
