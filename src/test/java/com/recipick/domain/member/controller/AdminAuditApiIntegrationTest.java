package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminAuditLogRepository;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.AdminAuditCleanupService;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminAuditApiIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AdminRepository admins;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired AdminAuditLogRepository logs;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminAuditCleanupService cleanup;
    @Autowired Clock clock;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    Admin admin;
    Admin other;
    Admin martAdmin;
    Member member;
    String token;

    @BeforeEach
    void setup() {
        admin = createAdmin(AdminRole.SUPER_ADMIN, null);
        other = createAdmin(AdminRole.SUPER_ADMIN, null);
        martAdmin = createAdmin(AdminRole.MART_ADMIN, 1L);
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@audit.test")
                .name("감사 테스트 회원").phone(UUID.randomUUID().toString().substring(0, 20)).passwordHash("hash").build());
        token = token(admin);
    }

    @AfterEach
    void teardown() {
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id IN (?, ?, ?)", admin.getId(), other.getId(), martAdmin.getId());
        jdbc.update("DELETE FROM membership WHERE member_id = ?", member.getId());
        members.deleteById(member.getId());
        admins.deleteAllById(List.of(admin.getId(), other.getId(), martAdmin.getId()));
    }

    private Admin createAdmin(AdminRole role, Long martId) {
        return admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).passwordHash("hash")
                .role(role).martId(martId).active(true).build());
    }

    private String token(Admin value) {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, value.getId(), value.getRole().name(),
                value.getMartId())).accessToken();
    }

    private AdminAuditLog log(Admin actor, AdminAuditAction action, Long targetId) {
        return logs.saveAndFlush(AdminAuditLog.create(actor,
                new AdminAuditCommand(action, targetId, null, Map.of()), "198.51.100.7"));
    }

    private MockHttpServletResponse search(String... parameters) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + token);
        boolean adminFilter = false;
        for (int i = 0; i < parameters.length; i += 2) {
            request.param(parameters[i], parameters[i + 1]);
            adminFilter |= parameters[i].equals("adminId");
        }
        if (!adminFilter) {
            request.param("adminId", admin.getId().toString());
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    @Test
    @DisplayName("감사 목록은 관리자·작업·대상 조건을 함께 적용하고 상세에서 IP를 제공한다")
    void filtersAndReturnsDetail() throws Exception {
        // given
        AdminAuditLog expected = log(admin, AdminAuditAction.MEMBER_LIST_VIEW, null);
        log(admin, AdminAuditAction.MEMBERSHIP_GRANT, 10L);
        log(other, AdminAuditAction.MEMBER_LIST_VIEW, null);
        // when
        var response = search("action", "MEMBER_LIST_VIEW", "targetType", "MEMBER");
        var detail = mvc.perform(get("/api/admin/audit-logs/{id}", expected.getId())
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("totalElements").asInt()).isEqualTo(1);
        assertThat(data(response).path("content").get(0).path("id").asLong()).isEqualTo(expected.getId());
        assertThat(data(response).path("content").get(0).has("ipAddress")).isFalse();
        assertThat(data(detail).path("ipAddress").asText()).isEqualTo("198.51.100.7");
        assertThat(data(detail).path("targetId").isNull()).isTrue();
        assertThat(data(detail).has("email")).isFalse();
    }

    @Test
    @DisplayName("기간 검색은 시작 포함·종료 미포함이며 같은 시각은 ID 내림차순으로 페이지 처리한다")
    void appliesPeriodAndStablePagination() throws Exception {
        // given
        LocalDateTime from = LocalDateTime.of(2026, 10, 20, 10, 0);
        AdminAuditLog first = log(admin, AdminAuditAction.MEMBER_LIST_VIEW, null);
        AdminAuditLog second = log(admin, AdminAuditAction.MEMBER_LIST_VIEW, null);
        AdminAuditLog outside = log(admin, AdminAuditAction.MEMBER_LIST_VIEW, null);
        setCreatedAt(first.getId(), from);
        setCreatedAt(second.getId(), from);
        setCreatedAt(outside.getId(), from.plusDays(1));
        // when
        var response = search("from", from.toString(), "to", from.plusDays(1).toString(), "size", "1");
        var next = search("from", from.toString(), "to", from.plusDays(1).toString(), "size", "1", "page", "1");
        // then
        assertThat(data(response).path("totalElements").asInt()).isEqualTo(2);
        assertThat(data(response).path("content").get(0).path("id").asLong()).isEqualTo(second.getId());
        assertThat(data(next).path("content").get(0).path("id").asLong()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("기록이 없으면 정상 빈 페이지이고 없는 상세는 AUDIT-001이다")
    void handlesMissingLogs() throws Exception {
        // given, when
        var empty = search();
        var missing = mvc.perform(get("/api/admin/audit-logs/{id}", Long.MAX_VALUE)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        // then
        assertThat(empty.getStatus()).isEqualTo(200);
        assertThat(data(empty).path("content").isEmpty()).isTrue();
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(missing.getContentAsString()).contains("AUDIT-001");
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "adminId,0", "action,INVALID", "targetType,INVALID", "from,wrong"})
    @DisplayName("잘못된 필터와 페이지 입력을 거절한다")
    void rejectsInvalidSearch(String field, String value) throws Exception {
        // given, when
        var response = search(field, value);
        // then
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("같거나 역전된 기간을 거절한다")
    void rejectsInvalidPeriod() throws Exception {
        // given, when
        var equal = search("from", "2026-10-20T10:00:00", "to", "2026-10-20T10:00:00");
        var reversed = search("from", "2026-10-21T10:00:00", "to", "2026-10-20T10:00:00");
        // then
        assertThat(equal.getStatus()).isEqualTo(400);
        assertThat(reversed.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("미인증·회원·마트 관리자는 목록과 상세에 접근할 수 없다")
    void protectsBothEndpoints() throws Exception {
        // given
        String memberToken = tokens.createMemberTokens(member.getId()).accessToken();
        // when, then
        for (String path : List.of("/api/admin/audit-logs", "/api/admin/audit-logs/1")) {
            assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).isEqualTo(401);
            assertThat(mvc.perform(get(path).header("Authorization", "Bearer " + memberToken))
                    .andReturn().getResponse().getStatus()).isEqualTo(403);
            assertThat(mvc.perform(get(path).header("Authorization", "Bearer " + token(martAdmin)))
                    .andReturn().getResponse().getStatus()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("토큰이 남아 있어도 비활성 슈퍼 관리자의 조회를 거절한다")
    void rejectsInactiveAdmin() throws Exception {
        // given
        jdbc.update("UPDATE admin SET active = false WHERE id = ?", admin.getId());
        // when
        var response = search();
        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTH-009");
    }

    @Test
    @DisplayName("회원 목록 조회는 매 요청 기록하며 검색어와 응답 본문을 저장하지 않는다")
    void recordsEachMemberSearchWithoutPersonalData() throws Exception {
        // given, when
        for (int i = 0; i < 2; i++) {
            assertThat(mvc.perform(get("/api/admin/members").header("Authorization", "Bearer " + token)
                    .param("email", member.getEmail()).param("name", member.getName()))
                    .andReturn().getResponse().getStatus()).isEqualTo(200);
        }
        // then
        var own = logs.findAll().stream().filter(value -> value.getAdmin().getId().equals(admin.getId())).toList();
        assertThat(own).hasSize(2).allSatisfy(value -> {
            assertThat(value.getAction()).isEqualTo(AdminAuditAction.MEMBER_LIST_VIEW);
            assertThat(value.getTargetId()).isNull();
            assertThat(value.getDetails()).isNull();
        });
    }

    @Test
    @DisplayName("멤버십 지급과 감사 로그가 함께 저장되고 중복 지급 거절은 기록을 추가하지 않는다")
    void recordsGrantOnce() throws Exception {
        // given, when
        var response = mvc.perform(post("/api/admin/members/{id}/memberships", member.getId())
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        var repeated = mvc.perform(post("/api/admin/members/{id}/memberships", member.getId())
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(repeated.getStatus()).isEqualTo(409);
        var own = logs.findAll().stream().filter(value -> value.getAdmin().getId().equals(admin.getId())).toList();
        assertThat(own).singleElement().satisfies(value -> {
            assertThat(value.getAction()).isEqualTo(AdminAuditAction.MEMBERSHIP_GRANT);
            assertThat(value.getTargetId()).isEqualTo(dataId(response));
            assertThat(value.getDetails()).containsOnlyKeys("type", "source", "startAt", "endAt", "autoRenew");
        });
    }

    private Long dataId(MockHttpServletResponse response) {
        try {
            return data(response).path("membershipId").asLong();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("감사 INSERT의 FK 오류가 나면 먼저 생성한 멤버십도 롤백된다")
    void rollsBackGrantWhenAuditInsertFails() throws Exception {
        // given
        jdbc.update("UPDATE member SET mart_id = ? WHERE id = ?", Long.MAX_VALUE, member.getId());
        // when
        var response = mvc.perform(post("/api/admin/members/{id}/memberships", member.getId())
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(memberships.existsByMemberId(member.getId())).isFalse();
        assertThat(logs.findAll()).noneMatch(value -> value.getAdmin().getId().equals(admin.getId()));
    }

    @Test
    @DisplayName("회원 목록 감사 기록이 거절되면 조회 결과를 반환하지 않는다")
    void withholdsMemberSearchWhenAuditFails() throws Exception {
        // given
        jdbc.update("UPDATE admin SET active = false WHERE id = ?", admin.getId());
        // when
        var response = mvc.perform(get("/api/admin/members").param("email", member.getEmail())
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(mapper.readTree(response.getContentAsString()).has("data")).isFalse();
    }

    @Test
    @DisplayName("보관 기간 경계까지 삭제하고 최근 로그는 유지하며 재실행은 안전하다")
    void deletesOnlyExpiredLogs() {
        // given
        LocalDateTime cutoff = LocalDateTime.now(clock).minusYears(1).withNano(0);
        AdminAuditLog old = log(admin, AdminAuditAction.MEMBER_LIST_VIEW, null);
        AdminAuditLog boundary = log(admin, AdminAuditAction.MEMBER_LIST_VIEW, null);
        AdminAuditLog recent = log(admin, AdminAuditAction.MEMBER_LIST_VIEW, null);
        setCreatedAt(old.getId(), cutoff.minusSeconds(1));
        setCreatedAt(boundary.getId(), cutoff);
        setCreatedAt(recent.getId(), cutoff.plusSeconds(1));
        // when
        cleanup.deleteExpiredBatch(cutoff);
        cleanup.deleteExpiredBatch(cutoff);
        // then
        assertThat(logs.existsById(old.getId())).isFalse();
        assertThat(logs.existsById(boundary.getId())).isFalse();
        assertThat(logs.existsById(recent.getId())).isTrue();
    }
    @Test
    @DisplayName("두 정리 작업이 동시에 실행돼도 만료 로그만 최대 1,000건씩 중복 없이 삭제한다")
    void concurrentCleanupIsIdempotentAndBounded() throws Exception {
        // given
        LocalDateTime cutoff = LocalDateTime.now(clock).minusYears(1).withNano(0);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var query = entityManager.createNativeQuery("INSERT INTO admin_audit_log "
                    + "(admin_id, action, target_type, created_at) VALUES (:adminId, :action, :target, :createdAt)")
                    .setParameter("adminId", admin.getId())
                    .setParameter("action", "MEMBER_LIST_VIEW")
                    .setParameter("target", "MEMBER")
                    .setParameter("createdAt", cutoff.minusDays(1));
            for (int i = 0; i < 1001; i++) {
                query.executeUpdate();
            }
        });
        AdminAuditLog recent = log(admin, AdminAuditAction.MEMBER_LIST_VIEW, null);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            // when
            java.util.concurrent.Callable<Integer> task = () -> {
                start.await();
                return cleanup.deleteExpiredBatch(cutoff);
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            int firstCount = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            int secondCount = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            // then
            assertThat(firstCount).isBetween(0, 1000);
            assertThat(secondCount).isBetween(0, 1000);
            assertThat(firstCount + secondCount).isEqualTo(1001);
            assertThat(logs.existsById(recent.getId())).isTrue();
            assertThat(cleanup.deleteExpiredBatch(cutoff)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private void setCreatedAt(Long id, LocalDateTime createdAt) {
        // 테스트 날짜도 조회·삭제와 동일한 Hibernate 시간대 설정으로 바인딩한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery("UPDATE admin_audit_log SET created_at = :createdAt WHERE id = :id")
                        .setParameter("createdAt", createdAt).setParameter("id", id).executeUpdate());
        assertThat(logs.findById(id).orElseThrow().getCreatedAt()).isEqualTo(createdAt);
    }

}
