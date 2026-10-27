package com.recipick.domain.member.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class TrialBlockConfigTest {
    @TempDir Path directory;

    @Test
    @DisplayName("운영에서 해시 키가 없으면 시작을 거절한다")
    void requiresProductionSecret() {
        // given
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        // when, then
        assertThatThrownBy(() -> new TrialBlockConfig().trialPhoneHasher(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("로컬 키는 재시작 후에도 같은 해시를 만든다")
    void persistsLocalKey() throws Exception {
        // given
        MockEnvironment environment = new MockEnvironment()
                .withProperty("security.trial-block.local-key-path", directory.resolve("trial-key").toString());
        environment.setActiveProfiles("local");
        TrialBlockConfig config = new TrialBlockConfig();
        // when
        String first = config.trialPhoneHasher(environment).hash("01012345678");
        String second = config.trialPhoneHasher(environment).hash("01012345678");
        // then
        assertThat(first).isEqualTo(second);
    }
}
