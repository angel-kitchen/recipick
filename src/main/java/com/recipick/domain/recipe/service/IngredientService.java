package com.recipick.domain.recipe.service;

import java.util.Objects;
import java.util.Map;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditField;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.domain.recipe.dto.request.IngredientSaveRequest;
import com.recipick.domain.recipe.dto.response.IngredientIdResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    private final AdminAuditRecorder auditRecorder;

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
        Ingredient saved;
        try {
            // 같은 이름 등록의 UNIQUE 오류만 중복 이름으로 변환한다.
            saved = ingredientRepository.saveAndFlush(ingredient);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_INGREDIENT_NAME, e);
        }
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.INGREDIENT_CREATE, saved.getId(), null,
                Map.of(AdminAuditField.SALE_UNIT, new AdminAuditChange(null, ingredient.getUnit()))));
        return new IngredientIdResponse(saved.getId());
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
        boolean changed = !Objects.equals(ingredient.getName(), request.name())
                || ingredient.getUnit() != request.unit();
        var beforeUnit = ingredient.getUnit();
        ingredient.rename(request.name());
        ingredient.changeUnit(request.unit());
        try {
            // 변경 감지 UPDATE는 커밋 때 나가므로 여기서 flush해야 UNIQUE 위반을 이 메서드 안에서 잡을 수 있다
            ingredientRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_INGREDIENT_NAME, e);
        }
        if (changed) {
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.INGREDIENT_UPDATE, ingredientId, null, Map.of(AdminAuditField.SALE_UNIT, new AdminAuditChange(beforeUnit, ingredient.getUnit()))));
        }
        return new IngredientIdResponse(ingredientId);
    }
}
