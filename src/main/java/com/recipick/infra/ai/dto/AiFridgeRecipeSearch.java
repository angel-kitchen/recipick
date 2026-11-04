package com.recipick.infra.ai.dto;

import java.util.List;

// 레시피 찾기 Tool 결과. 부족한 재료 ID를 담아 AI가 가격·재고 조회 Tool에 이름 대신 ID로 넘기게 한다
public record AiFridgeRecipeSearch(
    List<String> unknownIngredientNames,
    List<Recipe> recipes
) {

    public record Recipe(
        Long recipeId,
        String name,
        List<Ingredient> ownedIngredients,
        List<Ingredient> missingIngredients
    ) {
    }

    public record Ingredient(
        Long ingredientId,
        String name
    ) {
    }
}
