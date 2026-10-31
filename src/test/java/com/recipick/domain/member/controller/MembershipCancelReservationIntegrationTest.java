package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.MembershipCancelReservationService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MembershipCancelReservationIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipService membershipService;
    @Autowired MembershipCancelReservationService reservations;
    @Autowired JwtTokenProvider tokens;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    Member member;
    Member other;
    String token;
    LocalDateTime now;

    @BeforeEach
    void setup() {
        member = newMember();
        other = newMember();
        token = tokens.createMemberTokens(member.getId()).accessToken();
        now = LocalDateTime.now(clock).withNano(0);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM membership WHERE member_id IN (?, ?)", member.getId(), other.getId());
        members.deleteAllById(List.of(member.getId(), other.getId()));
    }

    private Member newMember() {
        String suffix = UUID.randomUUID().toString();
        return members.saveAndFlush(Member.builder().email(suffix + "@cancel.test")
                .name("해지 예약 회원").phone(suffix.substring(0, 20)).passwordHash("hash").build());
    }

    private Membership membership(Member owner, MembershipType type, MembershipSource source,
            LocalDateTime start, LocalDateTime end) {
        return Membership.builder().member(owner).type(type).source(source).startAt(start).activatedAt(start).endAt(end)
                .autoRenew(source != MembershipSource.ADMIN_GRANT)
                .billingAnchorDate(source == MembershipSource.TRIAL ? null : start.toLocalDate())
                .consentedAmount(3900L).consentTextVersion("v1").autopayConsentedAt(start).build();
    }

    private Membership active() {
        return memberships.saveAndFlush(membership(member, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                now.minusDays(1), now.plusDays(29)));
    }

    private MockHttpServletResponse reserve(Long id) throws Exception {
        return mvc.perform(post("/api/memberships/{id}/cancel-reservation", id)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo(code);
    }

    @ParameterizedTest
    @CsvSource({"PAID,SUBSCRIBE,PAYMENT_PENDING", "TRIAL,TRIAL,EXPIRED"})
    @DisplayName("결제 대기와 전환 대기도 해지 예약하고 응답 상태를 구분한다")
    void reservesPendingMembership(MembershipType type, MembershipSource source, String status) throws Exception {
        // given
        LocalDateTime start = now.minusDays(8);
        Membership pending = Membership.builder().member(member).type(type).source(source)
                .startAt(start).activatedAt(type == MembershipType.TRIAL ? start : null)
                .endAt(now.minusDays(1)).autoRenew(true).build();
        memberships.saveAndFlush(pending);
        // when
        var response = reserve(pending.getId());
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("status").asText()).isEqualTo(status);
        assertThat(data(response).path("autoRenew").asBoolean()).isFalse();
        assertThat(data(response).path("cancelReservedAt").isNull()).isFalse();
        assertThat(membershipService.isActive(member.getId())).isFalse();
    }

    @Test
    @DisplayName("유예 중 해지 예약은 유예를 지워 즉시 만료시킨다")
    void endsGraceOnReservation() throws Exception {
        // given
        Membership value = membership(member, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                now.minusDays(31), now.minusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(value, "graceEndAt", now.plusDays(2));
        memberships.saveAndFlush(value);
        assertThat(membershipService.isActive(member.getId())).isTrue();
        // when
        var response = reserve(value.getId());
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("status").asText()).isEqualTo("EXPIRED");
        assertThat(memberships.findById(value.getId()).orElseThrow().getGraceEndAt()).isNull();
        assertThat(membershipService.isActive(member.getId())).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"TRIAL,TRIAL", "PAID,SUBSCRIBE", "PAID,TRIAL_CONVERT"})
    @DisplayName("체험·구독·체험 전환의 해지 예약은 자동갱신만 끄고 기존 기간·동의·혜택을 유지한다")
    void reservesWithoutEndingBenefits(MembershipType type, MembershipSource source) throws Exception {
        // given
        Membership original = memberships.saveAndFlush(membership(member, type, source,
                now.minusDays(1), now.plusDays(6)));
        // when
        var response = reserve(original.getId());
        var me = mvc.perform(get("/api/memberships/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("membershipId").asLong()).isEqualTo(original.getId());
        assertThat(data(response).path("status").asText()).isEqualTo("CANCEL_RESERVED");
        assertThat(data(response).path("autoRenew").asBoolean()).isFalse();
        assertThat(data(response).path("cancelReservedAt").asText()).endsWith("+09:00");
        Membership saved = memberships.findById(original.getId()).orElseThrow();
        assertThat(saved.getType()).isEqualTo(type);
        assertThat(saved.getSource()).isEqualTo(source);
        assertThat(saved.getStartAt()).isEqualTo(original.getStartAt());
        assertThat(saved.getEndAt()).isEqualTo(original.getEndAt());
        assertThat(saved.getBillingAnchorDate()).isEqualTo(original.getBillingAnchorDate());
        assertThat(saved.getConsentedAmount()).isEqualTo(3900L);
        assertThat(saved.getConsentTextVersion()).isEqualTo("v1");
        assertThat(saved.getAutopayConsentedAt()).isEqualTo(original.getAutopayConsentedAt());
        assertThat(saved.getTerminatedAt()).isNull();
        assertThat(saved.getCancelReservedAt()).isNotNull();
        assertThat(data(me).path("active").asBoolean()).isTrue();
        assertThat(membershipService.getTypeAt(member.getId(), now)).contains(type);
    }

    @Test
    @DisplayName("반복 해지 예약은 최초 예약 시각을 덮어쓰지 않는다")
    void preservesFirstReservationOnRetry() throws Exception {
        // given
        Membership membership = active();
        // when
        var first = reserve(membership.getId());
        LocalDateTime firstTime = memberships.findById(membership.getId()).orElseThrow().getCancelReservedAt();
        var repeated = reserve(membership.getId());
        // then
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(repeated.getStatus()).isEqualTo(200);
        assertThat(data(repeated)).isEqualTo(data(first));
        assertThat(memberships.findById(membership.getId()).orElseThrow().getCancelReservedAt()).isEqualTo(firstTime);
    }

    @Test
    @DisplayName("다른 회원과 없는 이용권은 같은 404로 거절하고 상대 이용권을 변경하지 않는다")
    void rejectsForeignAndMissingMemberships() throws Exception {
        // given
        Membership foreign = memberships.saveAndFlush(membership(other, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                now.minusDays(1), now.plusDays(29)));
        // when, then
        assertError(reserve(foreign.getId()), 404, "MEMBERSHIP-006");
        assertError(reserve(Long.MAX_VALUE), 404, "MEMBERSHIP-006");
        assertThat(memberships.findById(foreign.getId()).orElseThrow().isAutoRenew()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FUTURE", "EXPIRED", "TERMINATED"})
    @DisplayName("미시작·만료·즉시 종료된 이용권의 예약을 거절한다")
    void rejectsInactiveMembership(String state) throws Exception {
        // given
        Membership membership = switch (state) {
            case "FUTURE" -> membership(member, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                    now.plusDays(1), now.plusDays(31));
            case "EXPIRED" -> membership(member, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                    now.minusDays(31), now.minusDays(1));
            default -> membership(member, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                    now.minusDays(1), now.plusDays(29));
        };
        if (state.equals("TERMINATED")) {
            membership.terminateAfterRefund(now.minusHours(1));
        }
        memberships.saveAndFlush(membership);
        // when, then
        assertError(reserve(membership.getId()), 404, "MEMBERSHIP-006");
        assertThat(memberships.findById(membership.getId()).orElseThrow().getCancelReservedAt()).isNull();
    }

    @Test
    @DisplayName("자동갱신 없는 관리자 지급 이용권은 해지 예약 대상이 아니다")
    void rejectsAdminGrantedMembership() throws Exception {
        // given
        Membership grant = memberships.saveAndFlush(membership(member, MembershipType.PAID, MembershipSource.ADMIN_GRANT,
                now.minusDays(1), now.plusDays(29)));
        // when, then
        assertError(reserve(grant.getId()), 409, "MEMBERSHIP-007");
        assertThat(memberships.findById(grant.getId()).orElseThrow().getCancelReservedAt()).isNull();
    }

    @Test
    @DisplayName("만료된 이전 이용권의 재요청으로 새 이용권을 해지하지 않는다")
    void retryDoesNotCancelNewMembership() throws Exception {
        // given
        Membership old = membership(member, MembershipType.TRIAL, MembershipSource.TRIAL,
                now.minusDays(10), now.minusDays(3));
        old.reserveCancellation(now.minusDays(4));
        memberships.saveAndFlush(old);
        Membership current = active();
        // when, then
        assertError(reserve(old.getId()), 404, "MEMBERSHIP-006");
        Membership unchanged = memberships.findById(current.getId()).orElseThrow();
        assertThat(unchanged.isAutoRenew()).isTrue();
        assertThat(unchanged.getCancelReservedAt()).isNull();
    }

    @Test
    @DisplayName("미인증·관리자·탈퇴한 회원은 해지 예약을 할 수 없다")
    void requiresActiveMemberAuthentication() throws Exception {
        // given
        Membership membership = active();
        String path = "/api/memberships/" + membership.getId() + "/cancel-reservation";
        String admin = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();
        // when, then
        assertThat(mvc.perform(post(path)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post(path).header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        jdbc.update("UPDATE member SET withdrawn_at = CURRENT_TIMESTAMP WHERE id = ?", member.getId());
        assertError(reserve(membership.getId()), 401, "AUTH-011");
        assertThat(memberships.findById(membership.getId()).orElseThrow().getCancelReservedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("양수가 아닌 이용권 ID는 입력 오류로 거절한다")
    void rejectsInvalidId(long id) throws Exception {
        // given, when, then
        assertError(reserve(id), 400, "COMMON-001");
    }

    @Test
    @DisplayName("같은 트랜잭션의 후속 처리가 실패하면 해지 예약도 롤백된다")
    void rollsBackReservation() {
        // given
        Membership membership = active();
        // when, then
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            reservations.reserve(member.getId(), membership.getId());
            throw new IllegalStateException("커밋 전 검증 실패");
        })).isInstanceOf(IllegalStateException.class);
        Membership unchanged = memberships.findById(membership.getId()).orElseThrow();
        assertThat(unchanged.isAutoRenew()).isTrue();
        assertThat(unchanged.getCancelReservedAt()).isNull();
    }

    @Test
    @DisplayName("동시 예약 6건은 모두 성공하고 하나의 최초 예약 시각과 기존 기간을 유지한다")
    void serializesConcurrentReservations() throws Exception {
        // given
        Membership membership = active();
        var executor = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("시작 대기 초과");
                    }
                    return reserve(membership.getId());
                }));
            }
            // when
            start.countDown();
            List<JsonNode> results = new ArrayList<>();
            for (var future : futures) {
                var response = future.get(20, TimeUnit.SECONDS);
                assertThat(response.getStatus()).isEqualTo(200);
                results.add(data(response));
            }
            // then
            assertThat(results).allSatisfy(result -> assertThat(result).isEqualTo(results.get(0)));
            Membership saved = memberships.findById(membership.getId()).orElseThrow();
            assertThat(saved.getCancelReservedAt()).isNotNull();
            assertThat(saved.isAutoRenew()).isFalse();
            assertThat(saved.getEndAt()).isEqualTo(membership.getEndAt());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("회원 잠금 시간 초과는 COMMON-010이며 이용권을 변경하지 않는다")
    void returnsMemberLockTimeout() throws Exception {
        // given
        Membership membership = active();
        var executor = Executors.newSingleThreadExecutor();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            members.findByIdForUpdate(member.getId()).orElseThrow();
            locked.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("잠금 해제 대기 초과");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            // when, then
            assertError(reserve(membership.getId()), 409, "COMMON-010");
            assertThat(memberships.findById(membership.getId()).orElseThrow().getCancelReservedAt()).isNull();
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
}
