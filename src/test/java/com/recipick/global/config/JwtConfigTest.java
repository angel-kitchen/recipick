package com.recipick.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.nio.file.Path;
import java.time.Duration;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigTest {

    @TempDir
    Path temporaryDirectory;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(JwtConfig.class)
            .withPropertyValues(
                    "security.jwt.access-token-expiration=30m",
                    "security.jwt.refresh-token-expiration=14d"
            );

    @Test
    @DisplayName("만료시간과 서명 키 설정을 읽어 토큰을 검증한다")
    void bindsDurationsAndLoadsSigningKey() {
        // given
        SecretKey key = Jwts.SIG.HS256.key().build();
        // when / then
        contextRunner.withPropertyValues("security.jwt.secret=" + Encoders.BASE64.encode(key.getEncoded()))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.accessTokenExpiration()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(properties.refreshTokenExpiration()).isEqualTo(Duration.ofDays(14));
                    SecretKey signingKey = context.getBean("jwtSigningKey", SecretKey.class);
                    String token = Jwts.builder().subject("1").signWith(signingKey).compact();
                    assertThat(Jwts.parser().verifyWith(signingKey).build()
                            .parseSignedClaims(token).getPayload().getSubject()).isEqualTo("1");
                });
    }

    @Test
    @DisplayName("서버 컨텍스트가 바뀌어도 로컬 키를 재사용한다")
    void reusesLocalKeyAcrossContexts() {
        // given
        String[] token = new String[1];
        ApplicationContextRunner localRunner = contextRunner.withPropertyValues(
                "spring.profiles.active=local",
                "security.jwt.local-key-path=" + temporaryDirectory.resolve("keys/jwt-secret")
        );
        // when / then
        localRunner.run(context -> {
            assertThat(context).hasNotFailed();
            token[0] = Jwts.builder().subject("1")
                    .signWith(context.getBean("jwtSigningKey", SecretKey.class)).compact();
        });
        // when / then
        localRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SecretKey key = context.getBean("jwtSigningKey", SecretKey.class);
            assertThat(Jwts.parser().verifyWith(key).build().parseSignedClaims(token[0])
                    .getPayload().getSubject()).isEqualTo("1");
        });
    }

    @Test
    @DisplayName("배포 환경에 키가 없으면 기동을 거부한다")
    void rejectsMissingProductionSecret() {
        // when / then
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("배포 프로필이 함께 활성화되면 키 자동 생성을 거부한다")
    void rejectsLocalFallbackWhenProductionIsAlsoActive() {
        // when / then
        contextRunner.withPropertyValues("spring.profiles.active=local,prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("로컬 외 환경에 키가 없으면 기동을 거부한다")
    void rejectsMissingSecret() {
        // when / then
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("짧은 서명 키로는 기동하지 않는다")
    void rejectsWeakSigningKey() {
        // when / then
        contextRunner.withPropertyValues("security.jwt.secret=" + Encoders.BASE64.encode(new byte[16]))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Base64 형식이 아닌 키는 거부한다")
    void rejectsInvalidBase64() {
        // when / then
        contextRunner.withPropertyValues("security.jwt.secret=%%%")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("토큰 유효기간이 양수가 아니면 거부한다")
    void rejectsNonPositiveExpiration() {
        // given
        SecretKey key = Jwts.SIG.HS256.key().build();
        // when / then
        contextRunner.withPropertyValues(
                        "security.jwt.secret=" + Encoders.BASE64.encode(key.getEncoded()),
                        "security.jwt.access-token-expiration=0s"
                )
                .run(context -> assertThat(context).hasFailed());
    }
}
