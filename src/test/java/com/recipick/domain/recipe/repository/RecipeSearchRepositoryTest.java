package com.recipick.domain.recipe.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStep;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RecipeSearchRepositoryTest extends IntegrationTestSupport {

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    EntityManager em;

    @BeforeEach
    void setUp() {
        saveRecipe("초성검색 김치찌개");
        saveRecipe("초성검색 된장찌개");
        em.flush();
        em.clear();
    }

    private Recipe saveRecipe(String name) {
        return recipeRepository.save(Recipe.builder()
            .name(name)
            .baseServings(2)
            .steps(List.of(new RecipeStep(1, "끓입니다.")))
            .build());
    }

    private Page<Recipe> search(String keyword) {
        return recipeRepository.search(
            new RecipeSearchCondition(keyword, null, null, null, null), PageRequest.of(0, 100));
    }

    @Test
    @DisplayName("초성만 입력하면 레시피명 초성으로 검색된다")
    void searchByChoseong() {
        // given
        // when
        Page<Recipe> result = search("ㄱㅊㅉㄱ");

        // then
        assertThat(result.getContent()).extracting(Recipe::getName).containsExactly("초성검색 김치찌개");
    }

    @Test
    @DisplayName("초성 검색어 앞뒤 공백은 무시한다")
    void searchByChoseong_trimmed() {
        // given
        // when
        Page<Recipe> result = search(" ㄷㅈㅉㄱ ");

        // then
        assertThat(result.getContent()).extracting(Recipe::getName).containsExactly("초성검색 된장찌개");
    }

    @Test
    @DisplayName("글자 검색은 기존처럼 레시피명 일부로 찾는다")
    void searchByName() {
        // given
        // when
        Page<Recipe> result = search("된장");

        // then
        assertThat(result.getContent()).extracting(Recipe::getName).containsExactly("초성검색 된장찌개");
    }
}
