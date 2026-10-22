package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.request.RecipeAdminSearchRequest;
import com.recipick.domain.recipe.dto.request.RecipeSaveRequest;
import com.recipick.domain.recipe.dto.request.RecipeStatusUpdateRequest;
import com.recipick.domain.recipe.dto.request.RecipeStepRequest;
import com.recipick.domain.recipe.dto.response.RecipeIdResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.RecipeStep;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.domain.recipe.repository.RecipeSearchCondition;
import com.recipick.domain.recipe.repository.RequiredIngredientRef;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeAdminService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    @Transactional
    public RecipeIdResponse createRecipe(RecipeSaveRequest request) {
        Recipe recipe = Recipe.builder()
            .name(request.name())
            .cuisine(request.cuisineType())
            .dishType(request.dishType())
            .baseServings(request.baseServings())
            .cookTimeMin(request.cookingTimeMinutes())
            .difficulty(request.difficulty())
            .spicyLevel(request.spicyLevel())
            .steps(toSteps(request.steps()))
            .youtubeVideoId(YoutubeUrlParser.extractVideoId(request.youtubeUrl()))
            .build();
        return new RecipeIdResponse(recipeRepository.save(recipe).getId());
    }

    @Transactional
    public RecipeIdResponse updateRecipe(Long recipeId, RecipeSaveRequest request) {
        Recipe recipe = getRecipeForUpdate(recipeId);
        recipe.update(
                request.name(),
                request.cuisineType(),
                request.dishType(),
                request.baseServings(),
                request.cookingTimeMinutes(),
                request.difficulty(),
                request.spicyLevel(),
                toSteps(request.steps()),
                YoutubeUrlParser.extractVideoId(request.youtubeUrl())
        );
        return new RecipeIdResponse(recipeId);
    }

    @Transactional
    public RecipeIdResponse changeStatus(Long recipeId, RecipeStatusUpdateRequest request) {
        getRecipeForUpdate(recipeId).changeStatus(request.status());
        return new RecipeIdResponse(recipeId);
    }

    private Recipe getRecipeForUpdate(Long recipeId) {
        return recipeRepository.findByIdForUpdate(recipeId)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));
    }

    public Page<Recipe> searchRecipes(RecipeAdminSearchRequest request, Pageable pageable) {
        RecipeSearchCondition condition = new RecipeSearchCondition(
                request.keyword(),
                request.cuisineType(),
                request.dishType(),
                request.autoCartAvailable(),
                request.status()
        );
        return recipeRepository.search(condition, pageable);
    }

    public Recipe getRecipe(Long recipeId) {
        return recipeRepository.findById(recipeId)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));
    }

    public List<RecipeIngredient> findRecipeIngredients(Long recipeId) {
        return recipeIngredientRepository.findAllWithIngredient(recipeId);
    }

    public Map<Long, List<Long>> findRequiredIngredientIds(Collection<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }
        return recipeIngredientRepository.findRequiredIngredientRefs(recipeIds).stream()
            .collect(Collectors.groupingBy(
                    RequiredIngredientRef::getRecipeId,
                    Collectors.mapping(RequiredIngredientRef::getIngredientId, Collectors.toList())
            ));
    }

    private List<RecipeStep> toSteps(List<RecipeStepRequest> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).order() != i + 1) {
                throw new BusinessException(RecipeErrorCode.INVALID_RECIPE_STEPS);
            }
        }
        return steps.stream()
            .map(RecipeStepRequest::toStep)
            .toList();
    }
}
