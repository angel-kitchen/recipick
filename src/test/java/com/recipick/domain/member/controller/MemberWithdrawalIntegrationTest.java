package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.request.MemberWithdrawRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.facade.MemberWithdrawalProcessor;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MemberAddressRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.repository.TrialBlockRepository;
import com.recipick.domain.member.service.TrialPhoneHasher;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.member.service.TrialBlockService;
import com.recipick.domain.member.service.MemberWithdrawalService;
import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.order.service.OrderService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MemberWithdrawalIntegrationTest extends IntegrationTestSupport {
    @Autowired MemberRepository members;
    @Autowired MemberAddressRepository addresses;
    @Autowired MembershipRepository histories;
    @Autowired TrialBlockRepository blocks;
    @Autowired TrialPhoneHasher hasher;
    @Autowired MemberService memberService;
    @Autowired OrderService orders;
    @Autowired MembershipService memberships;
    @Autowired TrialBlockService trialBlocks;
    @Autowired MemberWithdrawalService withdrawal;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired RedisRefreshTokenStore sessions;
    @Autowired JdbcTemplate jdbc;
    Member member;
    String access;
    String hash;
    final String password = "Original1!";

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        member = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
                .name("테스트").passwordHash(encoder.encode(password)).build());
        hash = hasher.hash(member.getPhone());
        access = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @AfterEach
    void cleanup() {
        sessions.deleteSessions(sessions.findSessionKeys("MEMBER", member.getId()));
        jdbc.update("UPDATE member SET selected_address_id = NULL WHERE id = ?", member.getId());
        jdbc.update("DELETE FROM member_address WHERE member_id = ?", member.getId());
        jdbc.update("DELETE FROM membership WHERE member_id = ?", member.getId());
        jdbc.update("DELETE FROM trial_block WHERE phone_hash = ?", hash);
        members.deleteById(member.getId());
    }

    private MemberWithdrawalProcessor processor(WithdrawalSettlementChecker checker) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("checker", new WithdrawalSettlementChecker() {
            @Override
            public java.util.Set<Scope> supportedScopes() {
                return java.util.EnumSet.allOf(Scope.class);
            }

            @Override
            public void requireSettled(Long memberId, Long acknowledgedPointBalance) {
                checker.requireSettled(memberId, acknowledgedPointBalance);
            }
        });
        return new MemberWithdrawalProcessor(memberService, orders, memberships, trialBlocks, withdrawal,
                factory.getBeanProvider(WithdrawalSettlementChecker.class), encoder, clock);
    }

    private void withdraw(WithdrawalSettlementChecker checker) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                processor(checker).withdraw(member.getId(), new MemberWithdrawRequest(password, 0L)));
    }

    @Test
    @DisplayName("정산 확인이 연결되지 않으면 API는 503이며 회원 정보를 변경하지 않는다")
    void refusesWithoutSettlementContract() throws Exception {
        // given, when
        var response = mvc.perform(post("/api/members/me/withdraw").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Original1!\",\"acknowledgedPointBalance\":0}"))
                .andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("MEMBER-008");
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("비밀번호가 다르면 탈퇴를 거절한다")
    void refusesIncorrectPassword() throws Exception {
        // given, when
        var response = mvc.perform(post("/api/members/me/withdraw").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Wrong1!\",\"acknowledgedPointBalance\":0}"))
                .andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MEMBER-005");
    }

    @Test
    @DisplayName("비로그인 탈퇴 요청을 거절한다")
    void refusesAnonymous() throws Exception {
        // given, when
        var response = mvc.perform(post("/api/members/me/withdraw").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Original1!\",\"acknowledgedPointBalance\":0}")).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("탈퇴는 현재 배송지 FK를 해제하고 개인정보와 배송지를 파기하며 기존 토큰을 거절한다")
    void anonymizesAndRejectsAccess() throws Exception {
        // given
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member locked = memberService.getMemberForUpdate(member.getId());
            MemberAddress address = addresses.saveAndFlush(MemberAddress.builder().member(locked)
                    .address("기본 주소").detailAddress("상세 주소").legalDongCode("1111010100").build());
            locked.selectAddress(address, null);
        });
        // when
        withdraw((id, balance) -> {});
        // then
        Member result = members.findById(member.getId()).orElseThrow();
        assertThat(result.isWithdrawn()).isTrue();
        assertThat(result.getEmail()).isNotEqualTo(member.getEmail());
        assertThat(result.getName()).isNotEqualTo(member.getName());
        assertThat(result.getPhone()).isNotEqualTo(member.getPhone());
        assertThat(result.getPasswordHash()).isEqualTo("!withdrawn");
        assertThat(result.getSelectedAddress()).isNull();
        assertThat(result.getRefreshTokenVersion()).isEqualTo(1);
        assertThat(addresses.countByMemberId(member.getId())).isZero();
        assertThat(mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + access))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(blocks.findAll()).noneMatch(block -> block.getPhoneHash().equals(hash));
    }

    @Test
    @DisplayName("만료된 관리자 지급 이력은 보존하고 탈퇴 번호의 체험을 차단한다")
    void preservesHistoryAndBlocksTrial() {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        Membership history = histories.saveAndFlush(Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.ADMIN_GRANT).startAt(now.minusDays(31)).endAt(now.minusDays(1)).build());
        // when
        withdraw((id, balance) -> {});
        // then
        assertThat(histories.existsById(history.getId())).isTrue();
        assertThat(blocks.existsByPhoneHashAndExpiresAtAfter(hash, now)).isTrue();
    }

    @Test
    @DisplayName("활성 멤버십이 있으면 정산 조회 전에 탈퇴를 거절한다")
    void refusesActiveMembership() {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        histories.saveAndFlush(Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.ADMIN_GRANT).startAt(now.minusDays(1)).endAt(now.plusDays(1)).build());
        // when, then
        assertThatThrownBy(() -> withdraw((id, balance) -> { throw new AssertionError("정산 조회 전에 거절해야 함"); }))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.WITHDRAW_BLOCKED_MEMBERSHIP));
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("정산 확인 실패 시 회원과 배송지·체험 기록을 변경하지 않는다")
    void refusesUnsettledPayment() {
        // given, when, then
        assertThatThrownBy(() -> withdraw((id, balance) -> { throw new IllegalStateException("정산 미완료"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
        assertThat(blocks.findAll()).noneMatch(block -> block.getPhoneHash().equals(hash));
    }

    @Test
    @DisplayName("후속 작업 실패로 롤백되면 탈퇴와 체험 차단 기록 모두 취소된다")
    void rollsBackAllChanges() {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        histories.saveAndFlush(Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.ADMIN_GRANT).startAt(now.minusDays(31)).endAt(now.minusDays(1)).build());
        // when, then
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            processor((id, balance) -> {}).withdraw(member.getId(), new MemberWithdrawRequest(password, 0L));
            throw new IllegalStateException("커밋 전 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
        assertThat(blocks.findAll()).noneMatch(block -> block.getPhoneHash().equals(hash));
    }
    @Test
    @DisplayName("동시 탈퇴는 회원 행 잠금으로 한 요청만 성공한다")
    void serializesConcurrentWithdrawals() throws Exception {
        // given
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var errors = new java.util.concurrent.CopyOnWriteArrayList<BusinessException>();
        try {
            // when
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        withdraw((id, balance) -> {});
                        successes.incrementAndGet();
                    } catch (BusinessException e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(15, java.util.concurrent.TimeUnit.SECONDS);
            }
            // then
            assertThat(successes.get()).isEqualTo(1);
            assertThat(errors).singleElement().satisfies(error ->
                    assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "null", "-1", "9223372036854775808"})
    @DisplayName("확인 잔액의 누락·null·음수·Long 범위 초과는 탈퇴나 세션 변경 전에 거절한다")
    void rejectsInvalidAcknowledgedBalance(String balanceJson) throws Exception {
        // given
        sessions.save("MEMBER", member.getId(), "preserved", "refresh", java.time.Duration.ofDays(14));
        var beforeSessions = sessions.findSessionKeys("MEMBER", member.getId());
        String body = "{\"password\":\"Original1!\""
                + (balanceJson.equals("MISSING") ? "" : ",\"acknowledgedPointBalance\":" + balanceJson) + "}";
        // when
        var response = mvc.perform(post("/api/members/me/withdraw")
                .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(400);
        Member unchanged = members.findById(member.getId()).orElseThrow();
        assertThat(unchanged.isWithdrawn()).isFalse();
        assertThat(unchanged.getRefreshTokenVersion()).isEqualTo(member.getRefreshTokenVersion());
        assertThat(unchanged.getEmail()).isEqualTo(member.getEmail());
        assertThat(sessions.findSessionKeys("MEMBER", member.getId())).containsExactlyElementsOf(beforeSessions);
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {2500L, Long.MAX_VALUE})
    @DisplayName("0보다 큰 확인 잔액도 입력은 유효하지만 정산 미연결 차단을 우회하지 못한다")
    void acceptsNonnegativeBalanceWithoutOpeningWithdrawal(long balance) throws Exception {
        // given, when
        String body = "{\"password\":\"Original1!\",\"acknowledgedPointBalance\":" + balance + "}";
        var response = mvc.perform(post("/api/members/me/withdraw")
                .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("MEMBER-008");
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("탈퇴 요청 문자열은 비밀번호와 확인 잔액을 노출하지 않는다")
    void redactsWithdrawalRequest() {
        // given
        var request = new MemberWithdrawRequest("Original1!", 2500L);
        // when, then
        assertThat(request.toString()).isEqualTo("MemberWithdrawRequest[REDACTED]");
    }

}
