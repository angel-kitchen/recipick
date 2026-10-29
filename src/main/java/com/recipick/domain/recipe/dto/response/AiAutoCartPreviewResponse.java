package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.service.AiAutoCartResolver;
import com.recipick.infra.ai.dto.AiRecipeOption;
import java.util.List;

// 상태에 따라 채우는 필드가 다르다: PREVIEWED는 preview, NEEDS_SELECTION은 menus, FALLBACK은 recipes
public record AiAutoCartPreviewResponse(
    AiResultStatus status,
    AutoCartPreviewResponse preview,
    List<Menu> menus,
    List<RecipeOption> recipes
) {

    public static AiAutoCartPreviewResponse previewed(AutoCartPreviewResponse preview) {
        return new AiAutoCartPreviewResponse(AiResultStatus.PREVIEWED, preview, List.of(), List.of());
    }

    public static AiAutoCartPreviewResponse needsSelection(List<AiAutoCartResolver.MenuSelection> menus) {
        return new AiAutoCartPreviewResponse(
            AiResultStatus.NEEDS_SELECTION, null, menus.stream().map(Menu::from).toList(), List.of());
    }

    public static AiAutoCartPreviewResponse noMatch() {
        return new AiAutoCartPreviewResponse(AiResultStatus.NO_MATCH, null, List.of(), List.of());
    }

    public static AiAutoCartPreviewResponse fallback(List<AiRecipeOption> recipes) {
        return new AiAutoCartPreviewResponse(
            AiResultStatus.FALLBACK, null, List.of(), recipes.stream().map(RecipeOption::from).toList());
    }

    // 후보가 비어 있으면 그 메뉴는 맞는 레시피를 찾지 못했다는 뜻이고, servings가 null이면 레시피 기본 인분을 쓴다
    public record Menu(String mention, Integer servings, List<RecipeOption> candidates) {

        static Menu from(AiAutoCartResolver.MenuSelection menu) {
            return new Menu(menu.mention(), menu.servings(),
                menu.candidates().stream().map(RecipeOption::from).toList());
        }
    }

    public record RecipeOption(Long recipeId, String name) {

        static RecipeOption from(AiRecipeOption recipe) {
            return new RecipeOption(recipe.id(), recipe.name());
        }
    }
}
