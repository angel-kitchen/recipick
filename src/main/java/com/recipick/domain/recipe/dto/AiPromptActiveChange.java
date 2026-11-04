package com.recipick.domain.recipe.dto;

import com.recipick.domain.recipe.entity.AiPromptType;
import java.util.Objects;

// 활성 버전 변경 결과. 감사 대상은 새로 켠 버전, 되돌리기면 꺼진 버전이다. 버전 번호가 null이면 코드 기본 지시문이다
public record AiPromptActiveChange(
    AiPromptType type,
    Long targetPromptId,
    Integer beforeVersion,
    Integer afterVersion
) {

    public boolean changed() {
        return !Objects.equals(beforeVersion, afterVersion);
    }
}
