package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.redis.RedisLoginAttemptStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class AdminLoginIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Abcd1234!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private AdminRepository admins;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private RedisLoginAttemptStore attempts;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private SecretKey jwtSigningKey;
    @Autowired
    private JdbcTemplate jdbc;

    private Admin admin;
    private String loginId;

    @BeforeEach
    void setUp() {
        loginId = "admin-" + UUID.randomUUID();
        admin = admins.saveAndFlush(Admin.builder().loginId(loginId).name("관리자로그인검증")
                .passwordHash(encoder.encode(PASSWORD)).role(AdminRole.SUPER_ADMIN).active(true).build());
    }

    @AfterEach
    void tearDown() {
        admins.deleteById(admin.getId());
    }

    @ParameterizedTest
    @CsvSource({"SUPER_ADMIN,", "MART_ADMIN,1"})
    @DisplayName("관리자 로그인은 역할·마트 범위를 포함한 토큰과 14일 쿠키를 발급한다")
    void issuesAdminTokens(String role, Long martId) throws Exception {
        // given
        jdbc.update("UPDATE admin SET role = ?, mart_id = ? WHERE id = ?", role, martId, admin.getId());
        String normalizedId = "  " + loginId.toUpperCase(java.util.Locale.ROOT) + "  ";

        // when
        MockHttpServletResponse response = login(normalizedId, PASSWORD);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
        assertThat(data.path("adminId").asLong()).isEqualTo(admin.getId());
        assertThat(data.path("role").asText()).isEqualTo(role);
        assertThat(data.path("accessTokenExpiresIn").asLong()).isEqualTo(1800);
        if (martId == null) {
            assertThat(data.path("martId").isNull()).isTrue();
        } else {
            assertThat(data.path("martId").asLong()).isEqualTo(martId);
        }
        assertThat(data.has("refreshToken")).isFalse();
        String access = data.path("accessToken").asText();
        AuthPrincipal principal = tokens.parseAccessToken(access);
        assertThat(principal.type()).isEqualTo(SubjectType.ADMIN);
        assertThat(principal.id()).isEqualTo(admin.getId());
        assertThat(principal.role()).isEqualTo(role);
        assertThat(principal.martId()).isEqualTo(martId);
        Claims accessClaims = claims(access);
        assertThat(accessClaims.get("adminId", Long.class)).isEqualTo(admin.getId());
        String refresh = refreshCookie(response);
        Claims refreshClaims = claims(refresh);
        assertThat(refreshClaims.get("tokenUse", String.class)).isEqualTo("REFRESH");
        assertThat(refreshClaims.get("sid", String.class)).isEqualTo(principal.sessionId());
        String key = "auth:refresh:ADMIN:" + admin.getId() + ":" + principal.sessionId();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(refresh.getBytes(StandardCharsets.UTF_8)));
        assertThat(redis.opsForValue().get(key)).isEqualTo(hash).isNotEqualTo(refresh);
        assertThat(redis.getExpire(key)).isBetween(1209590L, 1209600L);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=1209600");
    }

    @Test
    @DisplayName("없는 관리자와 비밀번호 불일치는 같은 AUTH-008 응답을 반환한다")
    void hidesAdminExistence() throws Exception {
        // given
        String missingId = UUID.randomUUID().toString();

        // when
        MockHttpServletResponse missing = login(missingId, PASSWORD);
        MockHttpServletResponse incorrect = login(loginId, "wrong");

        // then
        assertError(missing, 401, "AUTH-008");
        assertError(incorrect, 401, "AUTH-008");
    }

    @Test
    @DisplayName("비활성 관리자는 비밀번호가 맞아도 거절하고 토큰을 발급하지 않는다")
    void rejectsInactiveAdmin() throws Exception {
        // given
        jdbc.update("UPDATE admin SET active = false WHERE id = ?", admin.getId());

        // when
        MockHttpServletResponse correct = login(loginId, PASSWORD);
        MockHttpServletResponse incorrect = login(loginId, "wrong");

        // then
        assertError(correct, 403, "AUTH-009");
        assertError(incorrect, 401, "AUTH-008");
        assertThat(redis.keys("auth:refresh:ADMIN:" + admin.getId() + ":*")).isEmpty();
    }

    @Test
    @DisplayName("대소문자를 바꿔 요청해도 동일 관리자 계정의 세 번째 실패부터 10분 잠긴다")
    void locksAfterThreeFailures() throws Exception {
        // given
        login(loginId, "wrong");
        login(loginId.toUpperCase(java.util.Locale.ROOT), "wrong");

        // when
        MockHttpServletResponse third = login(loginId, "wrong");
        MockHttpServletResponse correct = login(loginId, PASSWORD);

        // then
        assertError(third, 403, "AUTH-012");
        assertError(correct, 403, "AUTH-012");
        assertThat(redis.getExpire(attemptKey())).isBetween(590L, 600L);
    }

    @Test
    @DisplayName("DB가 같은 계정으로 보는 악센트 표기도 동일한 잠금 횟수로 합산한다")
    void usesCanonicalStoredLoginIdForAttempts() throws Exception {
        // given
        String accentedId = loginId.replace("admin-", "ádmin-");
        assertThat(admins.findByLoginIdIgnoreCase(accentedId)).isPresent();
        login(accentedId, "wrong");
        login(loginId, "wrong");

        // when
        MockHttpServletResponse third = login(accentedId, "wrong");
        MockHttpServletResponse correct = login(loginId, PASSWORD);

        // then
        assertError(third, 403, "AUTH-012");
        assertError(correct, 403, "AUTH-012");
        assertThat(redis.opsForHash().get(attemptKey(), "failures")).isEqualTo("3");
    }

    @Test
    @DisplayName("관리자 로그인 성공 시 실패 횟수를 초기화한다")
    void resetsFailuresOnSuccess() throws Exception {
        // given
        login(loginId, "wrong");
        login(loginId, "wrong");

        // when
        MockHttpServletResponse success = login(loginId, PASSWORD);
        MockHttpServletResponse failure = login(loginId, "wrong");

        // then
        assertThat(success.getStatus()).isEqualTo(200);
        assertError(failure, 401, "AUTH-008");
        assertThat(redis.opsForHash().get(attemptKey(), "failures")).isEqualTo("1");
    }

    @Test
    @DisplayName("회원 로그인 제한은 같은 식별자를 쓰는 관리자 로그인에 영향을 주지 않는다")
    void isolatesMemberAttempts() throws Exception {
        // given
        attempts.recordFailure("MEMBER", loginId, 1, Duration.ofMinutes(10), Duration.ofMinutes(10));

        // when
        MockHttpServletResponse response = login(loginId, PASSWORD);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(attempts.isLocked("MEMBER", loginId)).isTrue();
    }

    @Test
    @DisplayName("관리자 잠금 TTL이 만료되면 다시 로그인할 수 있다")
    void allowsLoginAfterLockExpires() throws Exception {
        // given
        attempts.recordFailure("ADMIN", loginId, 1, Duration.ofMinutes(10), Duration.ofSeconds(1));
        assertThat(attempts.isLocked("ADMIN", loginId)).isTrue();

        // when
        await().atMost(Duration.ofSeconds(5)).until(() -> !attempts.isLocked("ADMIN", loginId));
        MockHttpServletResponse response = login(loginId, PASSWORD);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("관리자 동시 실패 요청에서도 세 번째부터 잠기고 실패 횟수가 누락되지 않는다")
    void countsConcurrentFailures() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return login(loginId, "wrong");
                }));
            }

            // when
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                statuses.add(future.get(15, TimeUnit.SECONDS).getStatus());
            }

            // then
            assertThat(statuses).containsOnly(401, 403);
            assertThat(statuses.stream().filter(status -> status == 401).count()).isEqualTo(2);
            assertThat(redis.opsForHash().get(attemptKey(), "failures")).isEqualTo("3");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("관리자 로그인으로 발급한 토큰은 공통 재발급·로그아웃 API에서 사용할 수 있다")
    void connectsToReissueAndLogout() throws Exception {
        // given
        MockHttpServletResponse loggedIn = login(loginId, PASSWORD);
        String originalRefresh = refreshCookie(loggedIn);

        // when
        MockHttpServletResponse reissued = mockMvc.perform(post("/api/auth/reissue")
                .cookie(new Cookie("refreshToken", originalRefresh))).andReturn().getResponse();
        String access = mapper.readTree(reissued.getContentAsString()).path("data").path("accessToken").asText();
        MockHttpServletResponse loggedOut = mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .cookie(new Cookie("refreshToken", refreshCookie(reissued)))).andReturn().getResponse();

        // then
        assertThat(reissued.getStatus()).isEqualTo(200);
        assertThat(loggedOut.getStatus()).isEqualTo(204);
        MockHttpServletResponse afterLogout = mockMvc.perform(post("/api/auth/reissue")
                .cookie(new Cookie("refreshToken", refreshCookie(reissued)))).andReturn().getResponse();
        assertError(afterLogout, 401, "AUTH-011");
    }

    @Test
    @DisplayName("로그인한 마트 관리자도 슈퍼 관리자 전용 API를 사용할 수 없다")
    void enforcesSuperAdminOnlyRoute() throws Exception {
        // given
        jdbc.update("UPDATE admin SET role = 'MART_ADMIN', mart_id = 1 WHERE id = ?", admin.getId());
        MockHttpServletResponse loggedIn = login(loginId, PASSWORD);
        String access = mapper.readTree(loggedIn.getContentAsString()).path("data").path("accessToken").asText();

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "관리자권한검증")))).andReturn().getResponse();

        // then
        assertError(response, 403, "AUTH-006");
    }

    @Test
    @DisplayName("필수 로그인 값 누락과 아이디 길이 초과는 입력 오류다")
    void validatesInput() throws Exception {
        // given
        String tooLongId = "a".repeat(51);

        // when
        MockHttpServletResponse blankId = login(" ", PASSWORD);
        MockHttpServletResponse blankPassword = login(loginId, " ");
        MockHttpServletResponse longId = login(tooLongId, PASSWORD);

        // then
        assertError(blankId, 400, "COMMON-001");
        assertError(blankPassword, 400, "COMMON-001");
        assertError(longId, 400, "COMMON-001");
        assertThat(redis.hasKey(attemptKey())).isFalse();
    }

    private MockHttpServletResponse login(String id, String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("loginId", id, "password", password))))
                .andReturn().getResponse();
    }

    private String refreshCookie(MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith("refreshToken=");
        return header.substring("refreshToken=".length(), header.indexOf(';'));
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(jwtSigningKey).build().parseSignedClaims(token).getPayload();
    }

    private String attemptKey() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(loginId.getBytes(StandardCharsets.UTF_8));
        return "auth:login:ADMIN:" + HexFormat.of().formatHex(hash);
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo(code);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
