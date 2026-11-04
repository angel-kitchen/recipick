package com.recipick.domain.member.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MemberDemoSeedProfileTest {
    @ParameterizedTest
    @CsvSource({"local,4", "prod,1"})
    @DisplayName("로컬과 운영 시연 프로필은 공용 회원 시드를 실행한다")
    void includesDemoSeed(String profile, int position) {
        runner(profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.sql.init.data-locations[" + position + "]"))
                    .isEqualTo("classpath:data-member-demo.sql");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "production", "staging"})
    @DisplayName("자동 테스트와 다른 프로필에는 시연 회원을 추가하지 않는다")
    void excludesDemoSeedFromOtherProfiles(String profile) {
        runner(profile).run(context -> {
            assertThat(context).hasNotFailed();
            for (int index = 0; index < 5; index++) {
                assertThat(context.getEnvironment().getProperty("spring.sql.init.data-locations[" + index + "]"))
                        .isNotEqualTo("classpath:data-member-demo.sql");
            }
        });
    }

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.config.location=classpath:/application.yml", "spring.profiles.active=" + profile)
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }
}
