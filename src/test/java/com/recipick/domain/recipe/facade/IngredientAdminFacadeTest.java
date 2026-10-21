package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.request.IngredientSaveRequest;
import com.recipick.domain.recipe.dto.response.IngredientAdminResponse;
import com.recipick.domain.recipe.dto.response.IngredientIdResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.service.IngredientService;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IngredientAdminFacadeTest {

    private static final Long INGREDIENT_ID = 3L;

    @Mock
    private IngredientService ingredientService;

    @Mock
    private ProductIngredientService productIngredientService;

    @InjectMocks
    private IngredientAdminFacade ingredientAdminFacade;

    private Ingredient ingredient(Long id, String name, SaleUnit unit) {
        Ingredient ingredient = Ingredient.builder()
            .name(name)
            .unit(unit)
            .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private Map<Long, Long> counts(Long ingredientId, long count) {
        Map<Long, Long> counts = new HashMap<>();
        counts.put(ingredientId, count);
        return counts;
    }

    @Test
    @DisplayName("목록: 재료마다 연결 상품 수를 붙인다")
    void findIngredients() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        List<Ingredient> ingredients = List.of(ingredient(3L, "두부", SaleUnit.G), ingredient(7L, "애호박", SaleUnit.EA));
        given(ingredientService.findIngredients("두", pageable)).willReturn(new PageImpl<>(ingredients, pageable, 2));
        given(productIngredientService.countLinkedProducts(List.of(3L, 7L))).willReturn(Map.of(3L, 2L, 7L, 0L));

        // when
        PageResponse<IngredientAdminResponse> response = ingredientAdminFacade.findIngredients("두", pageable);

        // then
        assertThat(response.content())
            .extracting(IngredientAdminResponse::linkedProductCount)
            .containsExactly(2L, 0L);
    }

    @Test
    @DisplayName("수정: 레시피에 쓰인 재료의 단위를 바꾸면 INGREDIENT_IN_USE")
    void updateIngredient_unitChangedUsedInRecipe() {
        // given
        given(ingredientService.getIngredient(INGREDIENT_ID)).willReturn(ingredient(INGREDIENT_ID, "두부", SaleUnit.G));
        given(ingredientService.isUsedInRecipe(INGREDIENT_ID)).willReturn(true);

        // when, then
        assertThatThrownBy(() -> ingredientAdminFacade.updateIngredient(
            INGREDIENT_ID, new IngredientSaveRequest("두부", SaleUnit.EA)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INGREDIENT_IN_USE);
        verify(ingredientService, never()).updateIngredient(any(), any());
    }

    @Test
    @DisplayName("수정: 상품에 연결된 재료의 단위를 바꾸면 INGREDIENT_IN_USE")
    void updateIngredient_unitChangedLinkedToProduct() {
        // given
        given(ingredientService.getIngredient(INGREDIENT_ID)).willReturn(ingredient(INGREDIENT_ID, "두부", SaleUnit.G));
        given(ingredientService.isUsedInRecipe(INGREDIENT_ID)).willReturn(false);
        given(productIngredientService.countLinkedProducts(List.of(INGREDIENT_ID)))
            .willReturn(counts(INGREDIENT_ID, 1L));

        // when, then
        assertThatThrownBy(() -> ingredientAdminFacade.updateIngredient(
            INGREDIENT_ID, new IngredientSaveRequest("두부", SaleUnit.EA)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INGREDIENT_IN_USE);
    }

    @Test
    @DisplayName("수정: 단위가 같으면 연결 여부를 보지 않고 이름만 바꾼다")
    void updateIngredient_sameUnit() {
        // given
        IngredientSaveRequest request = new IngredientSaveRequest("부침두부", SaleUnit.G);
        given(ingredientService.getIngredient(INGREDIENT_ID)).willReturn(ingredient(INGREDIENT_ID, "두부", SaleUnit.G));
        given(ingredientService.updateIngredient(INGREDIENT_ID, request))
            .willReturn(new IngredientIdResponse(INGREDIENT_ID));

        // when
        IngredientIdResponse response = ingredientAdminFacade.updateIngredient(INGREDIENT_ID, request);

        // then
        assertThat(response.ingredientId()).isEqualTo(INGREDIENT_ID);
        verify(ingredientService, never()).isUsedInRecipe(any());
    }
}
