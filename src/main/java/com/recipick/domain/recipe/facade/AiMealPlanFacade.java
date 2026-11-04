package com.recipick.domain.recipe.facade;

import com.recipick.domain.recipe.dto.ActiveAiPrompt;
import com.recipick.domain.recipe.dto.response.AiMealPlanResponse;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.AiPromptService;
import com.recipick.domain.recipe.service.AiRequestLimiter;
import com.recipick.domain.recipe.service.AutoCartService;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiPlannedMeal;
import com.recipick.infra.ai.dto.AiResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// AI 일주일 식단의 진입점. 자연어 장보기와 같이 AI 호출 동안 트랜잭션을 걸지 않고,
// 선검증 → 요청 수 제한 → AI 호출 → 판정·미리보기 저장(Processor의 트랜잭션) 순으로 처리한다
@Component
@RequiredArgsConstructor
public class AiMealPlanFacade {

    private final AutoCartFacade autoCartFacade;
    private final AutoCartService autoCartService;
    private final RecipeService recipeService;
    private final AiRequestLimiter aiRequestLimiter;
    private final AiPromptService aiPromptService;
    private final AiClient aiClient;
    private final AiMealPlanProcessor aiMealPlanProcessor;

    public AiMealPlanResponse plan(Long memberId, String text) {
        autoCartFacade.validatePreviewable(memberId);
        aiRequestLimiter.acquire(memberId);
        List<Recipe> recipes = autoCartService.findAutoCartRecipes();
        ActiveAiPrompt prompt = aiPromptService.getActive(AiPromptType.MEAL_PLAN);
        AiResult<List<AiPlannedMeal>> aiResult = aiClient.planMeals(prompt.content(), text, AiRecipeCandidates.of(
            recipes, recipeService.findRequiredIngredientNames(recipes.stream().map(Recipe::getId).toList())));
        return aiMealPlanProcessor.process(memberId, text, recipes, aiResult, prompt);
    }
}
