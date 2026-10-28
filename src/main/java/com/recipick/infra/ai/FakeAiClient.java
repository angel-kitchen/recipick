package com.recipick.infra.ai;

import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "ai.client", havingValue = "fake", matchIfMissing = true)
public class FakeAiClient implements AiClient {

    static final String MODEL = "fake";
    static final String FAILURE_TRIGGER = "#AI장애";

    private static final Pattern SERVINGS = Pattern.compile("(\\d+)\\s*인분");

    @Override
    public AiResult<List<AiExtractedMenu>> extractMenus(String text, List<AiRecipeOption> recipes) {
        if (text.contains(FAILURE_TRIGGER)) {
            return AiResult.failure(AiFailureType.UNAVAILABLE, AiUsage.failed(MODEL, 0));
        }
        Integer servings = parseServings(text);
        List<AiExtractedMenu> menus = recipes.stream()
            .filter(recipe -> text.contains(recipe.name()))
            .map(recipe -> new AiExtractedMenu(recipe.name(), List.of(recipe.id()),
                servings))
            .toList();
        return AiResult.success(menus, new AiUsage(MODEL, 0, 0, 0, 0));
    }

    private Integer parseServings(String text) {
        Matcher matcher = SERVINGS.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
