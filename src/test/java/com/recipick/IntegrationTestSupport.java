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
public abstract class IntegrationTestSupport {

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
