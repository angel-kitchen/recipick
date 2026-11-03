package com.recipick.domain.recipe.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmark;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.entity.RecipeStep;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RecipeRankingRepositoryTest extends IntegrationTestSupport {

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    RecipeBookmarkRepository recipeBookmarkRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("찜 순위: 기간 안의 찜이 많은 활성 레시피부터 돌려주고 비활성 레시피는 뺀다")
    void findPopularRecipes() {
        // given
        Recipe one = saveRecipe("랭킹 레시피 하나");
        Recipe three = saveRecipe("랭킹 레시피 셋");
        Recipe two = saveRecipe("랭킹 레시피 둘");
        Recipe inactive = saveRecipe("랭킹 비활성 레시피");
        inactive.changeStatus(RecipeStatus.INACTIVE);
        bookmark(one, 1L);
        bookmark(three, 1L, 2L, 3L);
        bookmark(two, 1L, 2L);
        bookmark(inactive, 1L, 2L, 3L, 4L);
        em.flush();
        em.clear();

        // when
        Page<Recipe> page = recipeBookmarkRepository.findPopularRecipes(
            RecipeStatus.ACTIVE, LocalDateTime.now().minusDays(1), PageRequest.of(0, 100));

        // then
        assertThat(page.getContent())
            .extracting(Recipe::getId)
            .containsSubsequence(three.getId(), two.getId(), one.getId())
            .doesNotContain(inactive.getId());
    }

    @Test
    @DisplayName("찜 순위: 기간 시작 시각 이후에 생긴 찜이 없으면 비어 있다")
    void findPopularRecipes_outOfPeriod() {
        // given
        Recipe recipe = saveRecipe("랭킹 기간 밖 레시피");
        bookmark(recipe, 1L, 2L);
        em.flush();
        em.clear();

        // when
        Page<Recipe> page = recipeBookmarkRepository.findPopularRecipes(
            RecipeStatus.ACTIVE, LocalDateTime.now().plusDays(1), PageRequest.of(0, 100));

        // then
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("조회수: 조회수를 1씩 올리고 마지막 수정 시각(updated_at)은 바꾸지 않는다")
    void increaseViewCount() {
        // given
        Recipe recipe = saveRecipe("랭킹 조회수 레시피");
        em.flush();
        em.clear();
        LocalDateTime updatedAtBefore = recipeRepository.findById(recipe.getId()).orElseThrow().getUpdatedAt();
        em.clear();

        // when
        int updated = recipeRepository.increaseViewCount(recipe.getId());
        recipeRepository.increaseViewCount(recipe.getId());
        em.clear();

        // then
        Recipe found = recipeRepository.findById(recipe.getId()).orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(found.getViewCount()).isEqualTo(2);
        assertThat(found.getUpdatedAt()).isEqualTo(updatedAtBefore);
    }

    @Test
    @DisplayName("조회수 순위: 조회수가 많은 활성 레시피부터 돌려주고 비활성 레시피는 뺀다")
    void findByStatusOrderByViewCountDesc() {
        // given
        Recipe few = saveRecipe("랭킹 조회 적은 레시피");
        Recipe many = saveRecipe("랭킹 조회 많은 레시피");
        Recipe inactive = saveRecipe("랭킹 조회 비활성 레시피");
        inactive.changeStatus(RecipeStatus.INACTIVE);
        em.flush();
        increase(few, 1);
        increase(many, 3);
        increase(inactive, 10);
        em.clear();

        // when
        Page<Recipe> page = recipeRepository.findByStatusOrderByViewCountDescIdDesc(
            RecipeStatus.ACTIVE, PageRequest.of(0, 5));

        // then
        assertThat(page.getContent())
            .extracting(Recipe::getId)
            .startsWith(many.getId(), few.getId())
            .doesNotContain(inactive.getId());
        assertThat(page.getContent().get(0).getViewCount()).isEqualTo(3);
    }

    private Recipe saveRecipe(String name) {
        return recipeRepository.save(Recipe.builder()
            .name(name)
            .baseServings(2)
            .steps(List.of(new RecipeStep(1, "끓입니다.")))
            .build());
    }

    private void bookmark(Recipe recipe, Long... memberIds) {
        for (Long memberId : memberIds) {
            recipeBookmarkRepository.save(RecipeBookmark.builder()
                .memberId(memberId)
                .recipe(recipe)
                .build());
        }
    }

    private void increase(Recipe recipe, int times) {
        for (int i = 0; i < times; i++) {
            recipeRepository.increaseViewCount(recipe.getId());
        }
    }
}
