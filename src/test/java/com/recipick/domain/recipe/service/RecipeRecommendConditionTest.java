package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.infra.ai.dto.AiRecipeConditions;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecipeRecommendConditionTest {

    private Recipe recipe(CuisineType cuisine, DishType dishType, Integer cookTimeMin, Integer spicyLevel) {
        return Recipe.builder()
            .name("조건 테스트")
            .cuisine(cuisine)
            .dishType(dishType)
            .baseServings(2)
            .cookTimeMin(cookTimeMin)
            .spicyLevel(spicyLevel)
            .steps(List.of())
            .build();
    }

    @Test
    @DisplayName("올바른 조건 값은 그대로 검색 조건이 된다")
    void from_keepsValidValues() {
        // when
        RecipeRecommendCondition condition = RecipeRecommendCondition.from(
            new AiRecipeConditions("KOREAN", "STEW", 30, 1, List.of(), List.of("오이"), null, null));

        // then
        assertThat(condition).isEqualTo(new RecipeRecommendCondition(
            CuisineType.KOREAN, DishType.STEW, 30, 1, List.of(), List.of("오이"), null, null));
    }

    @Test
    @DisplayName("목록에 없는 종류·범위 밖 시간과 맵기는 오류 대신 조건에서 뺀다")
    void from_dropsInvalidValues() {
        // when
        RecipeRecommendCondition condition = RecipeRecommendCondition.from(
            new AiRecipeConditions("CHINESE", "BBQ", -10, 9, null, null, null, null));

        // then
        assertThat(condition).isEqualTo(
            new RecipeRecommendCondition(null, null, null, null, List.of(), List.of(), null, null));
    }

    @Test
    @DisplayName("예산은 1,000~1,000,000원, 인분은 1~10만 쓰고, 예산·인분만 있으면 거르는 조건이 없는 것으로 본다")
    void from_keepsBudgetAndServingsInRange() {
        // when
        RecipeRecommendCondition valid = RecipeRecommendCondition.from(
            new AiRecipeConditions(null, null, null, null, List.of(), List.of(), 20000, 2));
        RecipeRecommendCondition outOfRange = RecipeRecommendCondition.from(
            new AiRecipeConditions(null, null, null, null, List.of(), List.of(), 500, 11));

        // then
        assertThat(valid.maxBudget()).isEqualTo(20000);
        assertThat(valid.servings()).isEqualTo(2);
        assertThat(valid.isEmpty()).isTrue();
        assertThat(outOfRange.maxBudget()).isNull();
        assertThat(outOfRange.servings()).isNull();
    }

    @Test
    @DisplayName("제외 재료는 빈 값·중복·너무 긴 값을 빼고 최대 5개만 남긴다")
    void from_cleansExcludedIngredients() {
        // given
        String tooLong = "가".repeat(51);
        List<String> names = Arrays.asList(" 오이 ", "오이", "", null, tooLong, "당근", "양파", "마늘", "파", "버섯");

        // when
        RecipeRecommendCondition condition = RecipeRecommendCondition.from(
            new AiRecipeConditions(null, null, null, null, List.of(), names, null, null));

        // then
        assertThat(condition.excludedIngredients()).containsExactly("오이", "당근", "양파", "마늘", "파");
    }

    @Test
    @DisplayName("포함 재료도 제외 재료와 같은 기준으로 정리하고, 포함 재료만 있어도 거르는 조건이 있는 것으로 본다")
    void from_cleansIncludedIngredients() {
        // given
        List<String> names = Arrays.asList(" 계란 ", "계란", "", null, "두부", "대파", "양파", "마늘", "버섯");

        // when
        RecipeRecommendCondition condition = RecipeRecommendCondition.from(
            new AiRecipeConditions(null, null, null, null, names, List.of(), null, null));

        // then
        assertThat(condition.includedIngredients()).containsExactly("계란", "두부", "대파", "양파", "마늘");
        assertThat(condition.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("종류·구분·최대 조리 시간·최대 맵기를 모두 만족해야 후보가 된다")
    void matches_checksEveryCondition() {
        // given
        RecipeRecommendCondition condition =
            new RecipeRecommendCondition(CuisineType.KOREAN, DishType.STEW, 30, 1, List.of(), List.of(), null, null);

        // when & then
        assertThat(condition.matches(recipe(CuisineType.KOREAN, DishType.STEW, 30, 1))).isTrue();
        assertThat(condition.matches(recipe(CuisineType.WESTERN, DishType.STEW, 30, 1))).isFalse();
        assertThat(condition.matches(recipe(CuisineType.KOREAN, DishType.SOUP, 30, 1))).isFalse();
        assertThat(condition.matches(recipe(CuisineType.KOREAN, DishType.STEW, 31, 1))).isFalse();
        assertThat(condition.matches(recipe(CuisineType.KOREAN, DishType.STEW, 30, 2))).isFalse();
    }

    @Test
    @DisplayName("시간·맵기 조건을 걸었는데 레시피에 값이 없으면 만족 여부를 알 수 없어 뺀다")
    void matches_excludesUnknownValuesWhenConditionSet() {
        // given
        RecipeRecommendCondition condition =
            new RecipeRecommendCondition(null, null, 30, 1, List.of(), List.of(), null, null);
        RecipeRecommendCondition noCondition =
            new RecipeRecommendCondition(null, null, null, null, List.of(), List.of(), null, null);
        Recipe unknown = recipe(CuisineType.KOREAN, DishType.STEW, null, null);

        // when & then
        assertThat(condition.matches(unknown)).isFalse();
        assertThat(noCondition.matches(unknown)).isTrue();
    }
}
