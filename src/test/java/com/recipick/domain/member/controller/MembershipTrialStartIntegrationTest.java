package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.annotation.Propagation;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

@Transactional
class MembershipTrialStartIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    Member member;
    String token;
    static final String REQUEST = """
            {"autopayAgreed":true,"consentedAmount":3900,"consentTextVersion":"v1"}
            """;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 20);
        member = members.saveAndFlush(Member.builder().email(suffix + "@trial.test")
                .phone(suffix).name("체험 회원").passwordHash("hash").build());
        token = tokens.createMemberTokens(member.getId()).accessToken();
    }

    void card(String status) {
        jdbc.update("""
                INSERT INTO billing_key(member_id, portone_billing_key, status, issued_at, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, member.getId(), UUID.randomUUID().toString(), status);
    }

    MockHttpServletResponse start(String body) throws Exception {
        return mvc.perform(post("/api/memberships/trial").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    void error(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentAsString()).contains(code);
        assertThat(memberships.findIdsByMemberId(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("활성 카드와 체험 자격이 있으면 청구 없이 7일 체험을 시작한다")
    void startsTrialWithRealPaymentQueries() throws Exception {
        // given
        card("ACTIVE");
        // when
        var response = start(REQUEST);
        // then
        assertThat(response.getStatus()).isEqualTo(201);
        long id = mapper.readTree(response.getContentAsString()).path("data").path("membershipId").asLong();
        Membership saved = memberships.findById(id).orElseThrow();
        assertThat(saved.getType()).isEqualTo(MembershipType.TRIAL);
        assertThat(saved.getActivatedAt()).isEqualTo(saved.getStartAt());
        assertThat(saved.getEndAt()).isEqualTo(saved.getStartAt().plusDays(7));
        assertThat(saved.getAutopayConsentedAt()).isEqualTo(saved.getStartAt());
        assertThat(saved.getConsentedAmount()).isEqualTo(3900L);
        assertThat(saved.getGraceEndAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_payment WHERE membership_id = ?",
                Long.class, id)).isZero();
        assertThat(start(REQUEST).getStatus()).isEqualTo(409);
        assertThat(memberships.findIdsByMemberId(member.getId())).hasSize(1);
    }

    @Test
    @DisplayName("카드가 없으면 이용권을 생성하지 않는다")
    void rejectsMissingCard() throws Exception {
        // given, when, then
        error(start(REQUEST), 409, "MEMBERSHIP-005");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REPLACED", "REVOKED", "DELETE_REQUESTED", "DELETED"})
    @DisplayName("활성이 아닌 카드는 체험 시작 조건을 충족하지 않는다")
    void rejectsInactiveCards(String status) throws Exception {
        // given
        card(status);
        // when, then
        error(start(REQUEST), 409, "MEMBERSHIP-005");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"autopayAgreed\":false,\"consentedAmount\":3900,\"consentTextVersion\":\"v1\"}",
            "{\"autopayAgreed\":true,\"consentedAmount\":100,\"consentTextVersion\":\"v1\"}",
            "{\"autopayAgreed\":true,\"consentedAmount\":3900,\"consentTextVersion\":\"\"}"})
    @DisplayName("자동결제 동의가 올바르지 않으면 체험을 생성하지 않는다")
    void rejectsInvalidConsent(String body) throws Exception {
        // given
        card("ACTIVE");
        // when, then
        error(start(body), 400, "MEMBERSHIP-004");
    }

    @ParameterizedTest
    @ValueSource(strings = {"v 1", " v1", "v1 ", "v\t1", "v\u00a01", "v\u30001",
            "123456789012345678901"})
    @DisplayName("동의 문구 버전의 공백·길이 위반은 동일한 동의 오류로 거절한다")
    void rejectsWhitespaceAndLongVersion(String version) throws Exception {
        // given
        card("ACTIVE");
        String body = mapper.writeValueAsString(java.util.Map.of(
                "autopayAgreed", true, "consentedAmount", 3900, "consentTextVersion", version));
        // when, then
        error(start(body), 400, "MEMBERSHIP-004");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "MANUAL_REVIEW", "PENDING"})
    @DisplayName("활성화 없이 종료된 과거 이용권의 미해결 결제도 체험을 차단한다")
    void rejectsUnresolvedPaymentOfOldUnactivatedMembership(String status) throws Exception {
        // given
        card("ACTIVE");
        long oldId = failedMembership(status);
        // when
        var response = start(REQUEST);
        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("MEMBERSHIP-009");
        assertThat(memberships.findIdsByMemberId(member.getId())).containsExactly(oldId);
    }

    @Test
    @DisplayName("활성화 없이 결제가 실패해 정산된 이력은 체험 자격을 소진하지 않는다")
    void permitsSettledUnactivatedHistory() throws Exception {
        // given
        card("ACTIVE");
        failedMembership("FAILED");
        // when, then
        assertThat(start(REQUEST).getStatus()).isEqualTo(201);
    }

    long failedMembership(String status) {
        LocalDateTime at = LocalDateTime.now().minusDays(40).withNano(0);
        jdbc.update("""
                INSERT INTO membership(member_id, type, source, start_at, end_at, auto_renew, terminated_at,
                    created_at, updated_at)
                VALUES (?, 'PAID', 'SUBSCRIBE', ?, ?, false, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, member.getId(), at, at.plusMonths(1), at.plusMinutes(1));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM membership WHERE member_id = ?", Long.class, member.getId());
        jdbc.update("""
                INSERT INTO membership_payment(membership_id, amount, status, billing_date, period_start_date,
                    period_end_date, created_at, updated_at)
                VALUES (?, 3900, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, status, at.toLocalDate(), at.toLocalDate(), at.plusMonths(1).toLocalDate());
        return id;
    }

    @Test
    @DisplayName("탈퇴 전에 발급한 토큰으로 체험을 시작할 수 없다")
    void rejectsWithdrawnMember() throws Exception {
        // given
        member.withdraw(LocalDateTime.now());
        members.flush();
        // when
        var response = start(REQUEST);
        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(memberships.findIdsByMemberId(member.getId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시에 API를 호출해도 실제 카드·정산 검사를 거쳐 한 건만 생성한다")
    void serializesApiRequests() throws Exception {
        // given
        card("ACTIVE");
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        java.util.concurrent.Callable<Integer> attempt = () -> {
            ready.countDown();
            if (!go.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 요청 대기 시간 초과");
            }
            return start(REQUEST).getStatus();
        };
        try {
            var a = pool.submit(attempt);
            var b = pool.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            // when
            go.countDown();
            // then
            assertThat(java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(memberships.findIdsByMemberId(member.getId())).hasSize(1);
        } finally {
            go.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            memberships.deleteAllById(memberships.findIdsByMemberId(member.getId()));
            jdbc.update("DELETE FROM billing_key WHERE member_id = ?", member.getId());
            members.deleteById(member.getId());
        }
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 체험을 시작할 수 없다")
    void requiresAuthentication() throws Exception {
        // given, when
        var response = mvc.perform(post("/api/memberships/trial").contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST)).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(memberships.findIdsByMemberId(member.getId())).isEmpty();
    }
}
