package com.recipick.infra.ai.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.Usage;
import com.recipick.infra.ai.claude.ClaudeAiClient.MenuOutput;
import com.recipick.infra.ai.dto.AiExtractedMenu;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiRecipeOption;
import com.recipick.infra.ai.dto.AiResult;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class ClaudeAiClientTest {

    private final AnthropicClient client = mock(AnthropicClient.class, Answers.RETURNS_DEEP_STUBS);
    private final ClaudeAiClient claudeAiClient = new ClaudeAiClient(
        client, new ClaudeProperties("test-key", "claude-opus-5-5", Duration.ofSeconds(15), 2, 4096));

    @Test
    @DisplayName("응답이 최대 토큰에서 잘리면 메뉴를 읽지 않고 실패로 돌려준다")
    @SuppressWarnings("unchecked")
    void extractMenus_failsWhenStoppedAtMaxTokens() {
        // given
        StructuredMessage<MenuOutput> message = mock(StructuredMessage.class);
        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(800L);
        when(usage.outputTokens()).thenReturn(4096L);
        when(usage.cacheReadInputTokens()).thenReturn(Optional.empty());
        when(message.usage()).thenReturn(usage);
        when(message.model()).thenReturn(Model.of("claude-opus-5-5"));
        when(message.stopReason()).thenReturn(Optional.of(StopReason.MAX_TOKENS));
        // 생각에 토큰을 다 쓰면 응답 블록이 없어, 잘린 것을 확인하지 않으면 빈 목록이 성공처럼 보인다
        when(message.content()).thenReturn(List.of());
        when(client.messages().create(any(StructuredMessageCreateParams.class))).thenReturn(message);

        // when
        AiResult<List<AiExtractedMenu>> result = claudeAiClient.extractMenus(
            "김치찌개 2인분", List.of(new AiRecipeOption(1L, "김치찌개")));

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureType()).isEqualTo(AiFailureType.INVALID_RESPONSE);
        assertThat(result.usage().outputTokens()).isEqualTo(4096);
    }
}
