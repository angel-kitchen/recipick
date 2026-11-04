package com.recipick.infra.ai;

import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFridgeAnswer;
import com.recipick.infra.ai.dto.AiRecipeCandidate;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import com.recipick.infra.ai.dto.AiResult;

import java.util.List;

// instruction은 domain이 고른 지시문(관리자가 활성화한 버전이나 코드 기본값)이다.
// 레시피·재료 목록은 구현체가 지시문 뒤에 붙인다
public interface AiClient {

    AiResult<List<AiExtractedMenu>> extractMenus(String instruction, String text, List<AiRecipeOption> recipes);

    AiResult<AiRecipeConditions> extractConditions(String instruction, String text);

    AiResult<List<AiRecommendedRecipe>> recommendRecipes(String instruction, String text,
                                                         List<AiRecipeCandidate> candidates);

    AiResult<AiFridgeAnswer> answerFridgeQuestion(String instruction, String text, List<String> ingredientNames,
                                                  AiFridgeTools tools);
}
