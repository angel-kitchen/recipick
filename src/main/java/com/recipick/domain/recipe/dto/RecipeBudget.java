package com.recipick.domain.recipe.dto;

import java.util.Map;

// 조건 추천에 적용한 예산. maxBudget이 null이면 예산을 말하지 않은 것이고, applied가 false인데 maxBudget이 있으면
// 이용 마트가 없어 금액을 계산하지 못해 예산 없이 추천한 것이다. 예상 금액은 예산을 적용했을 때만 채운다
public record RecipeBudget(
    Integer maxBudget,
    boolean applied,
    Map<Long, Long> estimatedPrices
) {

    public static RecipeBudget notRequested() {
        return new RecipeBudget(null, false, Map.of());
    }

    public static RecipeBudget notApplied(Integer maxBudget) {
        return new RecipeBudget(maxBudget, false, Map.of());
    }

    public Long estimatedPrice(Long recipeId) {
        return estimatedPrices.get(recipeId);
    }
}
