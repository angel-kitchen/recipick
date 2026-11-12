package com.recipick.domain.recipe.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.entity.RecipeStep;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RecipeSuggestionRepositoryTest extends IntegrationTestSupport {

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    EntityManager em;

    private Recipe saveRecipe(String name, int viewCount) {
        Recipe recipe = Recipe.builder()
            .name(name)
            .baseServings(2)
            .steps(List.of(new RecipeStep(1, "끓입니다.")))
            .build();
        ReflectionTestUtils.setField(recipe, "viewCount", viewCount);
        return recipeRepository.save(recipe);
    }

    private List<String> suggest(String query, int limit) {
        em.flush();
        em.clear();
        return recipeRepository.findSuggestions(query, limit).stream().map(Recipe::getName).toList();
    }

    @Test
    @DisplayName("이름이 검색어로 시작하는 레시피를 먼저, 같은 순위는 조회수 많은 순으로 띄어쓰기를 무시하고 보여 준다")
    void findSuggestions_ordersPrefixMatchesFirstThenViewCount() {
        // given
        saveRecipe("참치김치찌개", 500);
        saveRecipe("김치 볶음밥", 10);
        saveRecipe("김치찌개", 300);
        saveRecipe("된장찌개", 900);

        // when
        List<String> names = suggest("김치볶", 8);
        List<String> ordered = suggest("김치", 8);

        // then
        assertThat(names).containsExactly("김치 볶음밥");
        assertThat(ordered).containsExactly("김치찌개", "김치 볶음밥", "참치김치찌개");
    }

    @Test
    @DisplayName("초성만 입력하면 레시피 이름의 초성과 비교한다")
    void findSuggestions_matchesChoseong() {
        // given
        saveRecipe("김치찌개", 0);
        saveRecipe("까르보나라", 0);
        saveRecipe("된장찌개", 0);

        // when
        List<String> names = suggest("ㄱㅊ", 8);

        // then
        assertThat(names).containsExactly("김치찌개");
    }

    @Test
    @DisplayName("요청한 개수까지만 보여 주고, 영문은 대소문자를 가리지 않는다")
    void findSuggestions_limitsIgnoringCase() {
        // given
        IntStream.rangeClosed(1, 10).forEach(i -> saveRecipe("Pasta " + i, i));

        // when
        List<String> names = suggest("pas", 8);

        // then
        assertThat(names).hasSize(8).startsWith("Pasta 10", "Pasta 9");
    }

    @Test
    @DisplayName("비활성 레시피는 자동완성에 보이지 않는다")
    void findSuggestions_excludesInactive() {
        // given
        saveRecipe("김치찌개", 0);
        saveRecipe("김치전", 0).changeStatus(RecipeStatus.INACTIVE);

        // when
        List<String> names = suggest("김치", 8);

        // then
        assertThat(names).containsExactly("김치찌개");
    }
}
