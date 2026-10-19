package com.recipick.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.Duration;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(JwtConfig.class)
            .withPropertyValues(
                    "security.jwt.access-token-expiration=30m",
                    "security.jwt.refresh-token-expiration=14d"
            );

    @Test
    void bindsDurationsAndLoadsSigningKey() {
        SecretKey key = Jwts.SIG.HS256.key().build();
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

    @TempDir
    Path temporaryDirectory;

    @Test
    void reusesLocalKeyAcrossContexts() {
        String[] token = new String[1];
        ApplicationContextRunner localRunner = contextRunner.withPropertyValues(
                "spring.profiles.active=local",
                "security.jwt.local-key-path=" + temporaryDirectory.resolve("keys/jwt-secret")
        );
        localRunner.run(context -> {
            assertThat(context).hasNotFailed();
            token[0] = Jwts.builder().subject("1")
                    .signWith(context.getBean("jwtSigningKey", SecretKey.class)).compact();
        });
        localRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SecretKey key = context.getBean("jwtSigningKey", SecretKey.class);
            assertThat(Jwts.parser().verifyWith(key).build().parseSignedClaims(token[0])
                    .getPayload().getSubject()).isEqualTo("1");
        });
    }

    @Test
    void rejectsMissingProductionSecret() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsLocalFallbackWhenProductionIsAlsoActive() {
        contextRunner.withPropertyValues("spring.profiles.active=local,prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingSecret() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsWeakSigningKey() {
        contextRunner.withPropertyValues("security.jwt.secret=" + Encoders.BASE64.encode(new byte[16]))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidBase64() {
        contextRunner.withPropertyValues("security.jwt.secret=%%%")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveExpiration() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        contextRunner.withPropertyValues(
                        "security.jwt.secret=" + Encoders.BASE64.encode(key.getEncoded()),
                        "security.jwt.access-token-expiration=0s"
                )
                .run(context -> assertThat(context).hasFailed());
    }
}
