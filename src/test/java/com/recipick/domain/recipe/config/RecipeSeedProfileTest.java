package com.recipick.domain.recipe.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RecipeSeedProfileTest {

    @ParameterizedTest
    @ValueSource(strings = {"local"})
    @DisplayName("로컬은 기존 SQL 다음에 레시피 초기 데이터 SQL을 실행하도록 설정된다")
    void includesRecipeSeedInLocal(String profile) {
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
            assertThat(context.getEnvironment().getProperty("spring.sql.init.data-locations[2]"))
                .isEqualTo("optional:classpath:data-recipe-local.sql");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "prod", "production", "local,test", "local,prod"})
    @DisplayName("테스트·운영 환경에서는 레시피 초기 데이터 SQL을 추가하지 않는다")
    void excludesRecipeSeedOutsideLocal(String profile) {
        // given
        ApplicationContextRunner runner = runner(profile);

        // when
        runner.run(context -> {
            // then
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.sql.init.data-locations[2]")).isNull();
        });
    }

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
            .withPropertyValues("spring.config.location=classpath:/application.yml",
                "spring.profiles.active=" + profile)
            .withInitializer(new ConfigDataApplicationContextInitializer());
    }
}
