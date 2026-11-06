package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStep;
import com.recipick.domain.recipe.repository.RecipeRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RecipeChoseongBackfillerTest extends IntegrationTestSupport {

    private static final LocalDateTime OLD = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Autowired
    RecipeChoseongBackfiller backfiller;

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    EntityManager em;

    private Recipe saveRecipe(String name) {
        return recipeRepository.save(Recipe.builder()
            .name(name)
            .baseServings(2)
            .steps(List.of(new RecipeStep(1, "끓입니다.")))
            .build());
    }

    // 이전 버전에서 저장돼 초성이 비어 있고 수정 시각이 오래된 행을 만든다
    private void makeLegacy(Recipe recipe) {
        em.flush();
        em.createNativeQuery("update recipe set name_choseong = null, updated_at = ?1 where id = ?2")
            .setParameter(1, OLD)
            .setParameter(2, recipe.getId())
            .executeUpdate();
        em.clear();
    }

    private String choseongOf(Long id) {
        return (String) em.createNativeQuery("select name_choseong from recipe where id = ?1")
            .setParameter(1, id)
            .getSingleResult();
    }

    private LocalDateTime updatedAtOf(Long id) {
        return ((Timestamp) em.createNativeQuery("select updated_at from recipe where id = ?1")
            .setParameter(1, id)
            .getSingleResult()).toLocalDateTime();
    }

    @Test
    @DisplayName("초성이 비어 있는 레시피만 채우고 이미 채워진 레시피는 그대로 둔다")
    void fillsOnlyEmptyRows() {
        // given
        Recipe legacy = saveRecipe("채우기 김치찌개");
        Recipe filled = saveRecipe("채우기 된장찌개");
        makeLegacy(legacy);

        // when
        backfiller.run(null);
        em.clear();

        // then
        assertThat(choseongOf(legacy.getId())).isEqualTo("ㅊㅇㄱ ㄱㅊㅉㄱ");
        assertThat(choseongOf(filled.getId())).isEqualTo("ㅊㅇㄱ ㄷㅈㅉㄱ");
    }

    @Test
    @DisplayName("초성을 채워도 레시피의 수정 시각은 바뀌지 않는다")
    void keepsUpdatedAt() {
        // given
        Recipe legacy = saveRecipe("채우기 김치찌개");
        makeLegacy(legacy);

        // when
        backfiller.run(null);
        em.clear();

        // then
        assertThat(updatedAtOf(legacy.getId())).isEqualTo(OLD);
    }

    @Test
    @DisplayName("여러 번 실행해도 결과가 같다")
    void idempotent() {
        // given
        Recipe legacy = saveRecipe("채우기 김치찌개");
        makeLegacy(legacy);

        // when
        backfiller.run(null);
        backfiller.run(null);
        em.clear();

        // then
        assertThat(choseongOf(legacy.getId())).isEqualTo("ㅊㅇㄱ ㄱㅊㅉㄱ");
    }
}
