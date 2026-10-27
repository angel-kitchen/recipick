package com.recipick.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeAiClientTest {

    private final FakeAiClient fakeAiClient = new FakeAiClient();
    private final List<AiRecipeOption> recipes = List.of(
        new AiRecipeOption(1L, "김치찌개"),
        new AiRecipeOption(2L, "된장찌개"),
        new AiRecipeOption(3L, "크림 파스타"));

    @Test
    @DisplayName("문장에 들어 있는 레시피 이름과 인분을 메뉴로 돌려준다")
    void extractMenus_returnsRecipesNamedInText() {
        // given
        String text = "오늘 저녁 김치찌개랑 크림 파스타 3인분";

        // when
        AiResult<List<AiExtractedMenu>> result = fakeAiClient.extractMenus(text, recipes);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.content()).containsExactly(
            new AiExtractedMenu("김치찌개", List.of(1L), 3),
            new AiExtractedMenu("크림 파스타", List.of(3L), 3));
        assertThat(result.usage().model()).isEqualTo(FakeAiClient.MODEL);
    }

    @Test
    @DisplayName("인분을 말하지 않으면 인분은 비워 둔다")
    void extractMenus_leavesServingsEmptyWhenNotGiven() {
        // given
        String text = "된장찌개 먹고 싶어";

        // when
        AiResult<List<AiExtractedMenu>> result = fakeAiClient.extractMenus(text, recipes);

        // then
        assertThat(result.content()).containsExactly(new AiExtractedMenu("된장찌개", List.of(2L), null));
    }

    @Test
    @DisplayName("맞는 레시피가 없으면 빈 목록을 성공으로 돌려준다")
    void extractMenus_returnsEmptyWhenNothingMatches() {
        // given
        String text = "아무거나 맛있는 거";

        // when
        AiResult<List<AiExtractedMenu>> result = fakeAiClient.extractMenus(text, recipes);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("장애 문구가 들어오면 AI 장애처럼 실패를 돌려준다")
    void extractMenus_failsWhenTriggerGiven() {
        // given
        String text = "김치찌개 " + FakeAiClient.FAILURE_TRIGGER;

        // when
        AiResult<List<AiExtractedMenu>> result = fakeAiClient.extractMenus(text, recipes);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureType()).isEqualTo(AiFailureType.UNAVAILABLE);
        assertThat(result.content()).isNull();
        assertThat(result.usage().inputTokens()).isNull();
    }
}
