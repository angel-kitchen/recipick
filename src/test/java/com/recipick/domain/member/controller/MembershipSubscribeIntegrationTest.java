package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.request.MembershipSubscribeRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.facade.MembershipSubscribeFacade;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.payment.facade.MembershipChargeExecutor;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

// 접수·멱등·검사는 실제 C-11과 DB를 사용하고 PG 실행 경계만 대체한다.
class MembershipSubscribeIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MembershipSubscribeFacade facade;
    @Autowired TransactionTemplate tx;
    @MockitoBean MembershipChargeExecutor executor;

    Member member;
    String token;
    static final String BODY = """
            {"autopayAgreed":true,"consentedAmount":3900,"consentTextVersion":"v1"}
            """;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 20);
        member = members.saveAndFlush(Member.builder().email(suffix + "@sub.test")
                .phone(suffix).name("구독 회원").passwordHash("hash").build());
        token = tokens.createMemberTokens(member.getId()).accessToken();
        jdbc.update("""
                INSERT INTO billing_key(member_id, portone_billing_key, status, issued_at, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, member.getId(), UUID.randomUUID().toString());
        given(executor.execute(anyLong(), anyLong())).willReturn(MembershipChargeOutcome.NOT_DISPATCHED);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("""
                DELETE a FROM membership_payment_attempt a JOIN membership_payment p
                ON a.membership_payment_id = p.id JOIN membership m ON p.membership_id = m.id
                WHERE m.member_id = ?
                """, member.getId());
        jdbc.update("DELETE p FROM membership_payment p JOIN membership m ON p.membership_id = m.id "
                + "WHERE m.member_id = ?",
                member.getId());
        jdbc.update("DELETE FROM membership WHERE member_id = ?", member.getId());
        jdbc.update("DELETE FROM billing_key WHERE member_id = ?", member.getId());
        members.deleteById(member.getId());
    }

    MockHttpServletResponse subscribe(String key, String body) throws Exception {
        var request = post("/api/memberships/subscribe").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    long countMemberships() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM membership WHERE member_id = ?", Long.class, member.getId());
    }

    @Test
    @DisplayName("구독 접수는 실제 결제 계약을 호출하고 처리 중에는 혜택을 켜지 않는다")
    void acceptsThroughRealPaymentContract() throws Exception {
        // given, when
        var response = subscribe("sub-1", BODY);
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        var data = mapper.readTree(response.getContentAsString()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("IN_PROGRESS");
        long id = data.path("membershipId").asLong();
        assertThat(jdbc.queryForObject("SELECT activated_at FROM membership WHERE id = ?",
                LocalDateTime.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT consented_amount FROM membership WHERE id = ?", Long.class, id))
                .isEqualTo(3900L);
        assertThat(jdbc.queryForObject("SELECT autopay_consented_at FROM membership WHERE id = ?",
                LocalDateTime.class, id)).isNotNull();
    }

    @Test
    @DisplayName("같은 키의 재요청은 기존 이용권·회차·시도를 재사용한다")
    void reusesExistingReceipt() throws Exception {
        // given
        var first = mapper.readTree(subscribe("SUB-1", BODY).getContentAsString()).path("data");
        // when
        var second = mapper.readTree(subscribe("sub-1", BODY).getContentAsString()).path("data");
        // then
        assertThat(second).isEqualTo(first);
        assertThat(countMemberships()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_payment WHERE idempotency_key = ?",
                Long.class, member.getId() + ":sub-1")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM membership_payment_attempt a JOIN membership_payment p
                ON a.membership_payment_id = p.id WHERE p.idempotency_key = ?
                """, Long.class, member.getId() + ":sub-1")).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"PAID,PAID", "FAILED,FAILED", "UNKNOWN,UNKNOWN", "MANUAL_REVIEW,UNKNOWN"})
    @DisplayName("같은 키의 확정·결과 불명 상태를 200 응답으로 전달하며 새 청구를 시작하지 않는다")
    void returnsStoredStatus(String stored, String expected) throws Exception {
        // given
        subscribe("sub-1", BODY);
        jdbc.update("UPDATE membership_payment SET status = ? WHERE idempotency_key = ?",
                stored, member.getId() + ":sub-1");
        clearInvocations(executor);
        // when
        var response = subscribe("sub-1", BODY);
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(response.getContentAsString()).path("data").path("status").asText())
                .isEqualTo(expected);
        verifyNoInteractions(executor);
        assertThat(countMemberships()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키로 동의 내용을 바꾸면 활성 이용권 검사보다 먼저 멱등 충돌로 거절한다")
    void rejectsChangedConsentOnSameKey() throws Exception {
        // given
        subscribe("sub-1", BODY);
        // when
        var response = subscribe("sub-1", BODY.replace("v1", "v2"));
        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("COMMON-011");
        assertThat(countMemberships()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v 1", "", "v\t1", "123456789012345678901"})
    @DisplayName("잘못된 동의 버전은 접수 전에 MEMBERSHIP-004로 거절한다")
    void rejectsInvalidConsentVersion(String version) throws Exception {
        // given
        String body = mapper.writeValueAsString(Map.of("autopayAgreed", true,
                "consentedAmount", 3900, "consentTextVersion", version));
        // when
        var response = subscribe("sub-1", body);
        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MEMBERSHIP-004");
        assertThat(countMemberships()).isZero();
        verifyNoInteractions(executor);
    }

    @Test
    @DisplayName("멱등 키가 없으면 접수하지 않고 COMMON-007을 반환한다")
    void requiresKey() throws Exception {
        // given, when
        var response = subscribe(null, BODY);
        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("COMMON-007");
        assertThat(countMemberships()).isZero();
    }

    @Test
    @DisplayName("바깥 트랜잭션이 있으면 PG 실행 전에 구독 진입을 거절한다")
    void rejectsOuterTransaction() {
        // given, when, then
        assertThatThrownBy(() -> tx.execute(status -> facade.subscribe(member.getId(),
                new MembershipSubscribeRequest(true, 3900L, "v1"), "sub-1")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(countMemberships()).isZero();
        verifyNoInteractions(executor);
    }

    @Test
    @DisplayName("자동결제 미동의 요청은 결제 접수 전에 거절한다")
    void requiresAgreement() throws Exception {
        // given, when
        var response = subscribe("sub-1", BODY.replace("true", "false"));
        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MEMBERSHIP-004");
        assertThat(countMemberships()).isZero();
    }

    @Test
    @DisplayName("활성 카드가 없으면 실제 결제 계약의 오류를 그대로 반환한다")
    void rejectsMissingCard() throws Exception {
        // given
        jdbc.update("DELETE FROM billing_key WHERE member_id = ?", member.getId());
        // when
        var response = subscribe("sub-1", BODY);
        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("MEMBERSHIP-005");
        assertThat(countMemberships()).isZero();
    }

    @Test
    @DisplayName("미인증 요청은 구독을 접수할 수 없다")
    void requiresAuthentication() throws Exception {
        // given, when
        var response = mvc.perform(post("/api/memberships/subscribe").header("Idempotency-Key", "sub-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(countMemberships()).isZero();
    }
}
