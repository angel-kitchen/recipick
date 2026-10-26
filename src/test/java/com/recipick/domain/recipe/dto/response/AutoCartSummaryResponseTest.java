package com.recipick.domain.recipe.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoCartSummaryResponseTest {

    private AutoCartRequest request(List<AutoCartMenu> menus) {
        return AutoCartRequest.builder()
            .memberId(1L)
            .martId(1L)
            .menus(menus)
            .totalPrice(5000L)
            .build();
    }

    @Test
    @DisplayName("메뉴가 하나면 메뉴 이름과 인분만 적는다")
    void menuSummary_single() {
        // given
        AutoCartRequest request = request(List.of(new AutoCartMenu(12L, "된장찌개", 3, List.of())));

        // when
        AutoCartSummaryResponse response = AutoCartSummaryResponse.of(request, "레시픽 역삼점");

        // then
        assertThat(response.menuSummary()).isEqualTo("된장찌개 3인분");
    }

    @Test
    @DisplayName("메뉴가 여러 개면 첫 메뉴 뒤에 나머지 건수를 붙인다")
    void menuSummary_multiple() {
        // given
        AutoCartRequest request = request(List.of(
            new AutoCartMenu(12L, "된장찌개", 3, List.of()),
            new AutoCartMenu(18L, "두부조림", 2, List.of())
        ));

        // when
        AutoCartSummaryResponse response = AutoCartSummaryResponse.of(request, "레시픽 역삼점");

        // then
        assertThat(response.menuSummary()).isEqualTo("된장찌개 3인분 외 1건");
    }
}
