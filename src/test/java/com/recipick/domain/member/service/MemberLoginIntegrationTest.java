package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.infra.redis.RedisLoginAttemptStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class MemberLoginIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Abcd1234!";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedisLoginAttemptStore attemptStore;
    @Autowired
    private SecretKey jwtSigningKey;

    private Member member;
    private String email;

    @BeforeEach
    void setUp() {
        email = UUID.randomUUID() + "@test.com";
        member = memberRepository.saveAndFlush(Member.builder().email(email).name("로그인검증")
                .phone(UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                .passwordHash(passwordEncoder.encode(PASSWORD)).build());
    }

    @AfterEach
    void tearDown() {
        memberRepository.deleteById(member.getId());
    }

    @Test
    @DisplayName("로그인하면 회원 토큰과 안전한 쿠키를 발급하고 Redis에 해시와 14일 TTL을 저장한다")
    void issuesTokensAndStoresRefreshHash() throws Exception {
        // given
        String normalizedEmail = "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ";

        // when
        MockHttpServletResponse response = login(normalizedEmail, PASSWORD);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("OK");
        JsonNode data = body.path("data");
        assertThat(data.path("memberId").asLong()).isEqualTo(member.getId());
        assertThat(data.path("accessTokenExpiresIn").asLong()).isEqualTo(1800);
        assertThat(data.has("refreshToken")).isFalse();
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=1209600");
        String refreshToken = cookie.substring("refreshToken=".length(), cookie.indexOf(';'));
        Claims access = claims(data.path("accessToken").asText());
        Claims refresh = claims(refreshToken);
        assertThat(access.getSubject()).isEqualTo(member.getId().toString());
        assertThat(access.get("type", String.class)).isEqualTo("MEMBER");
        assertThat(access.get("tokenUse", String.class)).isEqualTo("ACCESS");
        assertThat(refresh.get("tokenUse", String.class)).isEqualTo("REFRESH");
        assertThat(refresh.getId()).isNotEqualTo(access.getId());
        assertThat(access.getExpiration().getTime() - access.getIssuedAt().getTime()).isEqualTo(1800000);
        assertThat(refresh.getExpiration().getTime() - refresh.getIssuedAt().getTime()).isEqualTo(1209600000);
        String key = "auth:refresh:MEMBER:" + member.getId() + ":" + refresh.getId();
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(expectedHash).isNotEqualTo(refreshToken);
        assertThat(redisTemplate.getExpire(key)).isBetween(1209590L, 1209600L);
    }

    @Test
    @DisplayName("없는 이메일과 잘못된 비밀번호는 같은 오류를 반환하고 쿠키를 발급하지 않는다")
    void hidesEmailExistence() throws Exception {
        // given
        String missingEmail = UUID.randomUUID() + "@missing.com";

        // when
        MockHttpServletResponse missing = login(missingEmail, PASSWORD);
        MockHttpServletResponse incorrect = login(email, "wrong");

        // then
        assertError(missing, 401, "AUTH-007");
        assertError(incorrect, 401, "AUTH-007");
        assertThat(missing.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(incorrect.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("세 번째 실패부터 10분 잠기고 올바른 비밀번호도 거부한다")
    void locksAfterThirdFailure() throws Exception {
        // given
        login(email, "wrong");
        login(email, "wrong");

        // when
        MockHttpServletResponse third = login(email, "wrong");
        MockHttpServletResponse correct = login(email, PASSWORD);

        // then
        assertError(third, 403, "AUTH-012");
        assertError(correct, 403, "AUTH-012");
        assertThat(redisTemplate.getExpire(attemptKey("MEMBER", email))).isBetween(590L, 600L);
    }

    @Test
    @DisplayName("성공 시 실패 횟수를 초기화하고 다음 실패는 첫 번째 실패로 센다")
    void resetsFailuresOnSuccess() throws Exception {
        // given
        login(email, "wrong");
        login(email, "wrong");

        // when
        MockHttpServletResponse success = login(email, PASSWORD);
        MockHttpServletResponse failure = login(email, "wrong");

        // then
        assertThat(success.getStatus()).isEqualTo(200);
        assertError(failure, 401, "AUTH-007");
        assertThat(redisTemplate.opsForHash().get(attemptKey("MEMBER", email), "failures")).isEqualTo("1");
    }

    @Test
    @DisplayName("잠금 TTL이 만료되면 다시 로그인할 수 있다")
    void allowsLoginAfterLockExpires() throws Exception {
        // given
        attemptStore.recordFailure("MEMBER", email, 1, Duration.ofMinutes(10), Duration.ofSeconds(1));
        assertThat(attemptStore.isLocked("MEMBER", email)).isTrue();

        // when
        await().atMost(Duration.ofSeconds(5)).until(() -> !attemptStore.isLocked("MEMBER", email));
        MockHttpServletResponse response = login(email, PASSWORD);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("회원과 관리자 로그인 제한은 같은 아이디여도 서로 영향을 주지 않는다")
    void isolatesAdminAttempts() throws Exception {
        // given
        attemptStore.recordFailure("ADMIN", email, 1, Duration.ofMinutes(10), Duration.ofMinutes(10));

        // when
        MockHttpServletResponse response = login(email, PASSWORD);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(attemptStore.isLocked("ADMIN", email)).isTrue();
    }

    @Test
    @DisplayName("동시 실패 요청에서도 횟수가 누락되지 않고 세 번째 실패부터 잠긴다")
    void countsConcurrentFailuresAtomically() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return login(email, "wrong").getStatus();
                }));
            }

            // when
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(15, TimeUnit.SECONDS));
            }

            // then
            assertThat(statuses).containsOnly(401, 403);
            assertThat(statuses.stream().filter(status -> status == 401).count()).isEqualTo(2);
            assertThat(attemptStore.isLocked("MEMBER", email)).isTrue();
            assertThat(redisTemplate.opsForHash().get(attemptKey("MEMBER", email), "failures")).isEqualTo("3");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("필수 값이 비어 있으면 입력 오류이며 Redis에 실패 횟수를 기록하지 않는다")
    void validatesRequiredInput() throws Exception {
        // given
        String blankPassword = " ";

        // when
        MockHttpServletResponse response = login(email, blankPassword);

        // then
        assertError(response, 400, "COMMON-001");
        assertThat(redisTemplate.hasKey(attemptKey("MEMBER", email))).isFalse();
    }

    @Test
    @DisplayName("DB가 같은 회원으로 비교하는 이메일 표기는 실패 횟수와 잠금을 공유한다")
    void sharesLockForEquivalentEmails() throws Exception {
        // given
        String alias = email.replace("@test.com", "@tést.com");
        assertThat(memberRepository.findByEmailIgnoreCase(alias)).isPresent();
        login(email, "wrong");
        login(alias, "wrong");

        // when
        MockHttpServletResponse third = login(alias, "wrong");
        MockHttpServletResponse original = login(email, PASSWORD);
        MockHttpServletResponse accented = login(alias, PASSWORD);

        // then
        assertError(third, 403, "AUTH-012");
        assertError(original, 403, "AUTH-012");
        assertError(accented, 403, "AUTH-012");
        assertThat(redisTemplate.opsForHash().get(attemptKey("MEMBER", email), "failures")).isEqualTo("3");
        assertThat(redisTemplate.hasKey(attemptKey("MEMBER", alias))).isFalse();
    }

    @Test
    @DisplayName("다른 이메일 표기로 로그인에 성공해도 저장된 이메일의 실패 횟수를 초기화한다")
    void clearsCanonicalFailuresOnAliasSuccess() throws Exception {
        // given
        String alias = email.replace("@test.com", "@tést.com");
        login(email, "wrong");

        // when
        MockHttpServletResponse success = login(alias, PASSWORD);

        // then
        assertThat(success.getStatus()).isEqualTo(200);
        assertThat(redisTemplate.hasKey(attemptKey("MEMBER", email))).isFalse();
    }

    private MockHttpServletResponse login(String loginEmail, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", loginEmail, "password", password))))
                .andReturn().getResponse();
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(jwtSigningKey).build().parseSignedClaims(token).getPayload();
    }

    private String attemptKey(String type, String loginEmail) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(loginEmail.getBytes(StandardCharsets.UTF_8));
        return "auth:login:" + type + ":" + HexFormat.of().formatHex(hash);
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(objectMapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo(code);
    }
}
