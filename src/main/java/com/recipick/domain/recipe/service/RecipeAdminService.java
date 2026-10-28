package com.recipick.domain.recipe.service;

import java.util.Arrays;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditField;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.domain.recipe.dto.request.RecipeAdminSearchRequest;
import com.recipick.domain.recipe.dto.request.RecipeIngredientsSaveRequest;
import com.recipick.domain.recipe.dto.request.RecipeSaveRequest;
import com.recipick.domain.recipe.dto.request.RecipeStatusUpdateRequest;
import com.recipick.domain.recipe.dto.request.RecipeStepRequest;
import com.recipick.domain.recipe.dto.response.RecipeIdResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.RecipeStep;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.IngredientRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeAdminService {

    private final AdminAuditRecorder auditRecorder;

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final IngredientRepository ingredientRepository;

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
        Recipe saved = recipeRepository.save(recipe);
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.RECIPE_CREATE, saved.getId(), null, Map.of()));
        return new RecipeIdResponse(saved.getId());
    }

    @Transactional
    public RecipeIdResponse updateRecipe(Long recipeId, RecipeSaveRequest request) {
        Recipe recipe = getRecipeForUpdate(recipeId);
        var before = auditValues(recipe);
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
        if (!before.equals(auditValues(recipe))) {
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.RECIPE_UPDATE, recipeId, null, Map.of()));
        }
        return new RecipeIdResponse(recipeId);
    }

    @Transactional
    public RecipeIdResponse changeStatus(Long recipeId, RecipeStatusUpdateRequest request) {
        Recipe recipe = getRecipeForUpdate(recipeId);
        var beforeStatus = recipe.getStatus();
        boolean beforeAuto = recipe.isAutoCartEnabled();
        recipe.changeStatus(request.status());
        if (beforeStatus != recipe.getStatus() || beforeAuto != recipe.isAutoCartEnabled()) {
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.RECIPE_STATUS_CHANGE, recipeId, null, Map.of(AdminAuditField.STATUS, new AdminAuditChange(beforeStatus, recipe.getStatus()), AdminAuditField.AUTO_CART_AVAILABLE, new AdminAuditChange(beforeAuto, recipe.isAutoCartEnabled()))));
        }
        return new RecipeIdResponse(recipeId);
    }

    // 클래스 기본값이 readOnly라, 잠금 조회(SELECT ... FOR UPDATE)는 쓰기 트랜잭션으로 명시한다
    @Transactional
    public Recipe getRecipeForUpdate(Long recipeId) {
        return recipeRepository.findByIdForUpdate(recipeId)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));
    }

    @Transactional
    public int replaceIngredients(Long recipeId, RecipeIngredientsSaveRequest request) {
        // 같은 레시피의 재료를 동시에 교체하면 삭제·삽입이 서로의 행을 잠가 교착이 나므로 레시피 행부터 잠가 차례로 처리한다
        Recipe recipe = getRecipeForUpdate(recipeId);
        List<Long> ingredientIds = request.ingredients().stream()
            .map(RecipeIngredientsSaveRequest.Item::ingredientId)
            .toList();
        if (ingredientIds.size() != ingredientIds.stream().distinct().count()) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_RECIPE_INGREDIENT);
        }
        Map<Long, Ingredient> ingredients =
            ingredientRepository.findAllById(ingredientIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
        if (ingredients.size() != ingredientIds.size()) {
            throw new BusinessException(RecipeErrorCode.INGREDIENT_NOT_FOUND);
        }

        recipeIngredientRepository.deleteByRecipeId(recipeId);
        recipeIngredientRepository.saveAll(request.ingredients().stream()
            .map(item -> RecipeIngredient.builder()
                .recipe(recipe)
                .ingredient(ingredients.get(item.ingredientId()))
                .amountPerServing(item.amountPerServing())
                .required(item.required())
                .build())
            .toList());
        return ingredients.size();
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
    private List<Object> auditValues(Recipe recipe) {
        return Arrays.asList(recipe.getName(), recipe.getCuisine(), recipe.getDishType(),
                recipe.getBaseServings(), recipe.getCookTimeMin(), recipe.getDifficulty(), recipe.getSpicyLevel(),
                recipe.getSteps(), recipe.getYoutubeVideoId());
    }

}
