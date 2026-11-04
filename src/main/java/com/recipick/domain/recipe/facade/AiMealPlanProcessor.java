package com.recipick.domain.recipe.facade;

import com.recipick.domain.recipe.dto.ActiveAiPrompt;
import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.dto.response.AiMealPlanResponse;
import com.recipick.domain.recipe.dto.response.AutoCartPreviewResponse;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.AiMealPlanResolver;
import com.recipick.domain.recipe.service.AiMealPlanResolver.PlannedMeal;
import com.recipick.domain.recipe.service.AiRequestLogService;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiPlannedMeal;
import com.recipick.infra.ai.dto.AiResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// AI 응답 판정과 미리보기 저장·이력 저장을 한 트랜잭션으로 묶어, 미리보기 없이 PREVIEWED 이력만 남지 않게 한다
@Component
@RequiredArgsConstructor
public class AiMealPlanProcessor {

    private final AutoCartFacade autoCartFacade;
    private final AiRequestLogService aiRequestLogService;

    @Transactional
    public AiMealPlanResponse process(Long memberId, String text, List<Recipe> recipes,
                                      AiResult<List<AiPlannedMeal>> aiResult, ActiveAiPrompt prompt) {
        if (!aiResult.isSuccess()) {
            saveLog(memberId, text, AiResultStatus.FALLBACK, aiResult, List.of(), null, prompt);
            return AiMealPlanResponse.fallback();
        }
        // 빈 목록은 AI가 식단과 관계없는 문장이거나 맞는 레시피가 없다고 판단한 것이라 정상 결과다
        if (aiResult.content().isEmpty()) {
            saveLog(memberId, text, AiResultStatus.NO_MATCH, aiResult, List.of(), null, prompt);
            return AiMealPlanResponse.noMatch();
        }
        List<PlannedMeal> meals = AiMealPlanResolver.select(aiResult.content(), recipes);
        // 목록 밖 레시피만 골랐으면 이유 문장이 없는 레시피를 설명하고 있어 쓸 수 없다
        if (meals.isEmpty()) {
            saveLog(memberId, text, AiResultStatus.FALLBACK,
                new AiResult<>(aiResult.content(), AiFailureType.INVALID_RESPONSE, aiResult.usage()),
                List.of(), null, prompt);
            return AiMealPlanResponse.fallback();
        }
        // 재료 합산·금액은 직접 고른 미리보기와 같은 규칙을 쓰도록 기존 미리보기를 이 트랜잭션 안에서 부른다
        AutoCartPreviewResponse preview = autoCartFacade.preview(memberId, new AutoCartPreviewRequest(meals.stream()
            .map(meal -> new AutoCartPreviewRequest.Menu(meal.recipe().getId(), meal.servings(), List.of()))
            .toList()));
        saveLog(memberId, text, AiResultStatus.PREVIEWED, aiResult,
            meals.stream().map(meal -> meal.recipe().getId()).toList(), preview.autoCartRequestId(), prompt);
        return AiMealPlanResponse.previewed(meals, preview);
    }

    private void saveLog(Long memberId, String text, AiResultStatus status, AiResult<?> aiResult,
                         List<Long> recipeIds, Long autoCartRequestId, ActiveAiPrompt prompt) {
        aiRequestLogService.save(memberId, AiRequestType.MEAL_PLAN, text, status, aiResult, recipeIds,
            autoCartRequestId, List.of(prompt));
    }
}
