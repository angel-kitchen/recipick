package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.AiRecipeRecommendResolver.Recommendation;
import com.recipick.infra.ai.dto.AiRecommendedRecipe;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiRecipeRecommendResolverTest {

    private static Recipe recipe(long id) {
        Recipe recipe = Recipe.builder().name("레시피" + id).baseServings(2).steps(List.of()).build();
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }

    private static List<Recipe> recipes(long count) {
        return LongStream.rangeClosed(1, count).mapToObj(AiRecipeRecommendResolverTest::recipe).toList();
    }

    private static List<Long> ids(List<Recommendation> recommendations) {
        return recommendations.stream().map(recommendation -> recommendation.recipe().getId()).toList();
    }

    @Test
    @DisplayName("AI가 고른 순서대로 후보 안의 레시피와 추천 이유를 남긴다")
    void select_keepsPicksInCandidates() {
        // given
        List<AiRecommendedRecipe> picks = List.of(
            new AiRecommendedRecipe(3L, "빨리 만들 수 있어요."),
            new AiRecommendedRecipe(1L, "맵지 않아요."));

        // when
        List<Recommendation> result = AiRecipeRecommendResolver.select(picks, recipes(3));

        // then
        assertThat(ids(result)).containsExactly(3L, 1L);
        assertThat(result.get(0).reason()).isEqualTo("빨리 만들 수 있어요.");
    }

    @Test
    @DisplayName("후보 밖 레시피와 중복으로 고른 레시피는 뺀다")
    void select_dropsOutsideAndDuplicatedPicks() {
        // given
        List<AiRecommendedRecipe> picks = List.of(
            new AiRecommendedRecipe(99L, "목록에 없음"),
            new AiRecommendedRecipe(2L, "첫 번째"),
            new AiRecommendedRecipe(2L, "두 번째"),
            new AiRecommendedRecipe(null, "ID 없음"));

        // when
        List<Recommendation> result = AiRecipeRecommendResolver.select(picks, recipes(3));

        // then
        assertThat(ids(result)).containsExactly(2L);
        assertThat(result.get(0).reason()).isEqualTo("첫 번째");
    }

    @Test
    @DisplayName("AI가 5개보다 많이 골라도 최대 5개만 남긴다")
    void select_keepsAtMostFive() {
        // given
        List<AiRecommendedRecipe> picks = LongStream.rangeClosed(1, 7)
            .mapToObj(id -> new AiRecommendedRecipe(id, "이유"))
            .toList();

        // when
        List<Recommendation> result = AiRecipeRecommendResolver.select(picks, recipes(7));

        // then
        assertThat(ids(result)).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("대체 결과는 후보 중 앞의 5개를 추천 이유 없이 보여 준다")
    void fallback_returnsFirstFiveWithoutReason() {
        // when
        List<Recommendation> result = AiRecipeRecommendResolver.fallback(recipes(7));

        // then
        assertThat(ids(result)).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(result).allSatisfy(recommendation -> assertThat(recommendation.reason()).isNull());
    }
}
