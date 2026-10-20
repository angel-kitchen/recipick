package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.recipe.dto.request.RecipeSearchRequest;
import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeSummaryResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.entity.RecipeStep;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeBookmarkRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.groups.Tuple;
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
class RecipeServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @Mock
    private RecipeBookmarkRepository recipeBookmarkRepository;

    @InjectMocks
    private RecipeService recipeService;

    private Recipe recipe(Long id, String name) {
        Recipe recipe = Recipe.builder()
            .name(name)
            .baseServings(2)
            .steps(List.of(new RecipeStep(1, "멸치 육수를 냅니다.")))
            .build();
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }

    private RecipeIngredient recipeIngredient(Recipe recipe, String amountPerServing) {
        Ingredient tofu = Ingredient.builder()
            .name("두부")
            .unit(SaleUnit.G)
            .build();
        return RecipeIngredient.builder()
            .recipe(recipe)
            .ingredient(tofu)
            .amountPerServing(new BigDecimal(amountPerServing))
            .required(true)
            .build();
    }

    @Test
    @DisplayName("검색: 찜한 레시피만 bookmarked가 true다")
    void searchRecipes_bookmarked() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        List<Recipe> recipes = List.of(recipe(12L, "된장찌개"), recipe(18L, "두부조림"));
        given(recipeRepository.search(any(), any())).willReturn(new PageImpl<>(recipes, pageable, 2));
        given(recipeBookmarkRepository.findBookmarkedRecipeIds(MEMBER_ID, List.of(12L, 18L)))
            .willReturn(Set.of(12L));

        // when
        PageResponse<RecipeSummaryResponse> response =
            recipeService.searchRecipes(MEMBER_ID, new RecipeSearchRequest(null, null, null, null), pageable);

        // then
        assertThat(response.content())
            .extracting(RecipeSummaryResponse::recipeId, RecipeSummaryResponse::bookmarked)
            .containsExactly(
                Tuple.tuple(12L, true),
                Tuple.tuple(18L, false)
            );
    }

    @Test
    @DisplayName("검색: 결과가 없으면 찜 여부를 조회하지 않는다")
    void searchRecipes_empty() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        given(recipeRepository.search(any(), any())).willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        PageResponse<RecipeSummaryResponse> response =
            recipeService.searchRecipes(MEMBER_ID, new RecipeSearchRequest("없는메뉴", null, null, null), pageable);

        // then
        assertThat(response.content()).isEmpty();
        verify(recipeBookmarkRepository, never()).findBookmarkedRecipeIds(any(), any());
    }

    @Test
    @DisplayName("상세: 선택한 인분으로 재료 필요량을 환산한다")
    void getRecipe_scaled() {
        // given
        Recipe recipe = recipe(12L, "된장찌개");
        given(recipeRepository.findByIdAndStatus(12L, RecipeStatus.ACTIVE)).willReturn(Optional.of(recipe));
        given(recipeIngredientRepository.findAllWithIngredient(12L))
            .willReturn(List.of(recipeIngredient(recipe, "100.00")));
        given(recipeBookmarkRepository.existsById(new RecipeBookmarkId(MEMBER_ID, 12L))).willReturn(true);

        // when
        RecipeDetailResponse response = recipeService.getRecipe(MEMBER_ID, 12L, 3);

        // then
        assertThat(response.servings()).isEqualTo(3);
        assertThat(response.bookmarked()).isTrue();
        assertThat(response.ingredients().get(0).requiredAmount().toPlainString()).isEqualTo("300");
    }

    @Test
    @DisplayName("상세: 인분을 생략하면 기본 인분으로 환산한다")
    void getRecipe_defaultServings() {
        // given
        Recipe recipe = recipe(12L, "된장찌개");
        given(recipeRepository.findByIdAndStatus(12L, RecipeStatus.ACTIVE)).willReturn(Optional.of(recipe));
        given(recipeIngredientRepository.findAllWithIngredient(12L))
            .willReturn(List.of(recipeIngredient(recipe, "0.50")));

        // when
        RecipeDetailResponse response = recipeService.getRecipe(MEMBER_ID, 12L, null);

        // then
        assertThat(response.servings()).isEqualTo(2);
        assertThat(response.ingredients().get(0).requiredAmount().toPlainString()).isEqualTo("1");
    }

    @Test
    @DisplayName("상세: 없거나 비활성인 레시피면 RECIPE_NOT_FOUND")
    void getRecipe_notFound() {
        // given
        given(recipeRepository.findByIdAndStatus(99L, RecipeStatus.ACTIVE)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recipeService.getRecipe(MEMBER_ID, 99L, null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.RECIPE_NOT_FOUND);
    }

    @Test
    @DisplayName("상세: 인분이 범위를 벗어나면 INVALID_SERVINGS")
    void getRecipe_invalidServings() {
        // given
        given(recipeRepository.findByIdAndStatus(12L, RecipeStatus.ACTIVE))
            .willReturn(Optional.of(recipe(12L, "된장찌개")));

        // when, then
        assertThatThrownBy(() -> recipeService.getRecipe(MEMBER_ID, 12L, 11))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INVALID_SERVINGS);
    }
}
