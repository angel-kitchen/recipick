package com.recipick.infra.ai.dto;

// servings가 null이면 사용자가 인분을 말하지 않은 것이라 서버가 레시피 기본 인분을 쓴다
public record AiPlannedMeal(
    Long recipeId,
    Integer servings,
    String reason
) {
}
