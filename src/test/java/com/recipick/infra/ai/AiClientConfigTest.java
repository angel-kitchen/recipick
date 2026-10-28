package com.recipick.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.infra.ai.claude.ClaudeAiClient;
import com.recipick.infra.ai.claude.ClaudeClientConfig;
import com.recipick.infra.ai.claude.ClaudeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// 설정 값 하나로 가짜·실제 클라이언트가 바뀌는지만 보면 되므로, DB를 띄우지 않고 AI 관련 빈만 올린다
class AiClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(FakeAiClient.class, ClaudeClientConfig.class, ClaudeAiClient.class);

    @Test
    @DisplayName("ai.client를 정하지 않으면 가짜 클라이언트를 쓴다")
    void usesFakeClientByDefault() {
        contextRunner.run(context -> {
            // then
            assertThat(context).hasSingleBean(AiClient.class);
            assertThat(context.getBean(AiClient.class)).isInstanceOf(FakeAiClient.class);
        });
    }

    @Test
    @DisplayName("ai.client가 claude면 Claude 클라이언트를 쓴다")
    void usesClaudeClientWhenSelected() {
        contextRunner
            .withPropertyValues("ai.client=claude", "ai.claude.api-key=test-key")
            .run(context -> {
                // then
                assertThat(context).hasSingleBean(AiClient.class);
                assertThat(context.getBean(AiClient.class)).isInstanceOf(ClaudeAiClient.class);
                assertThat(context.getBean(ClaudeProperties.class).model()).isEqualTo("claude-opus-5-5");
            });
    }

    @Test
    @DisplayName("claude를 골랐는데 API 키가 없으면 앱이 뜨지 않는다")
    void failsToStartWithoutApiKey() {
        contextRunner
            .withPropertyValues("ai.client=claude", "ai.claude.api-key=")
            .run(context -> {
                // then
                assertThat(context).hasFailed();
            });
    }
}
