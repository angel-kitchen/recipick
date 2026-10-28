package com.recipick.domain.recipe.facade;

import java.util.Comparator;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditField;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.request.RecipeAdminSearchRequest;
import com.recipick.domain.recipe.dto.request.RecipeAutoCartUpdateRequest;
import com.recipick.domain.recipe.dto.request.RecipeIngredientsSaveRequest;
import com.recipick.domain.recipe.dto.response.RecipeAdminDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeAdminSummaryResponse;
import com.recipick.domain.recipe.dto.response.RecipeAutoCartResponse;
import com.recipick.domain.recipe.dto.response.RecipeIngredientsSaveResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.service.RecipeAdminService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RecipeAdminFacade {

    private final AdminAuditRecorder auditRecorder;

    private final RecipeAdminService recipeAdminService;
    private final ProductIngredientService productIngredientService;

    @Transactional(readOnly = true)
    public PageResponse<RecipeAdminSummaryResponse> searchRecipes(RecipeAdminSearchRequest request, Pageable pageable) {
        Page<Recipe> recipes = recipeAdminService.searchRecipes(request, pageable);
        Map<Long, List<Long>> requiredIds =
            recipeAdminService.findRequiredIngredientIds(recipes.map(Recipe::getId).getContent());
        Map<Long, Long> linkedCounts =
            productIngredientService.countLinkedProducts(flatten(requiredIds.values()));

        return PageResponse.from(recipes.map(recipe -> RecipeAdminSummaryResponse.of(
            recipe, countMissingLinked(requiredIds.getOrDefault(recipe.getId(),
                List.of()), linkedCounts))));
    }

    @Transactional(readOnly = true)
    public RecipeAdminDetailResponse getRecipe(Long recipeId) {
        Recipe recipe = recipeAdminService.getRecipe(recipeId);
        List<RecipeIngredient> recipeIngredients =
            recipeAdminService.findRecipeIngredients(recipeId);
        List<Long> ingredientIds = recipeIngredients.stream()
            .map(recipeIngredient -> recipeIngredient.getIngredient().getId())
            .toList();

        return RecipeAdminDetailResponse.of(
                recipe,
                recipeIngredients,
                productIngredientService.countLinkedProducts(ingredientIds)
        );
    }

    @Transactional
    public RecipeIngredientsSaveResponse saveIngredients(Long recipeId, RecipeIngredientsSaveRequest request) {
        Recipe recipe = recipeAdminService.getRecipeForUpdate(recipeId);
        var before = recipeAdminService.findRecipeIngredients(recipeId).stream()
                .map(item -> new RecipeIngredientsSaveRequest.Item(item.getIngredient().getId(),
                        item.getAmountPerServing(), item.isRequired())).toList();
        boolean beforeAuto = recipe.isAutoCartEnabled();
        int ingredientCount = recipeAdminService.replaceIngredients(recipeId, request);
        boolean released = recipe.isAutoCartEnabled() && !hasLinkedProductsForAllRequired(recipeId);
        if (released) {
            recipe.disableAutoCart();
        }
        if (!ingredientState(before).equals(ingredientState(request.ingredients()))
                || beforeAuto != recipe.isAutoCartEnabled()) {
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.RECIPE_INGREDIENTS_CHANGE, recipeId, null, Map.of(AdminAuditField.INGREDIENT_COUNT, new AdminAuditChange(before.size(), ingredientCount), AdminAuditField.AUTO_CART_AVAILABLE, new AdminAuditChange(beforeAuto, recipe.isAutoCartEnabled()))));
        }
        return new RecipeIngredientsSaveResponse(recipeId, ingredientCount, recipe.isAutoCartEnabled(), released);
    }

    @Transactional
    public RecipeAutoCartResponse changeAutoCart(Long recipeId, RecipeAutoCartUpdateRequest request) {
        // 재료 저장과 같은 레시피 행 잠금을 먼저 잡아야, 재료가 바뀌는 중에 이전 재료 기준으로 지정되지 않는다
        Recipe recipe = recipeAdminService.getRecipeForUpdate(recipeId);
        boolean beforeAuto = recipe.isAutoCartEnabled();
        if (request.autoCartAvailable()) {
            if (!hasLinkedProductsForAllRequired(recipeId)) {
                throw new BusinessException(RecipeErrorCode.AUTO_CART_ENABLE_CONDITION_NOT_MET);
            }
            recipe.enableAutoCart();
        } else {
            recipe.disableAutoCart();
        }
        if (beforeAuto != recipe.isAutoCartEnabled()) {
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.RECIPE_AUTO_CART_CHANGE, recipeId, null, Map.of(AdminAuditField.AUTO_CART_AVAILABLE, new AdminAuditChange(beforeAuto, recipe.isAutoCartEnabled()))));
        }
        return new RecipeAutoCartResponse(recipeId, recipe.isAutoCartEnabled());
    }

    private int countMissingLinked(List<Long> requiredIngredientIds, Map<Long, Long> linkedCounts
    ) {
        return (int) requiredIngredientIds.stream()
            .filter(ingredientId -> linkedCounts.get(ingredientId) == 0)
            .count();
    }

    private List<Long> flatten(Collection<List<Long>> ids) {
        return ids.stream()
            .flatMap(List::stream)
            .distinct()
            .toList();
    }

    private boolean hasLinkedProductsForAllRequired(Long recipeId) {
        List<Long> requiredIds = recipeAdminService.findRequiredIngredientIds(List.of(recipeId))
            .getOrDefault(recipeId, List.of());
        if (requiredIds.isEmpty()) {
            return false;
        }
        return countMissingLinked(requiredIds, productIngredientService.countLinkedProducts(requiredIds)) == 0;
    }
    private List<List<Object>> ingredientState(List<RecipeIngredientsSaveRequest.Item> items) {
        return items.stream().sorted(Comparator.comparing(RecipeIngredientsSaveRequest.Item::ingredientId))
                .map(item -> List.<Object>of(item.ingredientId(),
                        item.amountPerServing().stripTrailingZeros(), item.required())).toList();
    }

}
