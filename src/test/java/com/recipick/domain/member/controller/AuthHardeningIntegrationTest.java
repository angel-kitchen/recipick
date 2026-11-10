package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.request.MemberPasswordChangeRequest;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.service.MemberPasswordService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AuthHardeningIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired AdminRepository admins;
    @Autowired PasswordEncoder encoder;
    @Autowired MemberPasswordService passwords;
    @Autowired JdbcTemplate jdbc;

    Member member;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        member = members.saveAndFlush(Member.builder().email(unique + "@auth.test")
                .phone(unique.substring(0, 20)).name("보안 검증").passwordHash(encoder.encode("Original1!")).build());
    }

    @Test
    @DisplayName("비밀번호 변경 뒤 이전 액세스와 재발급은 거절하고 새 로그인 토큰은 허용한다")
    void revokesOldAccessAfterPasswordChange() throws Exception {
        // given
        var old = testMemberTokens(member.getId());
        // when
        passwords.changePassword(member.getId(), new MemberPasswordChangeRequest("Original1!", "Changed22!"));
        // then
        assertThat(mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + old.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/auth/reissue").cookie(new Cookie("memberRefreshToken", old.refreshToken())))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        var current = testMemberTokens(member.getId(), members.findById(member.getId()).orElseThrow().getRefreshTokenVersion());
        assertThat(mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + current.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("로그아웃한 세션의 액세스만 거절하고 다른 기기의 세션은 유지한다")
    void logoutRevokesOnlyItsSession() throws Exception {
        // given
        var one = testMemberTokens(member.getId());
        var other = testMemberTokens(member.getId());
        // when
        assertThat(mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + one.accessToken())
                .cookie(new Cookie("memberRefreshToken", one.refreshToken())))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        // then
        assertThat(mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + one.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + other.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Redis 세션이 없는 서명 유효 JWT도 업무 API에서 거절한다")
    void rejectsAccessWithoutSession() throws Exception {
        // given
        var provider = context.getBean(com.recipick.global.security.JwtTokenProvider.class);
        var unsignedSession = provider.createMemberTokens(member.getId());
        // when, then
        assertThat(mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + unsignedSession.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("관리자 비활성화는 기존 액세스의 다음 요청부터 거절한다")
    void rejectsDisabledAdminBeforeController() throws Exception {
        // given
        var admin = admin();
        var pair = testAdminTokens(new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "SUPER_ADMIN", null));
        // when
        jdbc.update("UPDATE admin SET active = false WHERE id = ?", admin.getId());
        // then
        var response = mvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + pair.accessToken()))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(json.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("AUTH-009");
    }

    @Test
    @DisplayName("관리자 역할이 바뀌면 기존 역할의 액세스를 공통 인증에서 거절한다")
    void rejectsChangedAdminScope() throws Exception {
        // given
        var admin = admin();
        var pair = testAdminTokens(new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "SUPER_ADMIN", null));
        // when
        jdbc.update("UPDATE admin SET role = 'MART_ADMIN', mart_id = 1 WHERE id = ?", admin.getId());
        // then
        assertThat(mvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + pair.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("관리자 리프레시를 회원 경로에 넣어도 회원 토큰으로 재발급되지 않는다")
    void rejectsCrossSubjectRefresh() throws Exception {
        // given
        var admin = admin();
        var pair = testAdminTokens(new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "SUPER_ADMIN", null));
        // when, then
        assertThat(mvc.perform(post("/api/auth/reissue").cookie(new Cookie("memberRefreshToken", pair.refreshToken())))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/admin/auth/reissue").cookie(new Cookie("adminRefreshToken", pair.refreshToken())))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("기존 공용 리프레시 쿠키는 새 경로에서 인증되지 않아 재로그인이 필요하다")
    void rejectsLegacyCookie() throws Exception {
        // given
        var pair = testMemberTokens(member.getId());
        // when, then
        assertThat(mvc.perform(post("/api/auth/reissue").cookie(new Cookie("refreshToken", pair.refreshToken())))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("CSRF 없는 로그인은 거절하고 서버에서 받은 쿠키와 헤더가 일치하면 허용한다")
    void requiresCsrfForCookieAuthentication() throws Exception {
        // given
        // 공통 fixture의 기본 CSRF를 사용하지 않고 실제 필터 체인으로 재현한다.
        MockMvc browser = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String body = json.writeValueAsString(java.util.Map.of("email", member.getEmail(), "password", "Original1!"));
        // when, then
        assertThat(browser.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        var protection = browser.perform(get("/api/auth/csrf")).andReturn().getResponse();
        String token = json.readTree(protection.getContentAsString()).path("data").path("token").asText();
        String cookieHeader = protection.getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("authCsrfToken=")).findFirst().orElseThrow();
        Cookie cookie = new Cookie("authCsrfToken", cookieHeader.substring(
                "authCsrfToken=".length(), cookieHeader.indexOf(';')));
        assertThat(browser.perform(post("/api/auth/reissue").cookie(cookie).param("_csrf", token))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(browser.perform(post("/api/auth/login").cookie(cookie).header("X-CSRF-TOKEN", "wrong")
                .contentType("application/json").content(body)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(browser.perform(post("/api/auth/login").cookie(cookie).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content(body)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("민감 작업의 비밀번호 실패 제한은 일반 로그인까지 잠그지 않는다")
    void limitsReauthenticationWithoutLockingLogin() throws Exception {
        // given, when
        for (int i = 0; i < 5; i++) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> passwords.changePassword(member.getId(),
                    new MemberPasswordChangeRequest("Wrong11!", "Changed22!"))).isInstanceOf(BusinessException.class);
        }
        // then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> passwords.changePassword(member.getId(),
                new MemberPasswordChangeRequest("Original1!", "Changed22!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo("MEMBER-009"));
        assertThat(mvc.perform(post("/api/auth/login").contentType("application/json").content(
                json.writeValueAsString(java.util.Map.of("email", member.getEmail(), "password", "Original1!"))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("다른 탭에서 로그인한 정상 세션의 Cookie는 이전 세션 로그아웃이 지우지 않는다")
    void preservesCookieOfAnotherSession() throws Exception {
        // given
        var first = testMemberTokens(member.getId());
        var second = testMemberTokens(member.getId());
        // when
        var response = mvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + first.accessToken())
                .cookie(new Cookie("memberRefreshToken", second.refreshToken()))).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("memberRefreshToken="))).isEmpty();
        assertThat(mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + second.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("유예 중 이용권은 정상 종료와 실제 혜택 종료를 구분해 반환하고 예약하면 종료한다")
    void exposesGraceAndEndsItOnReservation() throws Exception {
        // given
        var clock = context.getBean(java.time.Clock.class);
        var now = java.time.LocalDateTime.now(clock).withNano(0);
        var membership = com.recipick.domain.member.entity.Membership.pendingPaid(member,
                com.recipick.domain.member.entity.enums.MembershipSource.SUBSCRIBE,
                now.minusDays(31), now.minusDays(1),
                new com.recipick.domain.member.dto.MembershipConsent(3900L, "v1", now.minusDays(31)));
        membership.activateAfterFirstPayment(now.minusDays(31), now.minusDays(1), now.plusDays(2));
        membership = context.getBean(com.recipick.domain.member.repository.MembershipRepository.class)
                .saveAndFlush(membership);
        var access = testMemberTokens(member.getId()).accessToken();
        // when
        var result = mvc.perform(get("/api/memberships/me").header("Authorization", "Bearer " + access))
                .andReturn().getResponse();
        var current = json.readTree(result.getContentAsString()).path("data").path("current");
        // then
        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(current.path("inGrace").asBoolean()).isTrue();
        assertThat(current.path("effectiveEndAt").asText()).isNotEqualTo(current.path("endAt").asText());
        assertThat(mvc.perform(post("/api/memberships/{id}/cancel-reservation", membership.getId())
                .header("Authorization", "Bearer " + access)).andReturn().getResponse().getStatus()).isEqualTo(200);
        var ended = mvc.perform(get("/api/memberships/me").header("Authorization", "Bearer " + access))
                .andReturn().getResponse();
        assertThat(json.readTree(ended.getContentAsString()).path("data").path("active").asBoolean()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/auth/signup", "/api/auth/login", "/api/auth/reissue", "/api/auth/logout",
            "/api/admin/auth/login", "/api/admin/auth/reissue", "/api/admin/auth/logout"
    })
    @DisplayName("모든 Cookie 인증 POST는 CSRF 없는 요청을 업무 실행 전에 거절한다")
    void protectsEveryCookieEndpoint(String path) throws Exception {
        // given
        MockMvc browser = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // when, then
        assertThat(browser.perform(post(path).contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    private Admin admin() {
        return admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).passwordHash("unused")
                .role(AdminRole.SUPER_ADMIN).active(true).build());
    }
}
