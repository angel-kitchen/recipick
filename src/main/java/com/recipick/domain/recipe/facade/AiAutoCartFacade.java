package com.recipick.domain.recipe.facade;

import com.recipick.domain.recipe.dto.response.AiAutoCartPreviewResponse;
import com.recipick.domain.recipe.service.AiRequestLimiter;
import com.recipick.domain.recipe.service.AutoCartService;
import com.recipick.infra.ai.AiClient;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// AI 자연어 장보기의 진입점. AI 호출이 수 초 걸려 DB 연결을 잡고 기다리지 않도록 트랜잭션을 걸지 않는다.
// 선검증(각 Service의 짧은 읽기) → 요청 수 제한 → AI 호출 → 판정·저장(Processor의 트랜잭션) 순이다.
@Component
@RequiredArgsConstructor
public class AiAutoCartFacade {

    private final AutoCartFacade autoCartFacade;
    private final AutoCartService autoCartService;
    private final AiRequestLimiter aiRequestLimiter;
    private final AiClient aiClient;
    private final AiAutoCartProcessor aiAutoCartProcessor;

    public AiAutoCartPreviewResponse preview(Long memberId, String text) {
        autoCartFacade.validatePreviewable(memberId);
        aiRequestLimiter.acquire(memberId);
        List<AiRecipeOption> recipes = autoCartService.findAiRecipeOptions();
        AiResult<List<AiExtractedMenu>> aiResult = aiClient.extractMenus(text, recipes);
        return aiAutoCartProcessor.process(memberId, text, recipes, aiResult);
    }
}
