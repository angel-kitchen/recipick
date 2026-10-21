package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.recipe.dto.request.IngredientSaveRequest;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @InjectMocks
    private IngredientService ingredientService;

    private Ingredient ingredient(Long id, String name) {
        Ingredient ingredient = Ingredient.builder()
            .name(name)
            .unit(SaleUnit.G)
            .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    @Test
    @DisplayName("등록: 같은 이름의 재료가 있으면 DUPLICATE_INGREDIENT_NAME")
    void createIngredient_duplicated() {
        // given
        given(ingredientRepository.existsByName("두부")).willReturn(true);

        // when, then
        assertThatThrownBy(() -> ingredientService.createIngredient(new IngredientSaveRequest("두부", SaleUnit.G)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.DUPLICATE_INGREDIENT_NAME);
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("수정: 다른 재료와 이름이 겹치면 DUPLICATE_INGREDIENT_NAME")
    void updateIngredient_duplicated() {
        // given
        given(ingredientRepository.findById(3L)).willReturn(Optional.of(ingredient(3L, "두부")));
        given(ingredientRepository.existsByNameAndIdNot("애호박", 3L)).willReturn(true);

        // when, then
        assertThatThrownBy(() -> ingredientService.updateIngredient(3L, new IngredientSaveRequest("애호박", SaleUnit.G)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.DUPLICATE_INGREDIENT_NAME);
    }

    @Test
    @DisplayName("수정: 자기 이름 그대로면 중복이 아니고 이름·단위를 바꾼다")
    void updateIngredient() {
        // given
        Ingredient tofu = ingredient(3L, "두부");
        given(ingredientRepository.findById(3L)).willReturn(Optional.of(tofu));
        given(ingredientRepository.existsByNameAndIdNot("두부", 3L)).willReturn(false);

        // when
        ingredientService.updateIngredient(3L, new IngredientSaveRequest("두부", SaleUnit.EA));

        // then
        assertThat(tofu.getUnit()).isEqualTo(SaleUnit.EA);
    }

    @Test
    @DisplayName("조회: 없는 재료면 INGREDIENT_NOT_FOUND")
    void getIngredient_notFound() {
        // given
        given(ingredientRepository.findById(99L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> ingredientService.getIngredient(99L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INGREDIENT_NOT_FOUND);
    }
}
