package com.recipick.domain.recipe.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecipeTest {

    private Recipe recipe(int baseServings) {
        return Recipe.builder()
                .name("된장찌개")
                .baseServings(baseServings)
                .build();
    }

    private Recipe recipeWithVideo(String youtubeVideoId) {
        return Recipe.builder()
                .name("된장찌개")
                .baseServings(2)
                .youtubeVideoId(youtubeVideoId)
                .build();
    }

    @Test
    @DisplayName("인분을 생략하면 기본 인분을 쓴다")
    void resolveServings_default() {
        // given
        Recipe recipe = recipe(2);

        // when
        int servings = recipe.resolveServings(null);

        // then
        assertThat(servings).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10})
    @DisplayName("인분이 1~10이면 그대로 쓴다")
    void resolveServings_inRange(int requested) {
        // given
        Recipe recipe = recipe(2);

        // when
        int servings = recipe.resolveServings(requested);

        // then
        assertThat(servings).isEqualTo(requested);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    @DisplayName("인분이 1~10을 벗어나면 INVALID_SERVINGS")
    void resolveServings_outOfRange(int requested) {
        // given
        Recipe recipe = recipe(2);

        // when, then
        assertThatThrownBy(() -> recipe.resolveServings(requested))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RecipeErrorCode.INVALID_SERVINGS);
    }

    @Test
    @DisplayName("등록 직후에는 활성 상태이고 자동 장보기는 지정되지 않는다")
    void create_defaultStatus() {
        // when
        Recipe recipe = recipe(2);

        // then
        assertThat(recipe.isAutoCartEnabled()).isFalse();
        assertThat(recipe.getStatus()).isEqualTo(RecipeStatus.ACTIVE);
    }

    @Test
    @DisplayName("영상 ID로 유튜브 시청 URL과 썸네일 URL을 만든다")
    void youtubeUrls() {
        // given
        Recipe recipe = recipeWithVideo("dQw4w9WgXcQ");

        // when, then
        assertThat(recipe.getYoutubeWatchUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(recipe.getThumbnailUrl()).isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
    }

    @Test
    @DisplayName("영상 ID가 없으면 유튜브 URL과 썸네일은 null이다")
    void youtubeUrls_noVideo() {
        // given
        Recipe recipe = recipeWithVideo(null);

        // when, then
        assertThat(recipe.getYoutubeWatchUrl()).isNull();
        assertThat(recipe.getThumbnailUrl()).isNull();
    }
}
