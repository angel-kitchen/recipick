package com.recipick.domain.recipe.facade;

import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.request.IngredientSaveRequest;
import com.recipick.domain.recipe.dto.response.IngredientAdminResponse;
import com.recipick.domain.recipe.dto.response.IngredientIdResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.service.IngredientService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IngredientAdminFacade {

    private final IngredientService ingredientService;
    private final ProductIngredientService productIngredientService;

    @Transactional(readOnly = true)
    public PageResponse<IngredientAdminResponse> findIngredients(String keyword, Pageable pageable) {
        Page<Ingredient> ingredients = ingredientService.findIngredients(keyword, pageable);
        List<Long> ingredientIds = ingredients.map(Ingredient::getId).getContent();
        Map<Long, Long> linkedCounts = productIngredientService.countLinkedProducts(ingredientIds);

        return PageResponse.from(ingredients.map(ingredient ->
                IngredientAdminResponse.of(ingredient, linkedCounts.getOrDefault(ingredient.getId(), 0L))));
    }

    @Transactional
    public IngredientIdResponse updateIngredient(Long ingredientId, IngredientSaveRequest request) {
        Ingredient ingredient = ingredientService.getIngredientForUpdate(ingredientId);
        if (ingredient.getUnit() != request.unit() && isInUse(ingredientId)) {
            throw new BusinessException(RecipeErrorCode.INGREDIENT_IN_USE);
        }
        return ingredientService.updateIngredient(ingredientId, request);
    }

    private boolean isInUse(Long ingredientId) {
        return ingredientService.isUsedInRecipe(ingredientId)
                || productIngredientService.countLinkedProducts(List.of(ingredientId))
                        .getOrDefault(ingredientId, 0L) > 0;
    }
}
