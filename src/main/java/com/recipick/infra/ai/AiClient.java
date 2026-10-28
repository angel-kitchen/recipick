package com.recipick.infra.ai;

import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiResult;

import java.util.List;

public interface AiClient {

    AiResult<List<AiExtractedMenu>> extractMenus(String text, List<AiRecipeOption> recipes);
}
