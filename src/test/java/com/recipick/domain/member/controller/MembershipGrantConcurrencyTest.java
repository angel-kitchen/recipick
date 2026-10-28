package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.MembershipGrantService;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MembershipGrantConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private AdminRepository admins;
    @Autowired
    private MembershipRepository memberships;
    @Autowired
    private MembershipGrantService service;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Long memberId;
    private Long adminId;
    private String token;

    @BeforeEach
    void setUp() {
        memberId = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@gc.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).passwordHash("hash").build()).getId();
        Admin admin = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString())
                .passwordHash("hash").role(AdminRole.SUPER_ADMIN).active(true).build());
        adminId = admin.getId();
        token = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, adminId, "SUPER_ADMIN", null)).accessToken();
    }

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", adminId);
        jdbc.update("DELETE FROM membership WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member WHERE id = ?", memberId);
        jdbc.update("DELETE FROM admin WHERE id = ?", adminId);
    }

    @Test
    @DisplayName("같은 회원에게 여섯 건을 동시에 지급해도 한 건만 생성한다")
    void grantsOnlyOnceUnderConcurrency() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                results.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 지급 시작 대기 시간 초과");
                    }
                    return grant();
                }));
            }

            // when
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> result : results) {
                responses.add(result.get(20, TimeUnit.SECONDS));
            }

            // then
            assertThat(responses.stream().filter(value -> value.getStatus() == 201).count()).isEqualTo(1);
            assertThat(responses.stream().filter(value -> value.getStatus() == 409).count()).isEqualTo(5);
            for (MockHttpServletResponse response : responses) {
                if (response.getStatus() == 409) {
                    assertThat(mapper.readTree(response.getContentAsString()).path("code").asText())
                            .isEqualTo("MEMBERSHIP-002");
                }
            }
            assertThat(count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT granted_by_admin_id FROM membership WHERE member_id = ?",
                    Long.class, memberId)).isEqualTo(adminId);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("회원 행 잠금 대기 5초가 지나면 COMMON-010이고 이용권은 생성되지 않는다")
    void returnsLockTimeoutWithoutGranting() throws Exception {
        // given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    members.findByIdForUpdate(memberId).orElseThrow();
                    locked.countDown();
                    try {
                        if (!release.await(15, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("검증용 잠금 해제 대기 시간 초과");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }));
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            long started = System.nanoTime();
            MockHttpServletResponse response = grant();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            // then
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("COMMON-010");
            assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(4500));
            assertThat(count()).isZero();
        } finally {
            release.countDown();
            try {
                holder.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("지급 후 같은 트랜잭션에서 실패하면 이용권을 롤백하고 다시 지급할 수 있다")
    void rollsBackFailedGrant() throws Exception {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AuthPrincipal principal = new AuthPrincipal(SubjectType.ADMIN, adminId, "SUPER_ADMIN", null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities()));

        // when
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.grant(memberId, adminId);
            memberships.flush();
            throw new IllegalStateException("검증용 후속 처리 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("검증용 후속 처리 실패");

        // then
        assertThat(count()).isZero();
        assertThat(grant().getStatus()).isEqualTo(201);
        assertThat(count()).isEqualTo(1);
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM membership WHERE member_id = ?", Long.class, memberId);
    }

    private MockHttpServletResponse grant() throws Exception {
        return mockMvc.perform(post("/api/admin/members/{id}/memberships", memberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
    }
}
