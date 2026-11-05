package com.recipick.infra.ai;

import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiFridgeAnswer;
import com.recipick.infra.ai.dto.AiFridgeRecipeSearch;
import com.recipick.infra.ai.dto.AiPlannedMeal;
import com.recipick.infra.ai.dto.AiRecipeCandidate;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiToolResult;
import com.recipick.infra.ai.dto.AiUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 정해진 규칙으로만 답하므로 지시문(instruction)은 쓰지 않는다
@Component
@ConditionalOnProperty(name = "ai.client", havingValue = "fake", matchIfMissing = true)
public class FakeAiClient implements AiClient {

    static final String MODEL = "fake";
    static final String FAILURE_TRIGGER = "#AI장애";
    static final String RECOMMEND_FAILURE_TRIGGER = "#AI추천장애";

    private static final Pattern SERVINGS = Pattern.compile("(\\d+)\\s*인분");
    private static final Pattern COOKING_MINUTES = Pattern.compile("(\\d+)\\s*분");
    private static final Pattern BUDGET_MAN_WON = Pattern.compile("(\\d+)\\s*만\\s*원");
    private static final Pattern BUDGET_WON = Pattern.compile("(\\d{4,})\\s*원");
    private static final int MAN = 10_000;
    private static final int MAX_RECOMMENDATIONS = 5;
    private static final int MAX_FRIDGE_RECIPES = 3;
    private static final Pattern MEAL_COUNT = Pattern.compile("(\\d+)\\s*(일|끼|개|가지)");
    private static final int DEFAULT_MEALS = 3;
    private static final int MAX_MEALS = 5;

    @Override
    public AiResult<List<AiExtractedMenu>> extractMenus(String instruction, String text, List<AiRecipeOption> recipes) {
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

    // 로컬 화면 확인용이라 한식·양식, "N분", "N만 원·N원", "N인분"만 읽고, 나머지 조건은 비워 둔다
    @Override
    public AiResult<AiRecipeConditions> extractConditions(String instruction, String text) {
        if (text.contains(FAILURE_TRIGGER)) {
            return AiResult.failure(AiFailureType.UNAVAILABLE, AiUsage.failed(MODEL, 0));
        }
        String cuisineType = text.contains("한식") ? "KOREAN" : text.contains("양식") ? "WESTERN" : null;
        Matcher matcher = COOKING_MINUTES.matcher(text);
        Integer maxCookingMinutes = matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
        AiRecipeConditions conditions =
            new AiRecipeConditions(cuisineType, null, maxCookingMinutes, null, List.of(), List.of(), parseBudget(text),
                parseServings(text));
        return AiResult.success(conditions, new AiUsage(MODEL, 0, 0, 0, 0));
    }

    @Override
    public AiResult<List<AiRecommendedRecipe>> recommendRecipes(String instruction, String text,
                                                                List<AiRecipeCandidate> candidates) {
        if (text.contains(RECOMMEND_FAILURE_TRIGGER)) {
            return AiResult.failure(AiFailureType.UNAVAILABLE, AiUsage.failed(MODEL, 0));
        }
        List<AiRecommendedRecipe> recommendations = candidates.stream()
            .limit(MAX_RECOMMENDATIONS)
            .map(recipe -> new AiRecommendedRecipe(recipe.id(), recipe.name() + "을(를) 추천합니다."))
            .toList();
        return AiResult.success(recommendations, new AiUsage(MODEL, 0, 0, 0, 0));
    }

    // 로컬 화면 확인용이라 문장에 들어 있는 등록 재료 이름으로만 찾고, 가장 덜 부족한 레시피부터 고른다
    @Override
    public AiResult<AiFridgeAnswer> answerFridgeQuestion(String instruction, String text, List<String> ingredientNames,
                                                         AiFridgeTools tools) {
        if (text.contains(FAILURE_TRIGGER)) {
            return AiResult.failure(AiFailureType.UNAVAILABLE, AiUsage.failed(MODEL, 0));
        }
        List<String> owned = ingredientNames.stream().filter(text::contains).toList();
        AiToolResult<AiFridgeRecipeSearch> search = tools.findRecipes(owned);
        List<Long> recipeIds = search.isError() ? List.of() : search.content().recipes().stream()
            .limit(MAX_FRIDGE_RECIPES)
            .map(AiFridgeRecipeSearch.Recipe::recipeId)
            .toList();
        String answer = recipeIds.isEmpty()
            ? "가진 재료로 만들 수 있는 레시피를 찾지 못했어요."
            : "가진 재료로 만들 수 있는 레시피를 골랐어요.";
        return AiResult.success(new AiFridgeAnswer(answer, recipeIds), new AiUsage(MODEL, 0, 0, 0, 0));
    }

    private Integer parseBudget(String text) {
        Matcher manWon = BUDGET_MAN_WON.matcher(text);
        if (manWon.find()) {
            return Integer.parseInt(manWon.group(1)) * MAN;
        }
        Matcher won = BUDGET_WON.matcher(text);
        return won.find() ? Integer.valueOf(won.group(1)) : null;
    }

    private Integer parseServings(String text) {
        Matcher matcher = SERVINGS.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    // 로컬 화면 확인용이라 "N일·N끼"로 개수(기본 3, 최대 5)와 "N인분"만 읽고, 목록 앞에서부터 고른다
    @Override
    public AiResult<List<AiPlannedMeal>> planMeals(String instruction, String text,
                                                   List<AiRecipeCandidate> candidates) {
        if (text.contains(FAILURE_TRIGGER)) {
            return AiResult.failure(AiFailureType.UNAVAILABLE, AiUsage.failed(MODEL, 0));
        }
        Matcher matcher = MEAL_COUNT.matcher(text);
        int count = matcher.find() ? Math.min(Integer.parseInt(matcher.group(1)), MAX_MEALS) : DEFAULT_MEALS;
        Integer servings = parseServings(text);
        List<AiPlannedMeal> meals = candidates.stream()
            .limit(count)
            .map(recipe -> new AiPlannedMeal(recipe.id(), servings, recipe.name() + "을(를) 식단에 넣었습니다."))
            .toList();
        return AiResult.success(meals, new AiUsage(MODEL, 0, 0, 0, 0));
    }
}
