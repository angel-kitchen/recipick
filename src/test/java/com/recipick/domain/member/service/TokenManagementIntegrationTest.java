package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenPair;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class TokenManagementIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private AdminRepository admins;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private RedisRefreshTokenStore store;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SecretKey jwtSigningKey;

    private Member member;
    private JwtTokenPair original;
    private final List<Long> adminIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        member = members.saveAndFlush(Member.builder().email(unique + "@test.com").name("토큰검증")
                .phone(unique.replace("-", "").substring(0, 20)).build());
        original = startSession(new AuthPrincipal(SubjectType.MEMBER, member.getId(), null, null));
    }

    @AfterEach
    void tearDown() {
        members.deleteById(member.getId());
        adminIds.forEach(admins::deleteById);
    }

    @Test
    @DisplayName("재발급은 세션을 유지하며 토큰을 교체하고 이전 토큰 재사용을 거절한다")
    void rotatesRefreshToken() throws Exception {
        // given
        String originalSession = original.refreshTokenId();

        // when
        MockHttpServletResponse response = reissue(original.refreshToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
        assertThat(data.path("accessTokenExpiresIn").asLong()).isEqualTo(1800);
        assertThat(data.has("refreshToken")).isFalse();
        String refresh = refreshCookie(response);
        assertThat(refresh).isNotEqualTo(original.refreshToken());
        assertThat(claims(refresh).getId()).isNotEqualTo(original.refreshTokenId());
        assertThat(claims(refresh).get("sid", String.class)).isEqualTo(originalSession);
        assertThat(tokens.parseAccessToken(data.path("accessToken").asText()).sessionId()).isEqualTo(originalSession);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=1209600");
        assertThat(redis.getExpire(sessionKey())).isBetween(1209590L, 1209600L);
        assertError(reissue(original.refreshToken()), 401, "AUTH-011");
        assertThat(reissue(refresh).getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "1.2.3"})
    @DisplayName("리프레시 쿠키가 없거나 잘못되면 AUTH-011을 반환한다")
    void rejectsInvalidCookie(String value) throws Exception {
        // given
        String cookie = value;

        // when
        MockHttpServletResponse response = reissue(cookie);

        // then
        assertError(response, 401, "AUTH-011");
    }

    @Test
    @DisplayName("액세스 토큰을 리프레시 쿠키로 보내면 거절한다")
    void rejectsAccessTokenInCookie() throws Exception {
        // given
        String cookie = original.accessToken();

        // when
        MockHttpServletResponse response = reissue(cookie);

        // then
        assertError(response, 401, "AUTH-011");
    }

    @Test
    @DisplayName("만료된 리프레시 토큰은 AUTH-011을 반환한다")
    void rejectsExpiredRefresh() throws Exception {
        // given
        String cookie = legacyToken("REFRESH", UUID.randomUUID().toString(), Instant.now().minusSeconds(30));

        // when
        MockHttpServletResponse response = reissue(cookie);

        // then
        assertError(response, 401, "AUTH-011");
    }

    @Test
    @DisplayName("액세스 토큰이 만료되어도 리프레시 쿠키가 유효하면 재발급한다")
    void reissuesWithoutValidAccessToken() throws Exception {
        // given
        String access = legacyToken("ACCESS", UUID.randomUUID().toString(), Instant.now().minusSeconds(30));

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/reissue")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .cookie(new Cookie("refreshToken", original.refreshToken()))).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Redis에 없는 토큰은 서명이 정상이어도 거절한다")
    void rejectsMissingStoredToken() throws Exception {
        // given
        store.delete("MEMBER", member.getId(), original.refreshTokenId());

        // when
        MockHttpServletResponse response = reissue(original.refreshToken());

        // then
        assertError(response, 401, "AUTH-011");
    }

    @Test
    @DisplayName("Redis 해시와 다른 토큰은 거절하고 기존 값을 교체하지 않는다")
    void comparesStoredHash() throws Exception {
        // given
        redis.opsForValue().set(sessionKey(), "different-hash", Duration.ofMinutes(10));

        // when
        MockHttpServletResponse response = reissue(original.refreshToken());

        // then
        assertError(response, 401, "AUTH-011");
        assertThat(redis.opsForValue().get(sessionKey())).isEqualTo("different-hash");
    }

    @Test
    @DisplayName("Redis TTL이 만료되면 JWT 유효기간이 남아 있어도 재발급을 거절한다")
    void rejectsExpiredStoredSession() throws Exception {
        // given
        redis.expire(sessionKey(), Duration.ofMillis(100));

        // when
        await().atMost(Duration.ofSeconds(5)).until(() -> !Boolean.TRUE.equals(redis.hasKey(sessionKey())));
        MockHttpServletResponse response = reissue(original.refreshToken());

        // then
        assertError(response, 401, "AUTH-011");
    }

    @Test
    @DisplayName("로그아웃은 Redis 세션과 쿠키를 지우고 반복해도 204를 반환한다")
    void logsOutIdempotently() throws Exception {
        // given
        String access = original.accessToken();

        // when
        MockHttpServletResponse first = logout(access, original.refreshToken());
        MockHttpServletResponse second = logout(access, null);

        // then
        assertThat(first.getStatus()).isEqualTo(204);
        assertThat(first.getContentAsString()).isEmpty();
        assertThat(first.getHeader(HttpHeaders.SET_COOKIE))
                .contains("refreshToken=;", "Max-Age=0", "Path=/api/auth", "HttpOnly", "Secure", "SameSite=Strict");
        assertThat(second.getStatus()).isEqualTo(204);
        assertThat(redis.hasKey(sessionKey())).isFalse();
        assertError(reissue(original.refreshToken()), 401, "AUTH-011");
    }

    @Test
    @DisplayName("로그아웃은 리프레시 쿠키가 없어도 액세스 토큰의 세션을 폐기한다")
    void logsOutWithoutRefreshCookie() throws Exception {
        // given
        String access = original.accessToken();

        // when
        MockHttpServletResponse response = logout(access, null);

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertError(reissue(original.refreshToken()), 401, "AUTH-011");
    }

    @Test
    @DisplayName("로그인 인증 없이 로그아웃하면 세션을 지우지 않고 AUTH-001을 반환한다")
    void requiresAuthenticationForLogout() throws Exception {
        // given
        String cookie = original.refreshToken();

        // when
        MockHttpServletResponse response = logout(null, cookie);

        // then
        assertError(response, 401, "AUTH-001");
        assertThat(reissue(cookie).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("같은 회원의 다른 세션 쿠키를 보내도 현재 액세스 토큰의 세션만 폐기한다")
    void isolatesLoginSessions() throws Exception {
        // given
        JwtTokenPair other = startSession(new AuthPrincipal(SubjectType.MEMBER, member.getId(), null, null));

        // when
        MockHttpServletResponse response = logout(original.accessToken(), other.refreshToken());

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertError(reissue(original.refreshToken()), 401, "AUTH-011");
        assertThat(reissue(other.refreshToken()).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("동시에 같은 토큰으로 재발급하면 한 요청만 성공한다")
    void rotatesOnlyOnceConcurrently() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return reissue(original.refreshToken());
                }));
            }

            // when
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(15, TimeUnit.SECONDS));
            }

            // then
            List<MockHttpServletResponse> successes = responses.stream().filter(r -> r.getStatus() == 200).toList();
            assertThat(successes).hasSize(1);
            for (MockHttpServletResponse response : responses) {
                if (response.getStatus() != 200) {
                    assertError(response, 401, "AUTH-011");
                }
            }
            assertThat(reissue(refreshCookie(successes.get(0))).getStatus()).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("재발급과 로그아웃이 동시에 실행되어도 새 리프레시 세션이 남지 않는다")
    void logoutWinsAgainstRotation() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MockHttpServletResponse> rotation = executor.submit(() -> {
                start.await();
                return reissue(original.refreshToken());
            });
            Future<MockHttpServletResponse> logout = executor.submit(() -> {
                start.await();
                return logout(original.accessToken(), original.refreshToken());
            });

            // when
            start.countDown();
            MockHttpServletResponse rotated = rotation.get(15, TimeUnit.SECONDS);
            MockHttpServletResponse loggedOut = logout.get(15, TimeUnit.SECONDS);

            // then
            assertThat(loggedOut.getStatus()).isEqualTo(204);
            assertThat(rotated.getStatus()).isIn(200, 401);
            assertThat(redis.hasKey(sessionKey())).isFalse();
            if (rotated.getStatus() == 200) {
                assertError(reissue(refreshCookie(rotated)), 401, "AUTH-011");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("재발급 전 액세스 토큰으로 로그아웃해도 회전된 리프레시 토큰이 폐기된다")
    void revokesRotatedTokenWithOldAccess() throws Exception {
        // given
        String rotated = refreshCookie(reissue(original.refreshToken()));

        // when
        MockHttpServletResponse response = logout(original.accessToken(), original.refreshToken());

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertError(reissue(rotated), 401, "AUTH-011");
    }

    @Test
    @DisplayName("sid 도입 전 발급된 토큰도 재발급하고 이전 액세스·쿠키로 로그아웃할 수 있다")
    void supportsLegacyTokens() throws Exception {
        // given
        String refreshId = UUID.randomUUID().toString();
        String legacyRefresh = legacyToken("REFRESH", refreshId, Instant.now().plus(Duration.ofDays(14)));
        String legacyAccess = legacyToken("ACCESS", UUID.randomUUID().toString(),
                Instant.now().plus(Duration.ofMinutes(30)));
        store.save("MEMBER", member.getId(), refreshId, legacyRefresh, Duration.ofDays(14));

        // when
        MockHttpServletResponse rotated = reissue(legacyRefresh);
        MockHttpServletResponse loggedOut = logout(legacyAccess, legacyRefresh);

        // then
        assertThat(rotated.getStatus()).isEqualTo(200);
        assertThat(loggedOut.getStatus()).isEqualTo(204);
        assertThat(claims(refreshCookie(rotated)).get("sid", String.class)).isEqualTo(refreshId);
        assertError(reissue(refreshCookie(rotated)), 401, "AUTH-011");
    }

    @Test
    @DisplayName("활성 관리자는 재발급과 로그아웃을 공통 API로 처리한다")
    void supportsAdminTokens() throws Exception {
        // given
        Admin admin = createAdmin();
        JwtTokenPair pair = startSession(new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "SUPER_ADMIN", null));

        // when
        MockHttpServletResponse rotated = reissue(pair.refreshToken());
        String access = mapper.readTree(rotated.getContentAsString()).path("data").path("accessToken").asText();
        MockHttpServletResponse loggedOut = logout(access, refreshCookie(rotated));

        // then
        assertThat(rotated.getStatus()).isEqualTo(200);
        assertThat(claims(access).get("role", String.class)).isEqualTo("SUPER_ADMIN");
        assertThat(loggedOut.getStatus()).isEqualTo(204);
        assertError(reissue(refreshCookie(rotated)), 401, "AUTH-011");
    }

    @Test
    @DisplayName("비활성 관리자의 재발급은 AUTH-009로 거절하고 세션을 폐기한다")
    void rejectsInactiveAdmin() throws Exception {
        // given
        Admin admin = createAdmin();
        JwtTokenPair pair = startSession(new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "SUPER_ADMIN", null));
        jdbc.update("UPDATE admin SET active = false WHERE id = ?", admin.getId());

        // when
        MockHttpServletResponse response = reissue(pair.refreshToken());

        // then
        assertError(response, 403, "AUTH-009");
        assertThat(redis.hasKey("auth:refresh:ADMIN:" + admin.getId() + ":" + pair.refreshTokenId())).isFalse();
    }

    @Test
    @DisplayName("관리자 재발급은 기존 토큰 대신 DB의 최신 역할과 소속 마트를 사용한다")
    void reloadsAdminScope() throws Exception {
        // given
        Admin admin = createAdmin();
        JwtTokenPair pair = startSession(new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "SUPER_ADMIN", null));
        jdbc.update("UPDATE admin SET role = 'MART_ADMIN', mart_id = 3 WHERE id = ?", admin.getId());

        // when
        MockHttpServletResponse response = reissue(pair.refreshToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        String access = mapper.readTree(response.getContentAsString()).path("data").path("accessToken").asText();
        assertThat(claims(access).get("role", String.class)).isEqualTo("MART_ADMIN");
        assertThat(claims(access).get("martId", Long.class)).isEqualTo(3L);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 토큰은 재발급하지 않는다")
    void rejectsDeletedMember() throws Exception {
        // given
        members.deleteById(member.getId());

        // when
        MockHttpServletResponse response = reissue(original.refreshToken());

        // then
        assertError(response, 401, "AUTH-011");
    }

    private JwtTokenPair startSession(AuthPrincipal principal) {
        JwtTokenPair pair = tokens.createTokens(principal);
        store.save(principal.type().name(), principal.id(), pair.refreshTokenId(), pair.refreshToken(),
                pair.refreshExpiration());
        return pair;
    }

    private Admin createAdmin() {
        Admin admin = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).name("관리자")
                .role(AdminRole.SUPER_ADMIN).active(true).build());
        adminIds.add(admin.getId());
        return admin;
    }

    private MockHttpServletResponse reissue(String refreshToken) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/auth/reissue");
        if (refreshToken != null) {
            request.cookie(new Cookie("refreshToken", refreshToken));
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse logout(String accessToken, String refreshToken) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/auth/logout");
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        if (refreshToken != null) {
            request.cookie(new Cookie("refreshToken", refreshToken));
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private String refreshCookie(MockHttpServletResponse response) {
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith("refreshToken=");
        return cookie.substring("refreshToken=".length(), cookie.indexOf(';'));
    }

    private String sessionKey() {
        return "auth:refresh:MEMBER:" + member.getId() + ":" + original.refreshTokenId();
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(jwtSigningKey).build().parseSignedClaims(token).getPayload();
    }

    private String legacyToken(String use, String tokenId, Instant expiration) {
        return Jwts.builder().subject(member.getId().toString()).id(tokenId).claim("type", "MEMBER")
                .claim("tokenUse", use).issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(expiration)).signWith(jwtSigningKey).compact();
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo(code);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
