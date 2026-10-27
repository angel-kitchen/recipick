package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.request.MemberLoginRequest;
import com.recipick.domain.member.dto.request.MemberPasswordChangeRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.facade.MemberPasswordFacade;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.service.MemberLoginService;
import com.recipick.domain.member.service.MemberPasswordService;
import com.recipick.domain.member.service.TokenManagementService;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenPair;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class MemberPasswordChangeIntegrationTest extends IntegrationTestSupport {

    private static final String OLD_PASSWORD = "Original1!";
    private static final String NEW_PASSWORD = "Changed22!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private RedisRefreshTokenStore sessions;
    @Autowired
    private TokenManagementService management;
    @Autowired
    private MemberLoginService login;
    @Autowired
    private MemberPasswordFacade facade;
    @Autowired
    private MemberPasswordService service;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;

    private Member member;
    private JwtTokenPair first;
    private JwtTokenPair second;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        member = members.saveAndFlush(Member.builder().email(unique + "@password.test").name("비밀번호 검증 회원")
                .phone(unique.substring(0, 20)).passwordHash(encoder.encode(OLD_PASSWORD)).martId(1L).build());
        first = startSession();
        second = startSession();
    }

    @AfterEach
    void tearDown() {
        sessions.deleteSessions(sessions.findSessionKeys("MEMBER", member.getId()));
        members.deleteById(member.getId());
    }

    @Test
    @DisplayName("비밀번호 변경은 모든 이전 재발급 세션을 폐기하고 액세스 토큰은 유지한다")
    void changesPasswordAndRevokesAllRefreshTokens() throws Exception {
        // given
        String originalHash = current().getPasswordHash();

        // when
        MockHttpServletResponse response = change(OLD_PASSWORD, NEW_PASSWORD, first.accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "HttpOnly", "Path=/api/auth");
        assertThat(current().getPasswordHash()).isNotEqualTo(originalHash).isNotEqualTo(NEW_PASSWORD);
        assertThat(encoder.matches(NEW_PASSWORD, current().getPasswordHash())).isTrue();
        assertThat(encoder.matches(OLD_PASSWORD, current().getPasswordHash())).isFalse();
        assertThat(current().getRefreshTokenVersion()).isEqualTo(1);
        assertThat(sessions.findSessionKeys("MEMBER", member.getId())).isEmpty();
        rejectsRefresh(first);
        rejectsRefresh(second);
        assertThat(mvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + first.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("현재 비밀번호가 다르면 비밀번호·세션 버전·Redis 세션을 변경하지 않는다")
    void preservesStateWhenCurrentPasswordDoesNotMatch() throws Exception {
        // given
        String hash = current().getPasswordHash();

        // when
        MockHttpServletResponse response = change("Incorrect1!", NEW_PASSWORD, first.accessToken());

        // then
        assertError(response, 400, "MEMBER-005");
        assertThat(current().getPasswordHash()).isEqualTo(hash);
        assertThat(current().getRefreshTokenVersion()).isZero();
        assertThat(management.reissue(first.refreshToken())).isNotNull();
        assertThat(management.reissue(second.refreshToken())).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short1!", "abcdefghijklmnopqr1!X", "NoNumber!", "NoSpecial1", "1234567!",
            "Has space1!", "Has\ttab1!"})
    @DisplayName("가입 규칙에 맞지 않는 새 비밀번호는 변경과 세션 폐기 전에 거절한다")
    void rejectsInvalidNewPassword(String password) throws Exception {
        // given
        String hash = current().getPasswordHash();

        // when
        MockHttpServletResponse response = change(OLD_PASSWORD, password, first.accessToken());

        // then
        assertError(response, 400, "COMMON-001");
        assertThat(current().getPasswordHash()).isEqualTo(hash);
        assertThat(current().getRefreshTokenVersion()).isZero();
        assertThat(sessions.findSessionKeys("MEMBER", member.getId())).hasSize(2);
    }

    @Test
    @DisplayName("새 비밀번호로 로그인한 세션은 새 버전을 사용하고 재발급할 수 있다")
    void logsInUsingNewPasswordAndVersion() throws Exception {
        // given
        assertThat(change(OLD_PASSWORD, NEW_PASSWORD, first.accessToken()).getStatus()).isEqualTo(204);

        // when
        var result = login.login(new MemberLoginRequest(member.getEmail(), NEW_PASSWORD));

        // then
        assertThat(tokens.parseRefreshToken(result.refreshToken()).refreshTokenVersion()).isEqualTo(1);
        assertThat(management.reissue(result.refreshToken())).isNotNull();
        assertThatThrownBy(() -> login.login(new MemberLoginRequest(member.getEmail(), OLD_PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_MEMBER_CREDENTIALS));
    }

    @Test
    @DisplayName("커밋 뒤 새 비밀번호로 발급한 세션은 이전 세션 정리의 영향을 받지 않는다")
    void preservesLoginCreatedAfterCommit() {
        // given
        List<String> previous = sessions.findSessionKeys("MEMBER", member.getId());
        service.changePassword(member.getId(), request());
        var fresh = login.login(new MemberLoginRequest(member.getEmail(), NEW_PASSWORD));

        // when
        sessions.deleteSessions(previous);

        // then
        assertThat(management.reissue(fresh.refreshToken())).isNotNull();
        rejectsRefresh(first);
    }

    @Test
    @DisplayName("비밀번호 변경 중 늦게 저장된 이전 버전 세션도 다시 살아나지 않는다")
    void rejectsLateOldSessionWrite() throws Exception {
        // given
        assertThat(change(OLD_PASSWORD, NEW_PASSWORD, first.accessToken()).getStatus()).isEqualTo(204);

        // when
        sessions.save("MEMBER", member.getId(), first.refreshTokenId(),
                first.refreshToken(), first.refreshExpiration());

        // then
        assertThat(sessions.findSessionKeys("MEMBER", member.getId())).hasSize(1);
        rejectsRefresh(first);
    }

    @Test
    @DisplayName("동일한 현재 비밀번호로 변경 요청이 동시에 오면 한 요청만 성공한다")
    void serializesConcurrentPasswordChanges() throws Exception {
        // given
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> attempt = () -> {
            start.await();
            try {
                facade.changePassword(member.getId(), request());
                return true;
            } catch (BusinessException error) {
                assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_CURRENT_PASSWORD);
                return false;
            }
        };
        try {
            Future<Boolean> a = executor.submit(attempt);
            Future<Boolean> b = executor.submit(attempt);

            // when
            start.countDown();
            List<Boolean> results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));

            // then
            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(current().getRefreshTokenVersion()).isEqualTo(1);
            assertThat(encoder.matches(NEW_PASSWORD, current().getPasswordHash())).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("DB 변경이 롤백되면 비밀번호·버전도 복구되고 기존 세션이 유지된다")
    void rollsBackPasswordAndVersionTogether() {
        // given
        String hash = current().getPasswordHash();

        // when
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.changePassword(member.getId(), request());
            throw new IllegalStateException("후속 실패");
        })).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(current().getPasswordHash()).isEqualTo(hash);
        assertThat(current().getRefreshTokenVersion()).isZero();
        assertThat(management.reissue(first.refreshToken())).isNotNull();
    }

    @Test
    @DisplayName("익명·관리자·탈퇴 회원은 회원 비밀번호 변경 API를 사용할 수 없다")
    void enforcesMemberAuthentication() throws Exception {
        // given
        String admin = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();

        // when, then
        assertError(change(OLD_PASSWORD, NEW_PASSWORD, null), 401, "AUTH-001");
        assertError(change(OLD_PASSWORD, NEW_PASSWORD, admin), 403, "AUTH-004");
        jdbc.update("UPDATE member SET withdrawn_at = ? WHERE id = ?", LocalDateTime.now(), member.getId());
        assertError(change(OLD_PASSWORD, NEW_PASSWORD, first.accessToken()), 401, "AUTH-011");
        assertThat(current().getRefreshTokenVersion()).isZero();
    }

    @Test
    @DisplayName("이미 열린 트랜잭션에서 호출하면 Redis를 회원 잠금 안에서 호출하지 않도록 거절한다")
    void rejectsOuterTransactionOnFacade() {
        // given, when, then
        assertThatThrownBy(() -> transaction.executeWithoutResult(
                status -> facade.changePassword(member.getId(), request())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("비밀번호 요청 객체 문자열에는 비밀번호가 보이지 않는다")
    void redactsPasswordRequest() {
        // given, when, then
        assertThat(request().toString()).doesNotContain(OLD_PASSWORD, NEW_PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 20})
    @DisplayName("새 비밀번호 길이의 최소·최대 경계를 허용한다")
    void acceptsPasswordLengthBoundaries(int length) throws Exception {
        // given
        String next = "A1!" + "a".repeat(length - 3);

        // when
        MockHttpServletResponse response = change(OLD_PASSWORD, next, first.accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(encoder.matches(next, current().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("필수 비밀번호가 누락되면 DB와 세션을 유지한다")
    void rejectsMissingFields() throws Exception {
        // given, when
        var response = mvc.perform(patch("/api/members/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse();

        // then
        assertError(response, 400, "COMMON-001");
        assertThat(current().getRefreshTokenVersion()).isZero();
        assertThat(sessions.findSessionKeys("MEMBER", member.getId())).hasSize(2);
    }

    @Test
    @DisplayName("같은 비밀번호로 변경해도 세션 버전은 올려 이전 토큰을 폐기한다")
    void revokesSessionsEvenWhenPasswordIsSame() throws Exception {
        // given, when
        var response = change(OLD_PASSWORD, OLD_PASSWORD, first.accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(current().getRefreshTokenVersion()).isEqualTo(1);
        rejectsRefresh(first);
    }

    @Test
    @DisplayName("다른 회원과 관리자 세션은 비밀번호 변경으로 삭제하지 않는다")
    void isolatesMemberAndAdminSessions() throws Exception {
        // given
        String unique = UUID.randomUUID().toString().replace("-", "");
        Member other = members.saveAndFlush(Member.builder().email(unique + "@other.test").name("다른 회원")
                .phone(unique.substring(0, 20)).passwordHash(current().getPasswordHash()).build());
        JwtTokenPair otherPair = tokens.createMemberTokens(other.getId());
        JwtTokenPair adminPair = tokens.createTokens(
                new AuthPrincipal(SubjectType.ADMIN, member.getId(), "SUPER_ADMIN", null));
        sessions.save("MEMBER", other.getId(), otherPair.refreshTokenId(),
                otherPair.refreshToken(), otherPair.refreshExpiration());
        sessions.save("ADMIN", member.getId(), adminPair.refreshTokenId(),
                adminPair.refreshToken(), adminPair.refreshExpiration());
        try {
            // when
            var response = change(OLD_PASSWORD, NEW_PASSWORD, first.accessToken());

            // then
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(management.reissue(otherPair.refreshToken())).isNotNull();
            assertThat(sessions.findSessionKeys("ADMIN", member.getId())).hasSize(1);
        } finally {
            sessions.deleteSessions(sessions.findSessionKeys("MEMBER", other.getId()));
            sessions.deleteSessions(sessions.findSessionKeys("ADMIN", member.getId()));
            members.deleteById(other.getId());
        }
    }

    private MemberPasswordChangeRequest request() {
        return new MemberPasswordChangeRequest(OLD_PASSWORD, NEW_PASSWORD);
    }

    private JwtTokenPair startSession() {
        JwtTokenPair pair = tokens.createMemberTokens(member.getId());
        sessions.save("MEMBER", member.getId(), pair.refreshTokenId(), pair.refreshToken(), pair.refreshExpiration());
        return pair;
    }

    private Member current() {
        return members.findById(member.getId()).orElseThrow();
    }

    private void rejectsRefresh(JwtTokenPair pair) {
        assertThatThrownBy(() -> management.reissue(pair.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.RELOGIN_REQUIRED));
    }

    private MockHttpServletResponse change(String current, String next, String token) throws Exception {
        var builder = patch("/api/members/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("currentPassword", current, "newPassword", next)));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(builder).andReturn().getResponse();
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo(code);
    }
}
