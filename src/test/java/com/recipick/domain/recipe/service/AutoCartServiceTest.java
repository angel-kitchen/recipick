package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.AutoCartRequestRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutoCartServiceTest {

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @Mock
    private AutoCartRequestRepository autoCartRequestRepository;

    @InjectMocks
    private AutoCartService autoCartService;

    private final Recipe stew = recipe(12L, "된장찌개", true);
    private final Recipe braised = recipe(18L, "두부조림", true);
    private final Ingredient tofu = ingredient(3L, "두부");
    private final Ingredient pepper = ingredient(21L, "청양고추");

    private Recipe recipe(Long id, String name, boolean autoCartEnabled) {
        Recipe recipe = Recipe.builder()
            .name(name)
            .baseServings(2)
            .build();
        ReflectionTestUtils.setField(recipe, "id", id);
        ReflectionTestUtils.setField(recipe, "autoCartEnabled", autoCartEnabled);
        return recipe;
    }

    private Ingredient ingredient(Long id, String name) {
        Ingredient ingredient = Ingredient.builder().name(name).unit(SaleUnit.G).build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private RecipeIngredient recipeIngredient(Recipe recipe, Ingredient ingredient, String amount, boolean required) {
        return RecipeIngredient.builder()
            .recipe(recipe)
            .ingredient(ingredient)
            .amountPerServing(new BigDecimal(amount))
            .required(required)
            .build();
    }

    private AutoCartPreviewRequest.Menu menu(Long recipeId, Integer servings, List<Long> optionalIngredientIds) {
        return new AutoCartPreviewRequest.Menu(recipeId, servings, optionalIngredientIds);
    }

    @Test
    @DisplayName("메뉴: 레시피 이름·인분·선택 재료를 담아 돌려준다")
    void resolveMenus() {
        // given
        given(recipeRepository.findAllById(List.of(12L))).willReturn(List.of(stew));
        given(recipeIngredientRepository.findAllWithIngredientByRecipeIds(List.of(12L)))
            .willReturn(List.of(recipeIngredient(stew, tofu, "100", true), recipeIngredient(stew, pepper, "1", false)));

        // when
        List<AutoCartMenu> menus = autoCartService.resolveMenus(List.of(menu(12L, 3, List.of(21L))));

        // then
        assertThat(menus).containsExactly(new AutoCartMenu(12L, "된장찌개", 3, List.of(21L)));
    }

    @Test
    @DisplayName("메뉴: 같은 레시피를 두 번 고르면 DUPLICATE_MENU_IN_REQUEST 예외를 던진다")
    void resolveMenus_duplicate() {
        // when
        Throwable thrown = catchThrowable(() ->
            autoCartService.resolveMenus(List.of(menu(12L, 2, null), menu(12L, 3, null))));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.DUPLICATE_MENU_IN_REQUEST));
    }

    @Test
    @DisplayName("메뉴: 없는 레시피면 RECIPE_NOT_FOUND 예외를 던진다")
    void resolveMenus_notFound() {
        // given
        given(recipeRepository.findAllById(List.of(99L))).willReturn(List.of());

        // when
        Throwable thrown = catchThrowable(() -> autoCartService.resolveMenus(List.of(menu(99L, 2, null))));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.RECIPE_NOT_FOUND));
    }

    @Test
    @DisplayName("메뉴: 자동 장보기 지정이 안 된 레시피면 RECIPE_NOT_AUTO_CART_ENABLED 예외를 던진다")
    void resolveMenus_notEnabled() {
        // given
        given(recipeRepository.findAllById(List.of(12L))).willReturn(List.of(recipe(12L, "된장찌개", false)));

        // when
        Throwable thrown = catchThrowable(() -> autoCartService.resolveMenus(List.of(menu(12L, 2, null))));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.RECIPE_NOT_AUTO_CART_ENABLED));
    }

    @Test
    @DisplayName("메뉴: 인분이 1~10을 벗어나면 INVALID_SERVINGS 예외를 던진다")
    void resolveMenus_invalidServings() {
        // given
        given(recipeRepository.findAllById(List.of(12L))).willReturn(List.of(stew));

        // when
        Throwable thrown = catchThrowable(() -> autoCartService.resolveMenus(List.of(menu(12L, 11, null))));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.INVALID_SERVINGS));
    }

    @Test
    @DisplayName("메뉴: 필수 재료나 다른 레시피 재료를 선택 재료로 보내면 OPTIONAL_INGREDIENT_NOT_IN_RECIPE 예외를 던진다")
    void resolveMenus_notOptional() {
        // given
        given(recipeRepository.findAllById(List.of(12L))).willReturn(List.of(stew));
        given(recipeIngredientRepository.findAllWithIngredientByRecipeIds(List.of(12L)))
            .willReturn(List.of(recipeIngredient(stew, tofu, "100", true)));

        // when
        Throwable thrown = catchThrowable(() -> autoCartService.resolveMenus(List.of(menu(12L, 2, List.of(3L)))));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.OPTIONAL_INGREDIENT_NOT_IN_RECIPE));
    }

    @Test
    @DisplayName("재료 합산: 여러 메뉴의 같은 재료는 더하고, 고르지 않은 선택 재료는 뺀다")
    void sumIngredients() {
        // given
        List<AutoCartMenu> menus = List.of(
            new AutoCartMenu(12L, "된장찌개", 3, List.of()),
            new AutoCartMenu(18L, "두부조림", 2, List.of())
        );
        given(recipeIngredientRepository.findAllWithIngredientByRecipeIds(Set.of(12L, 18L)))
            .willReturn(List.of(
                recipeIngredient(stew, tofu, "100", true),
                recipeIngredient(stew, pepper, "0.5", false),
                recipeIngredient(braised, tofu, "150.5", true)
            ));

        // when
        List<RequiredIngredient> ingredients = autoCartService.sumIngredients(menus);

        // then
        assertThat(ingredients).hasSize(1);
        assertThat(ingredients.get(0).ingredientId()).isEqualTo(3L);
        assertThat(ingredients.get(0).totalRequiredAmount()).isEqualByComparingTo("601");
    }
}
