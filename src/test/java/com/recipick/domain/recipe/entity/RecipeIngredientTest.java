package com.recipick.domain.recipe.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.global.common.SaleUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecipeIngredientTest {

    private RecipeIngredient recipeIngredient(String amountPerServing, SaleUnit unit) {
        Ingredient ingredient = Ingredient.builder()
            .name("애호박")
            .unit(unit)
            .build();
        return RecipeIngredient.builder()
            .ingredient(ingredient)
            .amountPerServing(new BigDecimal(amountPerServing))
            .required(true)
            .build();
    }

    @Test
    @DisplayName("1인분 필요량에 인분을 곱한다")
    void calculateRequiredAmount() {
        // given
        RecipeIngredient tofu = recipeIngredient("100", SaleUnit.G);

        // when
        BigDecimal required = tofu.calculateRequiredAmount(3);

        // then
        assertThat(required).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("소수 필요량도 오차 없이 계산한다")
    void calculateRequiredAmount_decimal() {
        // given
        RecipeIngredient zucchini = recipeIngredient("0.5", SaleUnit.EA);

        // when
        BigDecimal required = zucchini.calculateRequiredAmount(3);

        // then
        assertThat(required).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("단위는 재료의 단위를 따른다")
    void getUnit() {
        // given
        RecipeIngredient zucchini = recipeIngredient("0.5", SaleUnit.EA);

        // when, then
        assertThat(zucchini.getUnit()).isEqualTo(SaleUnit.EA);
    }
}
