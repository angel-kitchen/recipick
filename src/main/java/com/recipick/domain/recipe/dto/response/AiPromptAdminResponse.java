package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.AiPrompt;
import com.recipick.domain.recipe.entity.AiPromptType;
import java.util.List;

// 새 버전을 쓸 때 기본 지시문을 고쳐 시작할 수 있도록 함께 내려준다. activeVersion이 null이면 기본 지시문을 쓰는 중이다
public record AiPromptAdminResponse(
    AiPromptType type,
    String defaultContent,
    Integer activeVersion,
    List<AiPromptVersionResponse> versions
) {

    public static AiPromptAdminResponse of(AiPromptType type, List<AiPrompt> versions) {
        return new AiPromptAdminResponse(
            type,
            type.defaultContent(),
            versions.stream().filter(AiPrompt::isActive).map(AiPrompt::getVersion).findFirst().orElse(null),
            versions.stream().map(AiPromptVersionResponse::from).toList());
    }
}
