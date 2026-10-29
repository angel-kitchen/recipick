package com.recipick.domain.recipe.facade;

import com.recipick.domain.recipe.dto.response.AiAutoCartPreviewResponse;
import com.recipick.domain.recipe.dto.response.AutoCartPreviewResponse;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.service.AiAutoCartResolver;
import com.recipick.domain.recipe.service.AiRequestLogService;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// AI 응답을 받은 뒤의 판정과 저장. 미리보기와 요청 이력이 한쪽만 남지 않도록 한 트랜잭션으로 묶는다
@Component
@RequiredArgsConstructor
public class AiAutoCartProcessor {

    private final AutoCartFacade autoCartFacade;
    private final AiRequestLogService aiRequestLogService;

    @Transactional
    public AiAutoCartPreviewResponse process(
        Long memberId, String text, List<AiRecipeOption> recipes, AiResult<List<AiExtractedMenu>> aiResult) {
        if (!aiResult.isSuccess()) {
            List<AiRecipeOption> namedRecipes = AiAutoCartResolver.findNamedRecipes(text, recipes);
            saveLog(memberId, text, AiResultStatus.FALLBACK, aiResult, namedRecipes.stream()
                .map(AiRecipeOption::id)
                .toList(), null);
            return AiAutoCartPreviewResponse.fallback(namedRecipes);
        }

        AiAutoCartResolver.Resolution resolution = AiAutoCartResolver.resolve(aiResult.content(), recipes);
        if (resolution.status() == AiResultStatus.NO_MATCH) {
            saveLog(memberId, text, AiResultStatus.NO_MATCH, aiResult, List.of(), null);
            return AiAutoCartPreviewResponse.noMatch();
        }
        if (resolution.status() == AiResultStatus.NEEDS_SELECTION) {
            saveLog(memberId, text, AiResultStatus.NEEDS_SELECTION, aiResult, resolution.recipeIds(), null);
            return AiAutoCartPreviewResponse.needsSelection(resolution.menus());
        }
        // 미리보기 계산·저장은 직접 고른 경우와 같은 규칙을 쓰도록 기존 미리보기를 이 트랜잭션 안에서 부른다
        AutoCartPreviewResponse preview = autoCartFacade.preview(memberId, resolution.toPreviewRequest());
        saveLog(memberId, text, AiResultStatus.PREVIEWED, aiResult, resolution.recipeIds(),
            preview.autoCartRequestId());
        return AiAutoCartPreviewResponse.previewed(preview);
    }

    private void saveLog(Long memberId, String text, AiResultStatus status, AiResult<?> aiResult,
                         List<Long> recipeIds, Long autoCartRequestId) {
        aiRequestLogService.save(
            memberId, AiRequestType.AUTO_CART, text, status, aiResult, recipeIds, autoCartRequestId);
    }
}
