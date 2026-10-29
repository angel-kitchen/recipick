package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.request.RecipeSearchRequest;
import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeSummaryResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeBookmarkRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.domain.recipe.repository.RecipeSearchCondition;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeBookmarkRepository recipeBookmarkRepository;

    public PageResponse<RecipeSummaryResponse> searchRecipes(
            Long memberId,
            RecipeSearchRequest request,
            Pageable pageable
    ) {
        RecipeSearchCondition condition = new RecipeSearchCondition(
                request.keyword(),
                request.cuisineType(),
                request.dishType(),
                request.autoCartAvailable(),
                RecipeStatus.ACTIVE
        );
        Page<Recipe> recipes = recipeRepository.search(condition, pageable);
        Set<Long> bookmarkedRecipeIds = findBookmarkedRecipeIds(memberId, recipes.getContent());

        return PageResponse.from(recipes.map(recipe ->
            RecipeSummaryResponse.of(recipe, bookmarkedRecipeIds.contains(recipe.getId()))));
    }

    public RecipeDetailResponse getRecipe(Long memberId, Long recipeId, Integer servings) {
        Recipe recipe = getActiveRecipe(recipeId);
        int resolvedServings = recipe.resolveServings(servings);
        List<RecipeIngredient> recipeIngredients =
                recipeIngredientRepository.findAllWithIngredient(recipeId);
        boolean bookmarked = recipeBookmarkRepository.existsById(new RecipeBookmarkId(memberId, recipeId));

        return RecipeDetailResponse.of(recipe, resolvedServings, recipeIngredients, bookmarked);
    }

    private Recipe getActiveRecipe(Long recipeId) {
        return recipeRepository.findByIdAndStatus(recipeId, RecipeStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));
    }

    // 등록 레시피가 100개 남짓이라 활성 레시피를 한 번에 읽고 조건은 메모리에서 거른다. 순서는 ID순으로 고정한다
    public List<Recipe> findRecommendCandidates(RecipeRecommendCondition condition) {
        Set<Long> excludedRecipeIds = condition.excludedIngredients().stream()
            .flatMap(name -> recipeIngredientRepository.findRecipeIdsByIngredientNameContaining(name).stream())
            .collect(Collectors.toSet());
        return recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE).stream()
            .filter(condition::matches)
            .filter(recipe -> !excludedRecipeIds.contains(recipe.getId()))
            .toList();
    }

    private Set<Long> findBookmarkedRecipeIds(Long memberId, List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            return Set.of();
        }
        List<Long> recipeIds = recipes.stream()
            .map(Recipe::getId)
            .toList();
        return recipeBookmarkRepository.findBookmarkedRecipeIds(memberId, recipeIds);
    }
}
