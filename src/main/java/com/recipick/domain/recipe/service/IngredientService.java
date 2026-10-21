package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.request.IngredientSaveRequest;
import com.recipick.domain.recipe.dto.response.IngredientIdResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    @Transactional
    public IngredientIdResponse createIngredient(IngredientSaveRequest request) {
        if (ingredientRepository.existsByName(request.name())) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_INGREDIENT_NAME);
        }
        Ingredient ingredient = Ingredient.builder()
            .name(request.name())
            .unit(request.unit())
            .build();
        return new IngredientIdResponse(ingredientRepository.save(ingredient).getId());
    }

    // 레시피 재료 등록 화면에서 이름으로 찾기 쉽도록 가나다순으로 고정한다
    public Page<Ingredient> findIngredients(String keyword, Pageable pageable) {
        Pageable byName = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name"));
        if (!StringUtils.hasText(keyword)) {
            return ingredientRepository.findAll(byName);
        }
        return ingredientRepository.findByNameContaining(keyword.trim(), byName);
    }

    public Ingredient getIngredient(Long ingredientId) {
        return ingredientRepository.findById(ingredientId)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.INGREDIENT_NOT_FOUND));
    }

    public boolean isUsedInRecipe(Long ingredientId) {
        return recipeIngredientRepository.existsByIngredientId(ingredientId);
    }

    @Transactional
    public IngredientIdResponse updateIngredient(Long ingredientId, IngredientSaveRequest request) {
        Ingredient ingredient = getIngredient(ingredientId);
        if (ingredientRepository.existsByNameAndIdNot(request.name(), ingredientId)) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_INGREDIENT_NAME);
        }
        ingredient.rename(request.name());
        ingredient.changeUnit(request.unit());
        return new IngredientIdResponse(ingredientId);
    }
}
