package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// AI가 정리한 조건을 서버 규칙으로 검증한 결과. 맞지 않는 값은 오류 대신 버려서 그 조건 없이 찾는다
public record RecipeRecommendCondition(
    CuisineType cuisineType,
    DishType dishType,
    Integer maxCookingMinutes,
    Integer maxSpicyLevel,
    List<String> includedIngredients,
    List<String> excludedIngredients,
    Integer maxBudget,
    Integer servings
) {

    private static final int MIN_SPICY_LEVEL = 0;
    private static final int MAX_SPICY_LEVEL = 3;
    private static final int MAX_INGREDIENTS = 5;
    private static final int MAX_INGREDIENT_NAME_LENGTH = 50;
    private static final int MIN_BUDGET = 1_000;
    private static final int MAX_BUDGET = 1_000_000;

    public static RecipeRecommendCondition from(AiRecipeConditions conditions) {
        return new RecipeRecommendCondition(
            toEnum(CuisineType.class, conditions.cuisineType()),
            toEnum(DishType.class, conditions.dishType()),
            positiveOrNull(conditions.maxCookingMinutes()),
            spicyLevelOrNull(conditions.maxSpicyLevel()),
            cleanIngredients(conditions.includedIngredients()),
            cleanIngredients(conditions.excludedIngredients()),
            inRangeOrNull(conditions.maxBudget(), MIN_BUDGET, MAX_BUDGET),
            inRangeOrNull(conditions.servings(), Recipe.MIN_SERVINGS, Recipe.MAX_SERVINGS));
    }

    // 예산·인분은 레시피 속성으로 거르는 조건이 아니라 금액 계산에 쓰므로, 비었는지 판단할 때는 보지 않는다
    public boolean isEmpty() {
        return cuisineType == null && dishType == null && maxCookingMinutes == null
            && maxSpicyLevel == null && includedIngredients.isEmpty() && excludedIngredients.isEmpty();
    }

    // 조리 시간·맵기가 비어 있는 레시피는 조건을 만족하는지 알 수 없으므로, 그 조건을 걸었을 때는 빼고 보여 준다
    public boolean matches(Recipe recipe) {
        return (cuisineType == null || cuisineType == recipe.getCuisine())
            && (dishType == null || dishType == recipe.getDishType())
            && (maxCookingMinutes == null
            || (recipe.getCookTimeMin() != null && recipe.getCookTimeMin() <= maxCookingMinutes))
            && (maxSpicyLevel == null
            || (recipe.getSpicyLevel() != null && recipe.getSpicyLevel() <= maxSpicyLevel));
    }

    private static <E extends Enum<E>> E toEnum(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
            .filter(constant -> constant.name().equals(value.strip()))
            .findFirst()
            .orElse(null);
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static Integer inRangeOrNull(Integer value, int min, int max) {
        return value != null && value >= min && value <= max ? value : null;
    }

    private static Integer spicyLevelOrNull(Integer value) {
        return value != null && value >= MIN_SPICY_LEVEL && value <= MAX_SPICY_LEVEL ? value : null;
    }

    // 재료마다 검색 쿼리를 한 번씩 보내므로, 지나치게 많거나 긴 값이 들어와도 개수와 길이를 묶어 둔다
    private static List<String> cleanIngredients(List<String> names) {
        if (names == null) {
            return List.of();
        }
        return names.stream()
            .filter(Objects::nonNull)
            .map(String::strip)
            .filter(name -> !name.isEmpty() && name.length() <= MAX_INGREDIENT_NAME_LENGTH)
            .distinct()
            .limit(MAX_INGREDIENTS)
            .toList();
    }
}
