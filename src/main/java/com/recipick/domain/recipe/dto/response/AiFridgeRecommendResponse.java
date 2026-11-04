package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.FridgeRecipe;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.IngredientRef;
import com.recipick.domain.recipe.dto.IngredientPrice;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.UnavailableReason;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// answer는 AI가 쓴 안내 문장이고, 레시피·가격·재고는 서버가 조회한 값이다.
// NO_MATCH도 "모르는 재료예요"처럼 AI가 쓴 안내를 담고, FALLBACK은 AI 응답이 없어 answer가 null이다
public record AiFridgeRecommendResponse(
    AiResultStatus status,
    String answer,
    boolean martSelected,
    List<Recipe> recipes
) {

    public static AiFridgeRecommendResponse answered(String answer, List<FridgeRecipe> recipes,
                                                     Optional<Map<Long, IngredientPrice>> prices) {
        return new AiFridgeRecommendResponse(AiResultStatus.ANSWERED, answer, prices.isPresent(),
            recipes.stream().map(recipe -> Recipe.of(recipe, prices.orElse(Map.of()))).toList());
    }

    public static AiFridgeRecommendResponse noMatch(String answer, boolean martSelected) {
        return new AiFridgeRecommendResponse(AiResultStatus.NO_MATCH, answer, martSelected, List.of());
    }

    public static AiFridgeRecommendResponse fallback(boolean martSelected) {
        return new AiFridgeRecommendResponse(AiResultStatus.FALLBACK, null, martSelected, List.of());
    }

    public record Recipe(
        Long recipeId,
        String name,
        List<String> ownedIngredients,
        List<MissingIngredient> missingIngredients
    ) {

        static Recipe of(FridgeRecipe recipe, Map<Long, IngredientPrice> prices) {
            return new Recipe(
                recipe.recipeId(),
                recipe.name(),
                recipe.ownedIngredients().stream().map(IngredientRef::name).toList(),
                recipe.missingIngredients().stream()
                    .map(ingredient -> MissingIngredient.of(ingredient, prices.get(ingredient.ingredientId())))
                    .toList());
        }
    }

    // 이용 마트가 없으면 상품 필드와 unavailableReason이 모두 비어 있다
    public record MissingIngredient(
        Long ingredientId,
        String name,
        Long productId,
        String productName,
        Long price,
        UnavailableReason unavailableReason
    ) {

        static MissingIngredient of(IngredientRef ingredient, IngredientPrice price) {
            if (price == null) {
                return new MissingIngredient(ingredient.ingredientId(), ingredient.name(), null, null, null, null);
            }
            ProductSummary product = price.product();
            return new MissingIngredient(
                ingredient.ingredientId(),
                ingredient.name(),
                product == null ? null : product.productId(),
                product == null ? null : product.name(),
                product == null ? null : product.price(),
                price.unavailableReason());
        }
    }
}
