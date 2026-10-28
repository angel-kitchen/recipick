package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.request.RecipeAdminSearchRequest;
import com.recipick.domain.recipe.dto.request.RecipeAutoCartUpdateRequest;
import com.recipick.domain.recipe.dto.request.RecipeIngredientsSaveRequest;
import com.recipick.domain.recipe.dto.response.RecipeAdminSummaryResponse;
import com.recipick.domain.recipe.dto.response.RecipeAutoCartResponse;
import com.recipick.domain.recipe.dto.response.RecipeIngredientsSaveResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.service.RecipeAdminService;
import com.recipick.global.exception.BusinessException;
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

    private static final Long RECIPE_ID = 12L;

    @Mock
    private RecipeAdminService recipeAdminService;

    @Mock
    private ProductIngredientService productIngredientService;

    @Mock
    private com.recipick.global.audit.AdminAuditRecorder auditRecorder;

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

    @Test
    @DisplayName("재료 저장: 연결 상품 없는 필수 재료가 생기면 자동 장보기 지정을 자동으로 푼다")
    void saveIngredients_released() {
        // given
        Recipe recipe = recipe(RECIPE_ID, true);
        RecipeIngredientsSaveRequest request = new RecipeIngredientsSaveRequest(List.of());
        given(recipeAdminService.replaceIngredients(RECIPE_ID, request)).willReturn(2);
        given(recipeAdminService.getRecipeForUpdate(RECIPE_ID)).willReturn(recipe);
        given(recipeAdminService.findRequiredIngredientIds(List.of(RECIPE_ID)))
            .willReturn(Map.of(RECIPE_ID, List.of(3L, 7L)));
        given(productIngredientService.countLinkedProducts(List.of(3L, 7L))).willReturn(Map.of(3L, 1L, 7L, 0L));

        // when
        RecipeIngredientsSaveResponse response = recipeAdminFacade.saveIngredients(RECIPE_ID, request);

        // then
        assertThat(response.autoCartReleased()).isTrue();
        assertThat(response.autoCartAvailable()).isFalse();
        assertThat(recipe.isAutoCartEnabled()).isFalse();
    }

    @Test
    @DisplayName("재료 저장: 지정되지 않은 레시피는 조건을 다시 보지 않는다")
    void saveIngredients_notEnabled() {
        // given
        RecipeIngredientsSaveRequest request = new RecipeIngredientsSaveRequest(List.of());
        given(recipeAdminService.replaceIngredients(RECIPE_ID, request)).willReturn(0);
        given(recipeAdminService.getRecipeForUpdate(RECIPE_ID)).willReturn(recipe(RECIPE_ID, false));

        // when
        RecipeIngredientsSaveResponse response = recipeAdminFacade.saveIngredients(RECIPE_ID, request);

        // then
        assertThat(response.autoCartReleased()).isFalse();
    }

    @Test
    @DisplayName("자동 장보기 지정: 모든 필수 재료에 연결 상품이 있으면 지정한다")
    void changeAutoCart_enable() {
        // given
        Recipe recipe = recipe(RECIPE_ID, false);
        given(recipeAdminService.getRecipeForUpdate(RECIPE_ID)).willReturn(recipe);
        given(recipeAdminService.findRequiredIngredientIds(List.of(RECIPE_ID)))
            .willReturn(Map.of(RECIPE_ID, List.of(3L)));
        given(productIngredientService.countLinkedProducts(List.of(3L))).willReturn(Map.of(3L, 2L));

        // when
        RecipeAutoCartResponse response =
            recipeAdminFacade.changeAutoCart(RECIPE_ID, new RecipeAutoCartUpdateRequest(true));

        // then
        assertThat(response.autoCartAvailable()).isTrue();
    }

    @Test
    @DisplayName("자동 장보기 지정: 필수 재료가 없으면 AUTO_CART_ENABLE_CONDITION_NOT_MET 예외를 던진다")
    void changeAutoCart_noRequiredIngredient() {
        // given
        given(recipeAdminService.getRecipeForUpdate(RECIPE_ID)).willReturn(recipe(RECIPE_ID, false));
        given(recipeAdminService.findRequiredIngredientIds(List.of(RECIPE_ID))).willReturn(Map.of());

        // when
        Throwable thrown = catchThrowable(() ->
            recipeAdminFacade.changeAutoCart(RECIPE_ID, new RecipeAutoCartUpdateRequest(true)));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.AUTO_CART_ENABLE_CONDITION_NOT_MET));
    }

    @Test
    @DisplayName("자동 장보기 지정: 연결 상품 없는 필수 재료가 있으면 AUTO_CART_ENABLE_CONDITION_NOT_MET 예외를 던진다")
    void changeAutoCart_missingLinkedProduct() {
        // given
        given(recipeAdminService.getRecipeForUpdate(RECIPE_ID)).willReturn(recipe(RECIPE_ID, false));
        given(recipeAdminService.findRequiredIngredientIds(List.of(RECIPE_ID)))
            .willReturn(Map.of(RECIPE_ID, List.of(3L, 7L)));
        given(productIngredientService.countLinkedProducts(List.of(3L, 7L))).willReturn(Map.of(3L, 1L, 7L, 0L));

        // when
        Throwable thrown = catchThrowable(() ->
            recipeAdminFacade.changeAutoCart(RECIPE_ID, new RecipeAutoCartUpdateRequest(true)));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.AUTO_CART_ENABLE_CONDITION_NOT_MET));
    }

    @Test
    @DisplayName("자동 장보기 지정: 비활성 레시피면 조건이 맞아도 AUTO_CART_ENABLE_CONDITION_NOT_MET 예외를 던진다")
    void changeAutoCart_inactive() {
        // given
        Recipe recipe = recipe(RECIPE_ID, false);
        recipe.changeStatus(RecipeStatus.INACTIVE);
        given(recipeAdminService.getRecipeForUpdate(RECIPE_ID)).willReturn(recipe);
        given(recipeAdminService.findRequiredIngredientIds(List.of(RECIPE_ID)))
            .willReturn(Map.of(RECIPE_ID, List.of(3L)));
        given(productIngredientService.countLinkedProducts(any())).willReturn(Map.of(3L, 1L));

        // when
        Throwable thrown = catchThrowable(() ->
            recipeAdminFacade.changeAutoCart(RECIPE_ID, new RecipeAutoCartUpdateRequest(true)));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.AUTO_CART_ENABLE_CONDITION_NOT_MET));
    }

    @Test
    @DisplayName("자동 장보기 해제: 조건을 보지 않고 바로 푼다")
    void changeAutoCart_disable() {
        // given
        Recipe recipe = recipe(RECIPE_ID, true);
        given(recipeAdminService.getRecipeForUpdate(RECIPE_ID)).willReturn(recipe);

        // when
        RecipeAutoCartResponse response =
            recipeAdminFacade.changeAutoCart(RECIPE_ID, new RecipeAutoCartUpdateRequest(false));

        // then
        assertThat(response.autoCartAvailable()).isFalse();
    }
}
