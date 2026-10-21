package com.recipick.domain.member.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminSeedProfileTest {

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    @DisplayName("로컬과 테스트는 기존 SQL 다음에 관리자 SQL을 실행하도록 설정된다")
    void includesAdminSeedInDevelopment(String profile) {
        // given
        ApplicationContextRunner runner = runner(profile);

        // when
        runner.run(context -> {
            // then
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.sql.init.data-locations[0]"))
                    .isEqualTo("classpath:data.sql");
            assertThat(context.getEnvironment().getProperty("spring.sql.init.data-locations[1]"))
                    .isEqualTo("classpath:data-admin-local.sql");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production", "staging", "local,prod", "test,production"})
    @DisplayName("운영과 그 외 환경에서는 테스트 관리자 SQL을 추가하지 않는다")
    void excludesAdminSeedOutsideDevelopment(String profile) {
        // given
        ApplicationContextRunner runner = runner(profile);

        // when
        runner.run(context -> {
            // then
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.sql.init.data-locations[1]")).isNull();
            assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("always");
        });
    }

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.config.location=classpath:/application.yml",
                        "spring.profiles.active=" + profile)
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }
}
