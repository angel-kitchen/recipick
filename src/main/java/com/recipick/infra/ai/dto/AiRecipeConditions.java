package com.recipick.infra.ai.dto;

import java.util.List;

// 값 검증과 enum 변환은 domain Service가 하므로, AI가 준 값을 문자열 그대로 넘긴다
public record AiRecipeConditions(
    String cuisineType,
    String dishType,
    Integer maxCookingMinutes,
    Integer maxSpicyLevel,
    List<String> excludedIngredients
) {
}
