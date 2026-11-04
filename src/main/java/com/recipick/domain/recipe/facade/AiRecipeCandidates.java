package com.recipick.domain.recipe.facade;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.infra.ai.dto.AiRecipeCandidate;
import java.util.List;
import java.util.Map;

// 조건 추천·일주일 식단이 AI에 넘기는 레시피 정보. 이름만으로는 맵기·시간·재료 조건을 판단할 수 없어 속성을 함께 넘긴다
final class AiRecipeCandidates {

    private AiRecipeCandidates() {
    }

    static List<AiRecipeCandidate> of(List<Recipe> recipes, Map<Long, List<String>> ingredientsByRecipeId) {
        return recipes.stream()
            .map(recipe -> new AiRecipeCandidate(
                recipe.getId(),
                recipe.getName(),
                nameOrNull(recipe.getCuisine()),
                nameOrNull(recipe.getDishType()),
                recipe.getCookTimeMin(),
                recipe.getSpicyLevel(),
                nameOrNull(recipe.getDifficulty()),
                ingredientsByRecipeId.getOrDefault(recipe.getId(), List.of())))
            .toList();
    }

    // 종류·요리 구분·난이도는 비어 있을 수 있는 컬럼이다
    private static String nameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
