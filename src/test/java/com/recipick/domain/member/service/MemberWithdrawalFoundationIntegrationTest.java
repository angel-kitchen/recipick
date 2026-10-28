package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.request.MemberLoginRequest;
import com.recipick.domain.member.dto.request.MemberAdminSearchRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.JwtTokenPair;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class MemberWithdrawalFoundationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository members;
    @Autowired
    private MemberService service;
    @Autowired
    private MemberAdminService adminService;
    @Autowired
    private MembershipGrantService grant;
    @Autowired
    private MembershipService memberships;
    @Autowired
    private MemberLoginService login;
    @Autowired
    private TokenManagementService tokenManagement;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private RedisRefreshTokenStore refreshTokens;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private MockMvc mvc;

    private Member member;
    private JwtTokenPair pair;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        member = members.saveAndFlush(Member.builder().email(unique + "@status.test").name("상태 검증 회원")
                .phone(unique.substring(0, 20)).passwordHash(encoder.encode("Password1!"))
                .martId(1L).build());
        pair = tokens.createMemberTokens(member.getId());
        refreshTokens.save("MEMBER", member.getId(), pair.refreshTokenId(), pair.refreshToken(),
                pair.refreshExpiration());
    }

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        refreshTokens.delete("MEMBER", member.getId(), pair.refreshTokenId());
        members.deleteById(member.getId());
    }

    @Test
    @DisplayName("발급된 액세스 토큰도 DB에 탈퇴가 반영된 다음 요청부터 거절한다")
    void rejectsIssuedAccessTokenAfterWithdrawal() throws Exception {
        // given
        assertThat(mvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + pair.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(200);
        markWithdrawn();

        // when
        var response = mvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + pair.accessToken())).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH-011");
    }

    @Test
    @DisplayName("기존 리프레시 세션이 Redis에 남아 있어도 탈퇴 회원은 재발급할 수 없다")
    void rejectsRefreshEvenWhenSessionRemains() {
        // given
        markWithdrawn();

        // when
        var failure = assertThatThrownBy(() -> tokenManagement.reissue(pair.refreshToken()));

        // then
        failure.isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.RELOGIN_REQUIRED));
    }

    @Test
    @DisplayName("원래 이메일과 올바른 비밀번호가 남아 있어도 탈퇴 회원은 로그인할 수 없다")
    void rejectsLoginWithCorrectPassword() {
        // given
        markWithdrawn();

        // when
        var failure = assertThatThrownBy(() -> login.login(
                new MemberLoginRequest(member.getEmail(), "Password1!")));

        // then
        failure.isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_MEMBER_CREDENTIALS));
    }

    @Test
    @DisplayName("탈퇴 회원은 일반 조회·잠금 조회·멤버십 검증에서 회원 없음으로 처리한다")
    void rejectsDomainReadsAndLocks() {
        // given
        markWithdrawn();

        // when, then
        assertMissing(() -> service.getMartId(member.getId()));
        assertMissing(() -> service.getProfile(member.getId()));
        assertMissing(() -> service.getSelectedAddressId(member.getId()));
        assertMissing(() -> service.getMemberForUpdate(member.getId()));
        assertMissing(() -> transaction.executeWithoutResult(status -> service.lockActiveMember(member.getId())));
        assertMissing(() -> memberships.requireActiveMembership(member.getId()));
        assertMissing(() -> grant.grant(member.getId(), 1L));
    }

    @Test
    @DisplayName("외부 도메인용 회원 잠금은 바깥 트랜잭션이 필수이며 이용 중 회원만 통과한다")
    void requiresOuterTransactionForLock() {
        // given
        Long memberId = member.getId();

        // when, then
        assertThatThrownBy(() -> service.lockActiveMember(memberId))
                .isInstanceOf(IllegalTransactionStateException.class);
        TransactionTemplate readOnly = new TransactionTemplate(transaction.getTransactionManager());
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> service.lockActiveMember(memberId)))
                .isInstanceOf(IllegalStateException.class);
        transaction.executeWithoutResult(status -> service.lockActiveMember(memberId));
        assertThat(members.findById(memberId).orElseThrow().isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("관리자 회원 검색에서 탈퇴 회원은 목록과 전체 건수 모두 제외한다")
    void excludesWithdrawnMemberFromAdminSearch() {
        com.recipick.global.security.AuthPrincipal principal = new com.recipick.global.security.AuthPrincipal(
                com.recipick.global.security.SubjectType.ADMIN, 1L, "SUPER_ADMIN", null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities()));
        // given
        MemberAdminSearchRequest request = new MemberAdminSearchRequest(member.getEmail(), null, null, null, null, 0, 20, null);
        assertThat(adminService.findMembers(request).totalElements()).isEqualTo(1);
        markWithdrawn();

        // when
        var result = adminService.findMembers(request);

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    private void markWithdrawn() {
        // 최종 탈퇴 API 구현 전, 실제 커밋된 탈퇴 상태로 인증·도메인 진입점만 검증한다.
        jdbc.update("UPDATE member SET withdrawn_at = ? WHERE id = ?", LocalDateTime.now(), member.getId());
    }

    private void assertMissing(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
