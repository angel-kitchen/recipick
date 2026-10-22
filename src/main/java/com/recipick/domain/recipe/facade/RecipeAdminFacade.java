package com.recipick.domain.recipe.facade;

import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.request.RecipeAdminSearchRequest;
import com.recipick.domain.recipe.dto.response.RecipeAdminDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeAdminSummaryResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.service.RecipeAdminService;
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
}
