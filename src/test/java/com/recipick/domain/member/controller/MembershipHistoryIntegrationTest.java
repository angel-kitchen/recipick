package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.AdminAuditLogRepository;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditTarget;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MembershipHistoryIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AdminRepository admins;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired AdminAuditLogRepository logs;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    Admin admin;
    Admin martAdmin;
    Member member;
    Member other;
    String token;

    @BeforeEach
    void setup() {
        admin = createAdmin(AdminRole.SUPER_ADMIN, null);
        martAdmin = createAdmin(AdminRole.MART_ADMIN, 1L);
        member = createMember();
        other = createMember();
        token = token(admin);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id IN (?, ?)", admin.getId(), martAdmin.getId());
        jdbc.update("DELETE FROM membership WHERE member_id IN (?, ?)", member.getId(), other.getId());
        members.deleteAllById(List.of(member.getId(), other.getId()));
        admins.deleteAllById(List.of(admin.getId(), martAdmin.getId()));
    }

    private Admin createAdmin(AdminRole role, Long martId) {
        return admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).passwordHash("hash")
                .role(role).martId(martId).active(true).build());
    }

    private Member createMember() {
        return members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@history.test")
                .name("이력 조회 회원").phone(UUID.randomUUID().toString().substring(0, 20))
                .passwordHash("hash").martId(1L).build());
    }

    private String token(Admin value) {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, value.getId(), value.getRole().name(),
                value.getMartId())).accessToken();
    }

    private Membership membership(Member owner, MembershipType type, MembershipSource source,
            LocalDateTime start, LocalDateTime end) {
        return Membership.builder().member(owner).type(type).source(source).startAt(start).activatedAt(start).endAt(end)
                .autoRenew(source != MembershipSource.ADMIN_GRANT)
                .grantedByAdmin(source == MembershipSource.ADMIN_GRANT ? admin : null).build();
    }

    private MockHttpServletResponse search(Long memberId, String... params) throws Exception {
        var request = get("/api/admin/members/{memberId}/memberships", memberId)
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    @Test
    @DisplayName("회원의 모든 발급 경로와 상태를 조회하고 다른 회원 이력과 개인정보는 포함하지 않는다")
    void returnsAllHistoryWithoutOtherMembersOrPrivateFields() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        Membership expired = memberships.saveAndFlush(membership(member, MembershipType.TRIAL, MembershipSource.TRIAL,
                now.minusDays(9), now.minusDays(2)));
        Membership reserved = membership(member, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                now.minusDays(1), now.plusDays(29));
        reserved.reserveCancellation(now);
        memberships.saveAndFlush(reserved);
        Membership terminated = membership(member, MembershipType.PAID, MembershipSource.TRIAL_CONVERT,
                now.minusDays(1), now.plusDays(29));
        terminated.terminateAfterRefund(now);
        memberships.saveAndFlush(terminated);
        Membership grant = memberships.saveAndFlush(membership(member, MembershipType.PAID, MembershipSource.ADMIN_GRANT,
                now.minusDays(1), now.plusDays(29)));
        memberships.saveAndFlush(membership(other, MembershipType.PAID, MembershipSource.ADMIN_GRANT,
                now.minusDays(1), now.plusDays(29)));
        // when
        var response = search(member.getId());
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode result = data(response);
        assertThat(result.path("totalElements").asInt()).isEqualTo(4);
        assertThat(result.path("page").asInt()).isZero();
        assertThat(result.path("size").asInt()).isEqualTo(20);
        assertThat(result.path("content").findValuesAsText("membershipId"))
                .containsExactly(grant.getId().toString(), terminated.getId().toString(),
                        reserved.getId().toString(), expired.getId().toString());
        assertThat(result.path("content").findValuesAsText("status"))
                .containsExactly("ACTIVE", "TERMINATED", "CANCEL_RESERVED", "EXPIRED");
        assertThat(result.path("content").findValuesAsText("source"))
                .containsExactly("ADMIN_GRANT", "TRIAL_CONVERT", "SUBSCRIBE", "TRIAL");
        assertThat(result.path("content").get(0).path("grantedByAdminId").asLong()).isEqualTo(admin.getId());
        assertThat(result.path("content").get(1).path("grantedByAdminId").isNull()).isTrue();
        assertThat(result.path("content").get(2).path("autoRenew").asBoolean()).isFalse();
        assertThat(result.path("content").get(2).path("cancelReservedAt").asText()).endsWith("+09:00");
        assertThat(response.getContentAsString()).doesNotContain(member.getEmail(), member.getName(), member.getPhone(),
                "passwordHash", "portoneBillingKey", "consentedAmount");
    }

    @Test
    @DisplayName("등록 시각이 같아도 ID 내림차순으로 페이지를 나누며 범위 밖은 빈 목록이다")
    void paginatesWithStableTieBreaker() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        Membership first = memberships.saveAndFlush(membership(member, MembershipType.PAID, MembershipSource.ADMIN_GRANT,
                now.minusDays(1), now.plusDays(1)));
        Membership second = memberships.saveAndFlush(membership(member, MembershipType.TRIAL, MembershipSource.TRIAL,
                now.minusDays(10), now.minusDays(3)));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE membership SET created_at = :created WHERE member_id = :memberId")
                .setParameter("created", now).setParameter("memberId", member.getId()).executeUpdate());
        // when
        var page0 = search(member.getId(), "size", "1");
        var page1 = search(member.getId(), "size", "1", "page", "1");
        var outside = search(member.getId(), "size", "1", "page", "2");
        // then
        assertThat(data(page0).path("content").get(0).path("membershipId").asLong()).isEqualTo(second.getId());
        assertThat(data(page1).path("content").get(0).path("membershipId").asLong()).isEqualTo(first.getId());
        assertThat(data(page1).path("page").asInt()).isEqualTo(1);
        assertThat(data(page1).path("totalPages").asInt()).isEqualTo(2);
        assertThat(data(outside).path("content").isEmpty()).isTrue();
        assertThat(data(outside).path("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("빈 이력 조회도 매번 회원 ID와 마트를 감사 기록하고 상세 값은 남기지 않는다")
    void auditsEveryEmptyRead() throws Exception {
        // given, when
        var first = search(member.getId());
        var second = search(member.getId());
        // then
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(data(first).path("content").isEmpty()).isTrue();
        assertThat(data(first).path("totalElements").asInt()).isZero();
        assertThat(logs.findAll().stream().filter(log -> log.getAdmin().getId().equals(admin.getId())).toList())
                .hasSize(2).allSatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo(AdminAuditAction.MEMBER_MEMBERSHIP_HISTORY_VIEW);
                    assertThat(log.getTargetType()).isEqualTo(AdminAuditTarget.MEMBER);
                    assertThat(log.getTargetId()).isEqualTo(member.getId());
                    assertThat(log.getMartId()).isEqualTo(1L);
                    assertThat(log.getDetails()).isNull();
                });
    }

    @Test
    @DisplayName("미시작 이용권은 제외하거나 활성화하지 않고 기존 규칙대로 상태 null을 반환한다")
    void includesFutureMembershipWithoutChangingIt() throws Exception {
        // given: DB 저장 정밀도에 영향을 받지 않도록 입력 시각을 초 단위로 맞춘다.
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Membership future = memberships.saveAndFlush(membership(member, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                now.plusDays(1), now.plusDays(31)));
        // when
        var response = search(member.getId());
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("content").get(0).path("status").isNull()).isTrue();
        assertThat(memberships.findById(future.getId()).orElseThrow().getStartAt()).isEqualTo(future.getStartAt());
    }

    @Test
    @DisplayName("없는 회원과 탈퇴 회원은 MEMBER-001로 거절하고 성공 감사 기록을 만들지 않는다")
    void rejectsMissingAndWithdrawnMember() throws Exception {
        // given
        jdbc.update("UPDATE member SET withdrawn_at = CURRENT_TIMESTAMP WHERE id = ?", member.getId());
        // when
        var missing = search(Long.MAX_VALUE);
        var withdrawn = search(member.getId());
        // then
        for (var response : List.of(missing, withdrawn)) {
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsString()).contains("MEMBER-001");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ?", Long.class,
                admin.getId())).isZero();
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "page,wrong"})
    @DisplayName("잘못된 페이지 입력은 400으로 거절한다")
    void rejectsInvalidPage(String field, String value) throws Exception {
        // given, when
        var response = search(member.getId(), field, value);
        // then
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("미인증·일반 회원·마트 관리자는 멤버십 이력을 조회할 수 없다")
    void restrictsAccessToSuperAdmin() throws Exception {
        // given
        String path = "/api/admin/members/" + member.getId() + "/memberships";
        String memberToken = tokens.createMemberTokens(member.getId()).accessToken();
        // when, then
        assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).isEqualTo(401);
        for (String denied : List.of(memberToken, token(martAdmin))) {
            assertThat(mvc.perform(get(path).header("Authorization", "Bearer " + denied))
                    .andReturn().getResponse().getStatus()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("토큰 발급 뒤 비활성화되거나 마트 관리자로 바뀐 관리자의 조회 결과는 반환하지 않는다")
    void rechecksAdminAtAuditTime() throws Exception {
        // given
        jdbc.update("UPDATE admin SET active = false WHERE id = ?", admin.getId());
        // when
        var inactive = search(member.getId());
        jdbc.update("UPDATE admin SET active = true, role = 'MART_ADMIN', mart_id = 1 WHERE id = ?", admin.getId());
        var changedRole = search(member.getId());
        // then
        assertThat(inactive.getStatus()).isEqualTo(403);
        assertThat(changedRole.getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ?", Long.class,
                admin.getId())).isZero();
    }
}
