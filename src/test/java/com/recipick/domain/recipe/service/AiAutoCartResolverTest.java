package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiRecipeOption;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiAutoCartResolverTest {

    private static final AiRecipeOption KIMCHI_STEW = new AiRecipeOption(1L, "김치찌개");
    private static final AiRecipeOption SOYBEAN_STEW = new AiRecipeOption(2L, "된장찌개");
    private static final AiRecipeOption PASTA = new AiRecipeOption(3L, "크림 파스타");
    private static final List<AiRecipeOption> RECIPES = List.of(KIMCHI_STEW, SOYBEAN_STEW, PASTA);

    @Test
    @DisplayName("모든 메뉴의 레시피가 하나로 정해지면 미리보기를 만들 수 있다")
    void resolve_previewsWhenEveryMenuHasOneRecipe() {
        // given
        List<AiExtractedMenu> menus = List.of(
            new AiExtractedMenu("김치찌개", List.of(1L), 3),
            new AiExtractedMenu("파스타", List.of(3L), null));

        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(menus, RECIPES);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.PREVIEWED);
        assertThat(resolution.toPreviewRequest().menus()).containsExactly(
            new AutoCartPreviewRequest.Menu(1L, 3, List.of()),
            new AutoCartPreviewRequest.Menu(3L, null, List.of()));
        assertThat(resolution.recipeIds()).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("후보가 여러 개인 메뉴가 있으면 사용자가 고르게 한다")
    void resolve_needsSelectionWhenMenuIsAmbiguous() {
        // given
        List<AiExtractedMenu> menus = List.of(new AiExtractedMenu("찌개", List.of(1L, 2L), 2));

        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(menus, RECIPES);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.NEEDS_SELECTION);
        assertThat(resolution.menus().get(0).candidates()).containsExactly(KIMCHI_STEW, SOYBEAN_STEW);
    }

    @Test
    @DisplayName("일부 메뉴만 레시피를 찾으면 찾지 못한 메뉴도 함께 보여 주고 사용자가 고르게 한다")
    void resolve_needsSelectionWhenSomeMenusUnmatched() {
        // given
        List<AiExtractedMenu> menus = List.of(
            new AiExtractedMenu("김치찌개", List.of(1L), null),
            new AiExtractedMenu("마라탕", List.of(), null));

        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(menus, RECIPES);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.NEEDS_SELECTION);
        assertThat(resolution.menus()).extracting(AiAutoCartResolver.MenuSelection::mention)
            .containsExactly("김치찌개", "마라탕");
        assertThat(resolution.recipeIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("목록에 없는 레시피 ID는 AI가 골라도 버린다")
    void resolve_dropsRecipesOutsideCatalog() {
        // given
        List<AiExtractedMenu> menus = List.of(new AiExtractedMenu("김치찌개", List.of(99L, 1L), null));

        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(menus, RECIPES);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.PREVIEWED);
        assertThat(resolution.recipeIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("인분이 1~10을 벗어나면 인분을 비우고 사용자가 고르게 한다")
    void resolve_needsSelectionWhenServingsOutOfRange() {
        // given
        List<AiExtractedMenu> menus = List.of(new AiExtractedMenu("김치찌개", List.of(1L), 30));

        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(menus, RECIPES);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.NEEDS_SELECTION);
        assertThat(resolution.menus().get(0).servings()).isNull();
    }

    @Test
    @DisplayName("같은 레시피가 두 메뉴에 나오면 사용자가 고르게 한다")
    void resolve_needsSelectionWhenRecipeDuplicated() {
        // given
        List<AiExtractedMenu> menus = List.of(
            new AiExtractedMenu("김치찌개", List.of(1L), 2),
            new AiExtractedMenu("김치찌개 한 번 더", List.of(1L), 2));

        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(menus, RECIPES);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.NEEDS_SELECTION);
    }

    @Test
    @DisplayName("메뉴가 5개를 넘으면 사용자가 고르게 한다")
    void resolve_needsSelectionWhenTooManyMenus() {
        // given
        List<AiRecipeOption> recipes = List.of(
            new AiRecipeOption(1L, "a"), new AiRecipeOption(2L, "b"), new AiRecipeOption(3L, "c"),
            new AiRecipeOption(4L, "d"), new AiRecipeOption(5L, "e"), new AiRecipeOption(6L, "f"));
        List<AiExtractedMenu> menus = recipes.stream()
            .map(recipe -> new AiExtractedMenu(recipe.name(), List.of(recipe.id()), null))
            .toList();

        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(menus, recipes);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.NEEDS_SELECTION);
    }

    @Test
    @DisplayName("어느 메뉴도 레시피를 찾지 못하면 맞는 레시피 없음으로 본다")
    void resolve_noMatchWhenNothingFound() {
        // given
        List<AiExtractedMenu> menus = List.of(new AiExtractedMenu("마라탕", List.of(99L), null));

        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(menus, RECIPES);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.NO_MATCH);
        assertThat(resolution.recipeIds()).isEmpty();
    }

    @Test
    @DisplayName("AI가 메뉴를 하나도 찾지 못해도 맞는 레시피 없음으로 본다")
    void resolve_noMatchWhenNoMenus() {
        // when
        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(List.of(), RECIPES);

        // then
        assertThat(resolution.status()).isEqualTo(AiResultStatus.NO_MATCH);
    }

    @Test
    @DisplayName("AI 장애 때는 문장에 이름이 들어 있는 레시피만 대신 보여 준다")
    void findNamedRecipes_returnsRecipesNamedInText() {
        // when
        List<AiRecipeOption> recipes = AiAutoCartResolver.findNamedRecipes("오늘은 된장찌개랑 크림 파스타", RECIPES);

        // then
        assertThat(recipes).containsExactly(SOYBEAN_STEW, PASTA);
    }
}
