package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeFacadeTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private RecipeService recipeService;

    @InjectMocks
    private RecipeFacade recipeFacade;

    @Test
    @DisplayName("상세: 상세를 읽은 뒤 조회수를 올리고 읽은 응답을 그대로 돌려준다")
    void getRecipe() {
        // given
        RecipeDetailResponse expected = mock(RecipeDetailResponse.class);
        given(recipeService.getRecipe(MEMBER_ID, 12L, 3)).willReturn(expected);

        // when
        RecipeDetailResponse result = recipeFacade.getRecipe(MEMBER_ID, 12L, 3);

        // then
        assertThat(result).isSameAs(expected);
        verify(recipeService).increaseViewCount(12L);
    }

    @Test
    @DisplayName("상세: 조회수 증가에 실패해도 상세 응답은 정상으로 돌려준다")
    void getRecipe_viewCountFailure() {
        // given
        RecipeDetailResponse expected = mock(RecipeDetailResponse.class);
        given(recipeService.getRecipe(MEMBER_ID, 12L, null)).willReturn(expected);
        willThrow(new IllegalStateException("락 대기 시간 초과")).given(recipeService).increaseViewCount(12L);

        // when
        RecipeDetailResponse result = recipeFacade.getRecipe(MEMBER_ID, 12L, null);

        // then
        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("상세: 없거나 비활성인 레시피는 오류를 그대로 던지고 조회수를 올리지 않는다")
    void getRecipe_notFound() {
        // given
        given(recipeService.getRecipe(MEMBER_ID, 99L, null))
            .willThrow(new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));

        // when, then
        assertThatThrownBy(() -> recipeFacade.getRecipe(MEMBER_ID, 99L, null))
            .isInstanceOf(BusinessException.class);
        verify(recipeService, never()).increaseViewCount(any());
    }
}
