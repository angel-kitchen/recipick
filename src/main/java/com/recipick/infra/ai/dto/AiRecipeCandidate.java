package com.recipick.infra.ai.dto;

import java.util.List;

// 조건 추천 ③에서 "얼큰한", "요린이", 재료 이름처럼 조건 항목에 없는 표현도 AI가 판단하도록 이름과 함께 넘기는 레시피 속성
public record AiRecipeCandidate(
    Long id,
    String name,
    String cuisineType,
    String dishType,
    Integer cookingMinutes,
    Integer spicyLevel,
    String difficulty,
    List<String> requiredIngredients
) {
}
