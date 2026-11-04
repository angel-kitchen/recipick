package com.recipick.domain.recipe.dto;

import java.util.List;

// 냉장고 파먹기 레시피 찾기 Tool 결과. 부족한 재료 ID를 담아 AI가 가격·재고 조회 Tool에 이름 대신 ID로 넘기게 한다
public record FridgeRecipeSearchResult(
    List<String> unknownIngredientNames,
    List<FridgeRecipe> recipes
) {

    public record FridgeRecipe(
        Long recipeId,
        String name,
        List<IngredientRef> ownedIngredients,
        List<IngredientRef> missingIngredients
    ) {
    }

    public record IngredientRef(
        Long ingredientId,
        String name
    ) {
    }
}
