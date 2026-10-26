package com.recipick.domain.recipe.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoCartRequestTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 14, 5);

    private AutoCartRequest request() {
        return AutoCartRequest.builder()
            .memberId(1L)
            .martId(1L)
            .menus(List.of())
            .totalPrice(5000L)
            .build();
    }

    @Test
    @DisplayName("확정하면 상태·확정 합계·확정 시각이 바뀐다")
    void confirm() {
        // given
        AutoCartRequest request = request();

        // when
        request.confirm(5500L, NOW);

        // then
        assertThat(request.getStatus()).isEqualTo(AutoCartStatus.CONFIRMED);
        assertThat(request.getTotalPrice()).isEqualTo(5500L);
        assertThat(request.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 확정한 요청을 다시 확정하면 AUTO_CART_INVALID_STATUS 예외를 던진다")
    void confirm_alreadyConfirmed() {
        // given
        AutoCartRequest request = request();
        request.confirm(5000L, NOW);

        // when
        Throwable thrown = catchThrowable(() -> request.confirm(5000L, NOW));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.AUTO_CART_INVALID_STATUS));
    }
}
