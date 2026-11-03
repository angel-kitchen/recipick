package com.recipick.domain.recipe.facade;

import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.service.RecipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 상세 조회는 읽기 전용 트랜잭션으로 끝내고, 조회수 증가는 그 뒤 짧은 트랜잭션으로 따로 한다.
// 관리자가 레시피를 잠그고 있어도 회원 상세 조회가 락 때문에 실패하지 않게, 조회수 증가 실패는 응답을 막지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeFacade {

    private final RecipeService recipeService;

    public RecipeDetailResponse getRecipe(Long memberId, Long recipeId, Integer servings) {
        RecipeDetailResponse response = recipeService.getRecipe(memberId, recipeId, servings);
        try {
            recipeService.increaseViewCount(recipeId);
        } catch (RuntimeException e) {
            log.warn("레시피 조회수 증가에 실패했습니다. recipeId={}", recipeId, e);
        }
        return response;
    }
}
