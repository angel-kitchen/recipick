package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Difficulty;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;

// 순위 화면용 응답. 목록 카드 정보(RecipeSummaryResponse)에 순위 기준 값(찜 수·조회수)을 더했다.
// bookmarkCount는 기간별 찜 순위에서는 그 기간의 찜 수, 조회수 순위에서는 전체 기간의 찜 수다
public record RecipeRankingResponse(
        Long recipeId,
        String name,
        CuisineType cuisineType,
        DishType dishType,
        int baseServings,
        Integer cookingTimeMinutes,
        Difficulty difficulty,
        Integer spicyLevel,
        String thumbnailUrl,
        boolean autoCartAvailable,
        boolean bookmarked,
        long bookmarkCount,
        int viewCount
) {

    public static RecipeRankingResponse of(Recipe recipe, boolean bookmarked, long bookmarkCount) {
        return new RecipeRankingResponse(
            recipe.getId(),
            recipe.getName(),
            recipe.getCuisine(),
            recipe.getDishType(),
            recipe.getBaseServings(),
            recipe.getCookTimeMin(),
            recipe.getDifficulty(),
            recipe.getSpicyLevel(),
            recipe.getThumbnailUrl(),
            recipe.isAutoCartEnabled(),
            bookmarked,
            bookmarkCount,
            recipe.getViewCount()
        );
    }
}
