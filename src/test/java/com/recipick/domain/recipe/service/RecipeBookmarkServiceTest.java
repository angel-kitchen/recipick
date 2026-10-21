package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.recipe.dto.response.RecipeBookmarkCreateResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmark;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeBookmarkRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipeBookmarkServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long RECIPE_ID = 12L;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeBookmarkRepository recipeBookmarkRepository;

    @InjectMocks
    private RecipeBookmarkService recipeBookmarkService;

    private Recipe recipe() {
        Recipe recipe = Recipe.builder()
            .name("된장찌개")
            .baseServings(2)
            .build();
        ReflectionTestUtils.setField(recipe, "id", RECIPE_ID);
        return recipe;
    }

    @Test
    @DisplayName("찜 등록: 처음 찜하면 저장하고 레시피 ID를 돌려준다")
    void addBookmark() {
        // given
        given(recipeRepository.findByIdAndStatus(RECIPE_ID, RecipeStatus.ACTIVE)).willReturn(Optional.of(recipe()));
        given(recipeBookmarkRepository.existsById(new RecipeBookmarkId(MEMBER_ID, RECIPE_ID))).willReturn(false);
        given(recipeBookmarkRepository.saveAndFlush(any(RecipeBookmark.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        RecipeBookmarkCreateResponse response = recipeBookmarkService.addBookmark(MEMBER_ID, RECIPE_ID);

        // then
        assertThat(response.recipeId()).isEqualTo(RECIPE_ID);
    }

    @Test
    @DisplayName("찜 등록: 없거나 비활성인 레시피면 RECIPE_NOT_FOUND")
    void addBookmark_recipeNotFound() {
        // given
        given(recipeRepository.findByIdAndStatus(RECIPE_ID, RecipeStatus.ACTIVE)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recipeBookmarkService.addBookmark(MEMBER_ID, RECIPE_ID))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.RECIPE_NOT_FOUND);
    }

    @Test
    @DisplayName("찜 등록: 이미 찜했으면 저장하지 않고 DUPLICATE_BOOKMARK")
    void addBookmark_alreadyExists() {
        // given
        given(recipeRepository.findByIdAndStatus(RECIPE_ID, RecipeStatus.ACTIVE)).willReturn(Optional.of(recipe()));
        given(recipeBookmarkRepository.existsById(new RecipeBookmarkId(MEMBER_ID, RECIPE_ID))).willReturn(true);

        // when, then
        assertThatThrownBy(() -> recipeBookmarkService.addBookmark(MEMBER_ID, RECIPE_ID))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.DUPLICATE_BOOKMARK);
        verify(recipeBookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("찜 등록: 동시 요청으로 PK 위반이 나면 DUPLICATE_BOOKMARK로 바꾼다")
    void addBookmark_concurrentDuplicate() {
        // given
        given(recipeRepository.findByIdAndStatus(RECIPE_ID, RecipeStatus.ACTIVE)).willReturn(Optional.of(recipe()));
        given(recipeBookmarkRepository.existsById(new RecipeBookmarkId(MEMBER_ID, RECIPE_ID))).willReturn(false);
        given(recipeBookmarkRepository.saveAndFlush(any(RecipeBookmark.class)))
            .willThrow(new DataIntegrityViolationException("duplicate key"));

        // when, then
        assertThatThrownBy(() -> recipeBookmarkService.addBookmark(MEMBER_ID, RECIPE_ID))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.DUPLICATE_BOOKMARK);
    }

    @Test
    @DisplayName("찜 해제: 찜이 없어도 예외 없이 끝난다")
    void removeBookmark() {
        // when
        recipeBookmarkService.removeBookmark(MEMBER_ID, RECIPE_ID);

        // then
        verify(recipeBookmarkRepository).deleteById(new RecipeBookmarkId(MEMBER_ID, RECIPE_ID));
    }
}
