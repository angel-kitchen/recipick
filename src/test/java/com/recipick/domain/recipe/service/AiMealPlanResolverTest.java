package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.service.AiMealPlanResolver.PlannedMeal;
import com.recipick.infra.ai.dto.AiPlannedMeal;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiMealPlanResolverTest {

    @Test
    @DisplayName("레시피는 5개까지만 남긴다")
    void select_keepsAtMostFive() {
        // given
        List<Recipe> recipes = LongStream.rangeClosed(1, 7).mapToObj(AiMealPlanResolverTest::recipe).toList();
        List<AiPlannedMeal> meals = recipes.stream()
            .map(recipe -> new AiPlannedMeal(recipe.getId(), 2, "이유"))
            .toList();

        // when
        List<PlannedMeal> selected = AiMealPlanResolver.select(meals, recipes);

        // then
        assertThat(selected).extracting(meal -> meal.recipe().getId()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("인분이 1~10 밖이면 레시피 기본 인분을 쓴다")
    void select_usesBaseServingsWhenOutOfRange() {
        // given
        List<Recipe> recipes = List.of(recipe(1L), recipe(2L));

        // when
        List<PlannedMeal> selected = AiMealPlanResolver.select(
            List.of(new AiPlannedMeal(1L, 20, "많이"), new AiPlannedMeal(2L, 10, "딱 맞게")), recipes);

        // then
        assertThat(selected).extracting(PlannedMeal::servings).containsExactly(2, 10);
    }

    private static Recipe recipe(long id) {
        Recipe recipe = Recipe.builder().name("레시피 " + id).baseServings(2).steps(List.of()).build();
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }
}
