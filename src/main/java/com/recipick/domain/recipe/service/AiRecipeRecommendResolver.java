package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// AI가 고른 추천을 그대로 믿지 않고, 서버가 거른 후보 안에 있는 것만 최대 5개 남긴다
public final class AiRecipeRecommendResolver {

    private static final int MAX_RECOMMENDATIONS = 5;

    private AiRecipeRecommendResolver() {
    }

    public static List<Recommendation> select(List<AiRecommendedRecipe> picks, List<Recipe> candidates) {
        Map<Long, Recipe> candidateById = candidates.stream()
            .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        return picks.stream()
            .filter(pick -> pick.recipeId() != null && seen.add(pick.recipeId()))
            .map(pick -> toRecommendation(pick, candidateById))
            .filter(Objects::nonNull)
            .limit(MAX_RECOMMENDATIONS)
            .toList();
    }

    // AI가 고르지 못했을 때는 서버가 거른 후보를 순서대로 보여 주고, AI가 쓴 이유가 없으므로 이유는 비운다
    public static List<Recommendation> fallback(List<Recipe> candidates) {
        return candidates.stream()
            .limit(MAX_RECOMMENDATIONS)
            .map(recipe -> new Recommendation(recipe, null))
            .toList();
    }

    private static Recommendation toRecommendation(AiRecommendedRecipe pick, Map<Long, Recipe> candidateById) {
        Recipe recipe = candidateById.get(pick.recipeId());
        return recipe == null ? null : new Recommendation(recipe, pick.reason());
    }

    public record Recommendation(Recipe recipe, String reason) {
    }
}
