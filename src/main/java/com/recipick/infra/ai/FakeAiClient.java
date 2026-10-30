package com.recipick.infra.ai;

import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "ai.client", havingValue = "fake", matchIfMissing = true)
public class FakeAiClient implements AiClient {

    static final String MODEL = "fake";
    static final String FAILURE_TRIGGER = "#AI장애";
    static final String RECOMMEND_FAILURE_TRIGGER = "#AI추천장애";

    private static final Pattern SERVINGS = Pattern.compile("(\\d+)\\s*인분");
    private static final Pattern COOKING_MINUTES = Pattern.compile("(\\d+)\\s*분");
    private static final int MAX_RECOMMENDATIONS = 5;

    @Override
    public AiResult<List<AiExtractedMenu>> extractMenus(String text, List<AiRecipeOption> recipes) {
        if (text.contains(FAILURE_TRIGGER)) {
            return AiResult.failure(AiFailureType.UNAVAILABLE, AiUsage.failed(MODEL, 0));
        }
        Integer servings = parseServings(text);
        List<AiExtractedMenu> menus = recipes.stream()
            .filter(recipe -> text.contains(recipe.name()))
            .map(recipe -> new AiExtractedMenu(recipe.name(), List.of(recipe.id()),
                servings))
            .toList();
        return AiResult.success(menus, new AiUsage(MODEL, 0, 0, 0, 0));
    }

    // 로컬 화면 확인용이라 한식·양식과 "N분"만 읽고, 나머지 조건은 비워 둔다
    @Override
    public AiResult<AiRecipeConditions> extractConditions(String text) {
        if (text.contains(FAILURE_TRIGGER)) {
            return AiResult.failure(AiFailureType.UNAVAILABLE, AiUsage.failed(MODEL, 0));
        }
        String cuisineType = text.contains("한식") ? "KOREAN" : text.contains("양식") ? "WESTERN" : null;
        Matcher matcher = COOKING_MINUTES.matcher(text);
        Integer maxCookingMinutes = matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
        AiRecipeConditions conditions =
            new AiRecipeConditions(cuisineType, null, maxCookingMinutes, null, List.of());
        return AiResult.success(conditions, new AiUsage(MODEL, 0, 0, 0, 0));
    }

    @Override
    public AiResult<List<AiRecommendedRecipe>> recommendRecipes(String text, List<AiRecipeOption> candidates) {
        if (text.contains(RECOMMEND_FAILURE_TRIGGER)) {
            return AiResult.failure(AiFailureType.UNAVAILABLE, AiUsage.failed(MODEL, 0));
        }
        List<AiRecommendedRecipe> recommendations = candidates.stream()
            .limit(MAX_RECOMMENDATIONS)
            .map(recipe -> new AiRecommendedRecipe(recipe.id(), recipe.name() + "을(를) 추천합니다."))
            .toList();
        return AiResult.success(recommendations, new AiUsage(MODEL, 0, 0, 0, 0));
    }

    private Integer parseServings(String text) {
        Matcher matcher = SERVINGS.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
