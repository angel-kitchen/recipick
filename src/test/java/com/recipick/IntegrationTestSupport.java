package com.recipick;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(IntegrationTestSupport.AuthFixtureConfig.class)
public abstract class IntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private com.recipick.global.security.JwtTokenProvider fixtureTokens;
    @org.springframework.beans.factory.annotation.Autowired
    private com.recipick.infra.redis.RedisRefreshTokenStore fixtureSessions;

    protected com.recipick.global.security.JwtTokenPair testMemberTokens(Long id) {
        return testMemberTokens(id, 0);
    }

    protected com.recipick.global.security.JwtTokenPair testMemberTokens(Long id, long version) {
        var pair = fixtureTokens.createMemberTokens(id, version);
        var principal = fixtureTokens.parseAccessToken(pair.accessToken());
        fixtureSessions.save("MEMBER", id, principal.sessionId(), pair.refreshToken(), pair.refreshExpiration());
        return pair;
    }

    protected com.recipick.global.security.JwtTokenPair testAdminTokens(
            com.recipick.global.security.AuthPrincipal principal) {
        var pair = fixtureTokens.createTokens(principal);
        var issued = fixtureTokens.parseAccessToken(pair.accessToken());
        fixtureSessions.save(principal.type().name(), principal.id(), issued.sessionId(),
                pair.refreshToken(), pair.refreshExpiration());
        return pair;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.core.StringRedisTemplate fixtureRedis;

    @org.junit.jupiter.api.BeforeEach
    void clearAuthenticationFixtures() {
        // 컨텍스트마다 DB ID가 재사용돼도 이전 테스트의 로그인 키가 섞이지 않는다.
        var keys = fixtureRedis.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            fixtureRedis.delete(keys);
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class AuthFixtureConfig {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer authCsrfFixture(
                org.springframework.security.web.csrf.CsrfTokenRepository repository) {
            // 실제 Cookie 저장소를 교체하지 않고 클라이언트의 Cookie·헤더만 준비한다.
            return builder -> builder.defaultRequest(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/").with(request -> {
                        var token = repository.generateToken(request);
                        var existing = request.getCookies() == null ? new jakarta.servlet.http.Cookie[0]
                                : request.getCookies();
                        var cookies = java.util.Arrays.copyOf(existing, existing.length + 1);
                        cookies[existing.length] = new jakarta.servlet.http.Cookie("authCsrfToken", token.getToken());
                        request.setCookies(cookies);
                        request.addHeader(token.getHeaderName(), token.getToken());
                        return request;
                    }));
        }
    }


    // 테스트 클래스마다 새로 띄우면 느려서 JVM 전체가 컨테이너 하나를 공유한다.
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-lock-wait-timeout=5");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @MockitoSpyBean
    protected PasswordEncoder testPasswordEncoder;

    private static final String TEST_SECRET = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
    // 빌링키 암호화 키는 다른 비밀값과 겹치지 않게 따로 만든다(HS256 키 = 32바이트 = AES-256)
    private static final String TEST_BILLING_KEY_SECRET =
        Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_SECRET);
        registry.add("security.trial-block.secret", () -> TEST_SECRET);
        registry.add("recipick.billing-key.encryption-secret", () -> TEST_BILLING_KEY_SECRET);
        registry.add("recipick.billing-key.hmac-secret", () -> TEST_SECRET);
    }
}
