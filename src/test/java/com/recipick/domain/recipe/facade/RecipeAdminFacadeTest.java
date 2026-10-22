package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.request.RecipeAdminSearchRequest;
import com.recipick.domain.recipe.dto.response.RecipeAdminSummaryResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.RecipeAdminService;
import com.recipick.global.response.PageResponse;
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
class RecipeAdminFacadeTest {

    @Mock
    private RecipeAdminService recipeAdminService;

    @Mock
    private ProductIngredientService productIngredientService;

    @InjectMocks
    private RecipeAdminFacade recipeAdminFacade;

    private Recipe recipe(Long id, boolean autoCartEnabled) {
        Recipe recipe = Recipe.builder()
            .name("된장찌개")
            .baseServings(2)
            .build();
        ReflectionTestUtils.setField(recipe, "id", id);
        ReflectionTestUtils.setField(recipe, "autoCartEnabled", autoCartEnabled);
        return recipe;
    }

    @Test
    @DisplayName("목록: 레시피마다 연결 상품 없는 필수 재료 수를 센다")
    void searchRecipes_missingLinkedRequiredCount() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        RecipeAdminSearchRequest request = new RecipeAdminSearchRequest(null, null, null, null, null);
        given(recipeAdminService.searchRecipes(request, pageable))
            .willReturn(new PageImpl<>(List.of(recipe(12L, true), recipe(18L, false)), pageable, 2));
        given(recipeAdminService.findRequiredIngredientIds(List.of(12L, 18L)))
            .willReturn(Map.of(12L, List.of(3L, 7L)));
        given(productIngredientService.countLinkedProducts(List.of(3L, 7L))).willReturn(Map.of(3L, 1L, 7L, 0L));

        // when
        PageResponse<RecipeAdminSummaryResponse> response = recipeAdminFacade.searchRecipes(request, pageable);

        // then
        assertThat(response.content())
            .extracting(RecipeAdminSummaryResponse::missingLinkedRequiredCount)
            .containsExactly(1, 0);
    }
}
