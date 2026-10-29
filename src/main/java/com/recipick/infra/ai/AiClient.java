package com.recipick.infra.ai;

import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;

import java.util.List;

public interface AiClient {

    AiResult<List<AiExtractedMenu>> extractMenus(String text, List<AiRecipeOption> recipes);

    AiResult<AiRecipeConditions> extractConditions(String text);

    AiResult<List<AiRecommendedRecipe>> recommendRecipes(String text, List<AiRecipeOption> candidates);
}
