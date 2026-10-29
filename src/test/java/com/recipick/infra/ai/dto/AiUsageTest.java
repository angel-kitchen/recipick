package com.recipick.infra.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiUsageTest {

    @Test
    @DisplayName("두 호출의 토큰과 응답 시간을 더한다")
    void plus_sumsTokensAndLatency() {
        // given
        AiUsage first = new AiUsage("claude-opus-5-5", 300, 50, 0, 800);
        AiUsage second = new AiUsage("claude-opus-5-5", 900, 200, 100, 1200);

        // when
        AiUsage sum = first.plus(second);

        // then
        assertThat(sum).isEqualTo(new AiUsage("claude-opus-5-5", 1200, 250, 100, 2000));
    }

    @Test
    @DisplayName("응답을 받지 못한 호출은 토큰을 더하지 않고 응답 시간만 더한다")
    void plus_keepsReceivedTokensWhenOtherFailed() {
        // given
        AiUsage first = new AiUsage("claude-opus-5-5", 300, 50, 0, 800);
        AiUsage failed = AiUsage.failed("claude-opus-5-5", 15000);

        // when
        AiUsage sum = first.plus(failed);

        // then
        assertThat(sum).isEqualTo(new AiUsage("claude-opus-5-5", 300, 50, 0, 15800));
    }
}
