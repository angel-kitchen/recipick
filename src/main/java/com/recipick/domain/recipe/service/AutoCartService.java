package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.AutoCartRequestRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoCartService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final AutoCartRequestRepository autoCartRequestRepository;

    public List<AutoCartMenu> resolveMenus(List<AutoCartPreviewRequest.Menu> menus) {
        List<Long> recipeIds = menus.stream()
            .map(AutoCartPreviewRequest.Menu::recipeId)
            .toList();
        if (recipeIds.size() != recipeIds.stream().distinct().count()) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_MENU_IN_REQUEST);
        }
        Map<Long, Recipe> recipes = recipeRepository.findAllById(recipeIds).stream()
            .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Map<Long, Set<Long>> optionalIds = recipeIngredientRepository.findAllWithIngredientByRecipeIds(recipeIds)
            .stream()
            .filter(recipeIngredient -> !recipeIngredient.isRequired())
            .collect(Collectors.groupingBy(
                recipeIngredient -> recipeIngredient.getRecipe().getId(),
                Collectors.mapping(recipeIngredient -> recipeIngredient.getIngredient().getId(), Collectors.toSet())
            ));

        return menus.stream()
            .map(menu -> toMenu(menu, recipes.get(menu.recipeId()),
                optionalIds.getOrDefault(menu.recipeId(), Set.of())))
            .toList();
    }

    public List<RequiredIngredient> sumIngredients(List<AutoCartMenu> menus) {
        Map<Long, AutoCartMenu> menuByRecipeId = menus.stream()
            .collect(Collectors.toMap(AutoCartMenu::recipeId, Function.identity()));
        Map<Long, RequiredIngredient> totals = new LinkedHashMap<>();
        for (RecipeIngredient recipeIngredient : recipeIngredientRepository.findAllWithIngredientByRecipeIds(
            menuByRecipeId.keySet())) {
            AutoCartMenu menu = menuByRecipeId.get(recipeIngredient.getRecipe().getId());
            Long ingredientId = recipeIngredient.getIngredient().getId();
            if (!recipeIngredient.isRequired() && !menu.optionalIngredientIds().contains(ingredientId)) {
                continue;
            }
            totals.merge(ingredientId, new RequiredIngredient(
                ingredientId,
                recipeIngredient.getIngredient().getName(),
                recipeIngredient.getUnit(),
                recipeIngredient.calculateRequiredAmount(menu.servings())
            ), RequiredIngredient::add);
        }
        return List.copyOf(totals.values());
    }

    @Transactional
    public AutoCartRequest savePreview(Long memberId, Long martId, List<AutoCartMenu> menus, long totalPrice) {
        return autoCartRequestRepository.save(AutoCartRequest.builder()
            .memberId(memberId)
            .martId(martId)
            .menus(menus)
            .totalPrice(totalPrice)
            .build());
    }

    public AutoCartRequest getRequest(Long memberId, Long autoCartRequestId) {
        return autoCartRequestRepository.findByIdAndMemberId(autoCartRequestId, memberId)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.AUTO_CART_REQUEST_NOT_FOUND));
    }

    private AutoCartMenu toMenu(AutoCartPreviewRequest.Menu menu, Recipe recipe, Set<Long> optionalIds) {
        if (recipe == null) {
            throw new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND);
        }
        if (!recipe.isActive() || !recipe.isAutoCartEnabled()) {
            throw new BusinessException(RecipeErrorCode.RECIPE_NOT_AUTO_CART_ENABLED);
        }
        int servings = recipe.resolveServings(menu.servings());
        List<Long> selectedOptionalIds = menu.optionalIngredientIds() == null
            ? List.of()
            : menu.optionalIngredientIds().stream().distinct().toList();
        if (!optionalIds.containsAll(selectedOptionalIds)) {
            throw new BusinessException(RecipeErrorCode.OPTIONAL_INGREDIENT_NOT_IN_RECIPE);
        }
        return new AutoCartMenu(recipe.getId(), recipe.getName(), servings, selectedOptionalIds);
    }
}
