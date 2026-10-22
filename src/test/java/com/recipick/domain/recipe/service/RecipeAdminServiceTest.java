package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.recipe.dto.request.RecipeIngredientsSaveRequest;
import com.recipick.domain.recipe.dto.request.RecipeSaveRequest;
import com.recipick.domain.recipe.dto.request.RecipeStatusUpdateRequest;
import com.recipick.domain.recipe.dto.request.RecipeStepRequest;
import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Difficulty;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipeAdminServiceTest {

    private static final Long RECIPE_ID = 12L;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private RecipeAdminService recipeAdminService;

    private RecipeSaveRequest saveRequest(List<RecipeStepRequest> steps, String youtubeUrl) {
        return new RecipeSaveRequest("된장찌개", CuisineType.KOREAN, DishType.STEW, 2, 25,
            Difficulty.EASY, 1, youtubeUrl, steps);
    }

    private Recipe recipe() {
        Recipe recipe = Recipe.builder()
            .name("된장찌개")
            .baseServings(2)
            .build();
        ReflectionTestUtils.setField(recipe, "id", RECIPE_ID);
        return recipe;
    }

    private Ingredient ingredient(Long id) {
        Ingredient ingredient = Ingredient.builder()
            .name("재료" + id)
            .unit(SaleUnit.G)
            .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private RecipeIngredientsSaveRequest.Item item(Long ingredientId) {
        return new RecipeIngredientsSaveRequest.Item(ingredientId, new BigDecimal("100"), true);
    }

    @Test
    @DisplayName("등록: 유튜브 주소에서 영상 ID만 저장하고 활성·자동 장보기 해제로 시작한다")
    void createRecipe() {
        // given
        RecipeSaveRequest request = saveRequest(
            List.of(new RecipeStepRequest(1, "육수를 냅니다."), new RecipeStepRequest(2, "된장을 풉니다.")),
            "https://youtu.be/dQw4w9WgXcQ");
        ArgumentCaptor<Recipe> captor = ArgumentCaptor.forClass(Recipe.class);
        given(recipeRepository.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        recipeAdminService.createRecipe(request);

        // then
        Recipe saved = captor.getValue();
        assertThat(saved.getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(saved.getSteps()).hasSize(2);
        assertThat(saved.getStatus()).isEqualTo(RecipeStatus.ACTIVE);
        assertThat(saved.isAutoCartEnabled()).isFalse();
    }

    @Test
    @DisplayName("등록: 조리 순서가 1부터 이어지지 않으면 INVALID_RECIPE_STEPS")
    void createRecipe_invalidSteps() {
        // given
        RecipeSaveRequest request = saveRequest(
            List.of(new RecipeStepRequest(1, "육수"), new RecipeStepRequest(3, "된장")), null);

        // when, then
        assertThatThrownBy(() -> recipeAdminService.createRecipe(request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INVALID_RECIPE_STEPS);
        verify(recipeRepository, never()).save(any());
    }

    @Test
    @DisplayName("상태 변경: 비활성으로 바꾸면 자동 장보기 지정도 풀린다")
    void changeStatus_inactive() {
        // given
        Recipe recipe = recipe();
        ReflectionTestUtils.setField(recipe, "autoCartEnabled", true);
        given(recipeRepository.findByIdForUpdate(RECIPE_ID)).willReturn(Optional.of(recipe));

        // when
        recipeAdminService.changeStatus(RECIPE_ID, new RecipeStatusUpdateRequest(RecipeStatus.INACTIVE));

        // then
        assertThat(recipe.getStatus()).isEqualTo(RecipeStatus.INACTIVE);
        assertThat(recipe.isAutoCartEnabled()).isFalse();
    }

    @Test
    @DisplayName("조회: 없는 레시피면 RECIPE_NOT_FOUND")
    void getRecipe_notFound() {
        // given
        given(recipeRepository.findById(99L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recipeAdminService.getRecipe(99L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.RECIPE_NOT_FOUND);
    }

    @Test
    @DisplayName("재료 저장: 한 요청에 같은 재료가 두 번 있으면 DUPLICATE_RECIPE_INGREDIENT")
    void replaceIngredients_duplicated() {
        // given
        given(recipeRepository.findById(RECIPE_ID)).willReturn(Optional.of(recipe()));
        RecipeIngredientsSaveRequest request = new RecipeIngredientsSaveRequest(List.of(item(3L), item(3L)));

        // when, then
        assertThatThrownBy(() -> recipeAdminService.replaceIngredients(RECIPE_ID, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.DUPLICATE_RECIPE_INGREDIENT);
        verify(recipeIngredientRepository, never()).deleteByRecipeId(any());
    }

    @Test
    @DisplayName("재료 저장: 없는 재료가 있으면 기존 재료를 지우지 않고 INGREDIENT_NOT_FOUND")
    void replaceIngredients_ingredientNotFound() {
        // given
        given(recipeRepository.findById(RECIPE_ID)).willReturn(Optional.of(recipe()));
        given(ingredientRepository.findAllById(List.of(3L, 99L))).willReturn(List.of(ingredient(3L)));
        RecipeIngredientsSaveRequest request = new RecipeIngredientsSaveRequest(List.of(item(3L), item(99L)));

        // when, then
        assertThatThrownBy(() -> recipeAdminService.replaceIngredients(RECIPE_ID, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INGREDIENT_NOT_FOUND);
        verify(recipeIngredientRepository, never()).deleteByRecipeId(any());
    }

    @Test
    @DisplayName("재료 저장: 기존 재료를 지운 뒤 요청한 재료로 바꾸고 개수를 돌려준다")
    void replaceIngredients() {
        // given
        given(recipeRepository.findById(RECIPE_ID)).willReturn(Optional.of(recipe()));
        given(ingredientRepository.findAllById(List.of(3L, 7L))).willReturn(List.of(ingredient(3L), ingredient(7L)));
        RecipeIngredientsSaveRequest request = new RecipeIngredientsSaveRequest(List.of(item(3L), item(7L)));

        // when
        int count = recipeAdminService.replaceIngredients(RECIPE_ID, request);

        // then
        assertThat(count).isEqualTo(2);
        verify(recipeIngredientRepository).deleteByRecipeId(RECIPE_ID);
        verify(recipeIngredientRepository).saveAll(any());
    }
}
