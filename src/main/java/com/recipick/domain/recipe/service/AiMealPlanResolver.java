package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.infra.ai.dto.AiPlannedMeal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// AI가 짠 식단을 그대로 믿지 않고, 자동 장보기 가능 레시피 안에서 중복 없이 최대 5개만 남긴다
public final class AiMealPlanResolver {

    private static final int MAX_MEALS = 5;

    private AiMealPlanResolver() {
    }

    public static List<PlannedMeal> select(List<AiPlannedMeal> meals, List<Recipe> recipes) {
        Map<Long, Recipe> recipeById = recipes.stream()
            .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        return meals.stream()
            .filter(meal -> meal.recipeId() != null && seen.add(meal.recipeId()))
            .map(meal -> toPlannedMeal(meal, recipeById.get(meal.recipeId())))
            .filter(Objects::nonNull)
            .limit(MAX_MEALS)
            .toList();
    }

    // 인분을 말하지 않았거나 1~10 밖이면 미리보기에서 막히지 않도록 레시피 기본 인분을 쓴다
    private static PlannedMeal toPlannedMeal(AiPlannedMeal meal, Recipe recipe) {
        if (recipe == null) {
            return null;
        }
        Integer servings = meal.servings();
        boolean valid = servings != null && servings >= Recipe.MIN_SERVINGS && servings <= Recipe.MAX_SERVINGS;
        return new PlannedMeal(recipe, valid ? servings : recipe.getBaseServings(), meal.reason());
    }

    public record PlannedMeal(Recipe recipe, int servings, String reason) {
    }
}
