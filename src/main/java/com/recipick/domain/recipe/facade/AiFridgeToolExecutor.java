package com.recipick.domain.recipe.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.FridgeRecipe;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.IngredientRef;
import com.recipick.domain.recipe.dto.IngredientPrice;
import com.recipick.domain.recipe.entity.UnavailableReason;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.infra.ai.AiFridgeTools;
import com.recipick.infra.ai.dto.AiFridgeRecipeSearch;
import com.recipick.infra.ai.dto.AiIngredientPrice;
import com.recipick.infra.ai.dto.AiToolResult;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 냉장고 파먹기 한 요청 동안 AI가 부르는 Tool을 실행한다. 회원은 토큰에서 꺼낸 값으로 고정하고,
// AI가 지어낸 레시피·재료를 쓰지 못하도록 이 요청에서 레시피 찾기가 돌려준 것만 기억해 둔다
class AiFridgeToolExecutor implements AiFridgeTools {

    private static final int MAX_RECIPES = 3;

    private static final Comparator<ProductSummary> PRIORITY = Comparator
        .comparing((ProductSummary product) -> !isPurchasable(product))
        .thenComparingLong(ProductSummary::price)
        .thenComparing(ProductSummary::name);

    private final Long memberId;
    private final RecipeService recipeService;
    private final MemberService memberService;
    private final ProductIngredientService productIngredientService;
    private final Map<Long, FridgeRecipe> foundRecipes = new LinkedHashMap<>();
    private final Set<Long> foundIngredientIds = new HashSet<>();

    AiFridgeToolExecutor(Long memberId, RecipeService recipeService, MemberService memberService,
                         ProductIngredientService productIngredientService) {
        this.memberId = memberId;
        this.recipeService = recipeService;
        this.memberService = memberService;
        this.productIngredientService = productIngredientService;
    }

    @Override
    public AiToolResult<AiFridgeRecipeSearch> findRecipes(List<String> ingredientNames) {
        FridgeRecipeSearchResult result = recipeService.findRecipesByOwnedIngredients(ingredientNames);
        result.recipes().forEach(recipe -> {
            foundRecipes.put(recipe.recipeId(), recipe);
            recipe.missingIngredients().forEach(ingredient -> foundIngredientIds.add(ingredient.ingredientId()));
        });
        return AiToolResult.success(new AiFridgeRecipeSearch(
            result.unknownIngredientNames(),
            result.recipes().stream().map(AiFridgeToolExecutor::toAiRecipe).toList()));
    }

    @Override
    public AiToolResult<List<AiIngredientPrice>> findIngredientPrices(List<Long> ingredientIds) {
        List<Long> requested = ingredientIds.stream().distinct().filter(foundIngredientIds::contains).toList();
        if (requested.isEmpty()) {
            return AiToolResult.error("find_recipes_by_ingredients가 돌려준 부족한 재료 ID로 다시 호출하세요.");
        }
        Optional<Long> martId = memberService.getMartId(memberId);
        if (martId.isEmpty()) {
            return AiToolResult.error("이용 마트가 설정되지 않아 가격·재고를 조회할 수 없습니다. 이용 마트를 먼저 설정하도록 안내하세요.");
        }
        return AiToolResult.success(findPrices(martId.get(), requested).stream()
            .map(AiFridgeToolExecutor::toAiPrice)
            .toList());
    }

    // AI가 고른 ID 중 이 요청에서 레시피 찾기가 돌려준 레시피만 순서대로 남긴다
    List<FridgeRecipe> pick(List<Long> recipeIds) {
        return recipeIds.stream()
            .distinct()
            .map(foundRecipes::get)
            .filter(Objects::nonNull)
            .limit(MAX_RECIPES)
            .toList();
    }

    // 응답에는 AI가 답변에 쓴 숫자가 아니라 서버가 다시 조회한 가격·재고를 담는다
    Optional<Map<Long, IngredientPrice>> findPricesForResponse(List<FridgeRecipe> recipes) {
        List<Long> ingredientIds = recipes.stream()
            .flatMap(recipe -> recipe.missingIngredients().stream())
            .map(IngredientRef::ingredientId)
            .distinct()
            .toList();
        return memberService.getMartId(memberId).map(martId -> findPrices(martId, ingredientIds).stream()
            .collect(Collectors.toMap(IngredientPrice::ingredientId, price -> price)));
    }

    // 재료마다 지금 살 수 있는 상품을 먼저, 그중 가장 싼 상품을 고른다. 삭제된 상품은 다시 살 수 없어 뺀다
    private List<IngredientPrice> findPrices(Long martId, List<Long> ingredientIds) {
        Map<Long, List<ProductSummary>> candidates = productIngredientService.findCandidates(martId, ingredientIds);
        return ingredientIds.stream()
            .map(ingredientId -> candidates.getOrDefault(ingredientId, List.of()).stream()
                .filter(product -> !product.deleted())
                .min(PRIORITY)
                .map(product -> new IngredientPrice(ingredientId, product, unavailableReason(product)))
                .orElseGet(() -> new IngredientPrice(ingredientId, null, UnavailableReason.NOT_SOLD_IN_MART)))
            .toList();
    }

    private static boolean isPurchasable(ProductSummary product) {
        return unavailableReason(product) == null;
    }

    private static UnavailableReason unavailableReason(ProductSummary product) {
        if (product.status() != ProductStatus.SELLING) {
            return UnavailableReason.SALE_STOPPED;
        }
        return product.totalQuantity() == 0 ? UnavailableReason.SOLD_OUT : null;
    }

    private static AiFridgeRecipeSearch.Recipe toAiRecipe(FridgeRecipe recipe) {
        return new AiFridgeRecipeSearch.Recipe(recipe.recipeId(), recipe.name(),
            toAiIngredients(recipe.ownedIngredients()), toAiIngredients(recipe.missingIngredients()));
    }

    private static List<AiFridgeRecipeSearch.Ingredient> toAiIngredients(List<IngredientRef> ingredients) {
        return ingredients.stream()
            .map(ingredient -> new AiFridgeRecipeSearch.Ingredient(ingredient.ingredientId(), ingredient.name()))
            .toList();
    }

    private static AiIngredientPrice toAiPrice(IngredientPrice price) {
        ProductSummary product = price.product();
        String reason = price.unavailableReason() == null ? null : price.unavailableReason().name();
        if (product == null) {
            return new AiIngredientPrice(price.ingredientId(), null, null, null, reason);
        }
        return new AiIngredientPrice(price.ingredientId(), product.name(), product.price(),
            product.saleUnitAmount().stripTrailingZeros().toPlainString() + product.saleUnit().name(), reason);
    }
}
