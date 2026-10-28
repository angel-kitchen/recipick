package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiRecipeOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// AI가 고른 결과를 그대로 믿지 않고, 자동 장보기 가능한 레시피 목록과 메뉴·인분 규칙으로 다시 판정한다
public final class AiAutoCartResolver {

    private static final int MAX_MENUS = 5;

    private AiAutoCartResolver() {
    }

    public static Resolution resolve(List<AiExtractedMenu> extractedMenus, List<AiRecipeOption> recipes) {
        Map<Long, AiRecipeOption> recipeById = recipes.stream()
            .collect(Collectors.toMap(AiRecipeOption::id, Function.identity()));
        List<MenuSelection> menus = extractedMenus.stream()
            .map(menu -> toSelection(menu, recipeById))
            .toList();
        if (menus.stream().allMatch(menu -> menu.candidates().isEmpty())) {
            return new Resolution(AiResultStatus.NO_MATCH, List.of());
        }
        AiResultStatus status = isPreviewable(menus) ? AiResultStatus.PREVIEWED : AiResultStatus.NEEDS_SELECTION;
        return new Resolution(status, menus);
    }

    // AI 장애 때 대신 보여 줄 결과. 문장에 레시피 이름이 그대로 들어 있는 것만 골라 사용자가 직접 고르게 한다
    public static List<AiRecipeOption> findNamedRecipes(String text, List<AiRecipeOption> recipes) {
        return recipes.stream()
            .filter(recipe -> text.contains(recipe.name()))
            .toList();
    }

    // 목록 밖 ID는 AI가 지어낸 값일 수 있어 버리고, 인분이 범위를 벗어나면 사용자가 다시 정하도록 비운다
    private static MenuSelection toSelection(AiExtractedMenu menu, Map<Long, AiRecipeOption> recipeById) {
        List<AiRecipeOption> candidates = menu.recipeIds().stream()
            .distinct()
            .map(recipeById::get)
            .filter(Objects::nonNull)
            .toList();
        Integer servings = menu.servings();
        boolean validServings = servings == null
            || (servings >= Recipe.MIN_SERVINGS && servings <= Recipe.MAX_SERVINGS);
        return new MenuSelection(menu.mention(), validServings ? servings : null, validServings, candidates);
    }

    private static boolean isPreviewable(List<MenuSelection> menus) {
        long distinctRecipes = menus.stream()
            .filter(menu -> menu.candidates().size() == 1)
            .map(menu -> menu.candidates().get(0).id())
            .distinct()
            .count();
        return menus.size() <= MAX_MENUS
            && distinctRecipes == menus.size()
            && menus.stream().allMatch(MenuSelection::validServings);
    }

    public record Resolution(AiResultStatus status, List<MenuSelection> menus) {

        public AutoCartPreviewRequest toPreviewRequest() {
            return new AutoCartPreviewRequest(menus.stream()
                .map(menu -> new AutoCartPreviewRequest.Menu(menu.candidates().get(0).id(), menu.servings(), List.of()))
                .toList());
        }

        public List<Long> recipeIds() {
            return menus.stream()
                .flatMap(menu -> menu.candidates().stream())
                .map(AiRecipeOption::id)
                .distinct()
                .toList();
        }
    }

    public record MenuSelection(String mention, Integer servings, boolean validServings,
                                List<AiRecipeOption> candidates) {
    }
}
