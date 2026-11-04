package com.recipick.infra.ai;

import com.recipick.infra.ai.dto.AiFridgeRecipeSearch;
import com.recipick.infra.ai.dto.AiIngredientPrice;
import com.recipick.infra.ai.dto.AiToolResult;
import java.util.List;

// 냉장고 파먹기에서 AI가 호출하는 서버 기능. 회원·마트는 요청마다 domain이 정해 구현하므로 AI가 넘기지 않는다
public interface AiFridgeTools {

    AiToolResult<AiFridgeRecipeSearch> findRecipes(List<String> ingredientNames);

    AiToolResult<List<AiIngredientPrice>> findIngredientPrices(List<Long> ingredientIds);
}
