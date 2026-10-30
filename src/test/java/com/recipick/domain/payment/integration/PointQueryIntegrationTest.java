package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.PointHistory;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.PointHistoryRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 회원·관리자 포인트 조회를 실제 DB·보안·감사까지 이어서 본다. 날짜는 실제 시계의 오늘을 기준으로 상대값으로 만든다.
// 회원 잔액 조회가 쓰기 쪽 집계(MEMBER_POINT.balance)를 보지 않고, 조회 때문에 DB가 바뀌지 않는다는 것이 핵심이다
class PointQueryIntegrationTest extends IntegrationTestSupport {

    // 포인트 테이블과 주문은 외래키로 묶여 있지 않아 주문 ID만 쓴다. 실제 주문과 겹치지 않게 크게 띄운다
    private static final long ORDER_OFFSET = 9_700_000_000L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private MemberPointRepository memberPointRepository;
    @Autowired
    private PointEarningRepository pointEarningRepository;
    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    private Member member;
    private Long otherMemberId;
    private Admin superAdmin;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
            .email(UUID.randomUUID() + "@pq.test")
            .name("포인트 조회 회원")
            .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
            .passwordHash("hash")
            .build());
        // 이력만 남기는 다른 회원. 포인트 테이블은 회원과 외래키가 없어 ID만 쓴다
        otherMemberId = member.getId() + 1_000_000_000L;
        superAdmin = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .passwordHash("hash")
            .role(AdminRole.SUPER_ADMIN)
            .active(true)
            .build());
        now = LocalDateTime.now(clock);
    }

    // 외래키 역순으로 포인트 → 감사 로그 → 관리자 → 회원 순으로 지운다
    @AfterEach
    void cleanUp() {
        for (Long memberId : new Long[] {member.getId(), otherMemberId}) {
            jdbc.update("DELETE FROM point_history WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM point_earning WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        }
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", superAdmin.getId());
        adminRepository.deleteById(superAdmin.getId());
        memberRepository.deleteById(member.getId());
    }

    @Test
    @DisplayName("만료 배치가 아직 돌지 않아 집계 잔액이 1,500P여도 조회는 실제로 쓸 수 있는 500P만 돌려주고 DB는 그대로다")
    void balanceExcludesUnprocessedExpiredPoints() throws Exception {
        // given — 어제 만료됐지만 아직 COMPLETED인 1,000P와 유효 500P. 집계 잔액은 둘의 합이다
        PointEarning stale = completed(member.getId(), 1L, 1_000L, now.minusYears(1).minusDays(1));
        completed(member.getId(), 2L, 500L, now.minusDays(1));
        memberPointRepository.save(MemberPoint.builder().memberId(member.getId()).balance(1_500L).build());

        // when
        MockHttpServletResponse response = memberGet("/api/points");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("availableBalance").asLong()).isEqualTo(500L);
        assertThat(data(response).path("nearestExpiringAmount").asLong()).isEqualTo(500L);
        assertThat(jdbc.queryForObject("SELECT balance FROM member_point WHERE member_id = ?", Long.class,
            member.getId())).isEqualTo(1_500L);
        assertThat(jdbc.queryForObject("SELECT status FROM point_earning WHERE id = ?", String.class, stale.getId()))
            .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT remaining_amount FROM point_earning WHERE id = ?", Long.class,
            stale.getId())).isEqualTo(1_000L);
        assertThat(historyCount(member.getId())).isZero();
    }

    @Test
    @DisplayName("가장 가까운 만료는 가장 이른 만료일에 만료되는 적립 건 잔량을 합쳐 보여준다")
    void nearestExpirySumsSameDate() throws Exception {
        // given — 60일 뒤 500P·700P, 90일 뒤 300P
        LocalDate in60Days = now.toLocalDate().plusDays(60);
        completed(member.getId(), 1L, 500L, now.minusYears(1).plusDays(60));
        completed(member.getId(), 2L, 700L, now.minusYears(1).plusDays(60));
        completed(member.getId(), 3L, 300L, now.minusYears(1).plusDays(90));
        memberPointRepository.save(MemberPoint.builder().memberId(member.getId()).balance(1_500L).build());

        // when
        JsonNode data = data(memberGet("/api/points"));

        // then
        assertThat(data.path("availableBalance").asLong()).isEqualTo(1_500L);
        assertThat(data.path("nearestExpiringAmount").asLong()).isEqualTo(1_200L);
        assertThat(data.path("nearestExpiresOn").asText()).isEqualTo(in60Days.toString());
    }

    @Test
    @DisplayName("적립 내역은 적립 예정까지 모두 최신순으로 보여주고, 배치 전 만료 건의 남은 양은 0·사용 불가로 보여준다")
    void earningsShowDateAwareRemaining() throws Exception {
        // given
        PointEarning stale = completed(member.getId(), 1L, 1_000L, now.minusYears(1).minusDays(1));
        PointEarning valid = completed(member.getId(), 2L, 500L, now.minusDays(1));
        PointEarning pending = pointEarningRepository.save(
            PointEarning.pending(member.getId(), ORDER_OFFSET + member.getId() * 10 + 3, 50L));

        // when
        JsonNode content = data(memberGet("/api/points/earnings")).path("content");

        // then
        assertThat(content).hasSize(3);
        assertThat(content.get(0).path("earningId").asLong()).isEqualTo(pending.getId());
        assertThat(content.get(0).path("status").asText()).isEqualTo("PENDING");
        assertThat(content.get(1).path("earningId").asLong()).isEqualTo(valid.getId());
        assertThat(content.get(1).path("availableRemainingAmount").asLong()).isEqualTo(500L);
        assertThat(content.get(1).path("usable").asBoolean()).isTrue();
        assertThat(content.get(2).path("earningId").asLong()).isEqualTo(stale.getId());
        assertThat(content.get(2).path("availableRemainingAmount").asLong()).isZero();
        assertThat(content.get(2).path("usable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("이력은 전체·주문별로 볼 수 있고, 다른 회원의 주문 ID로 조회하면 빈 페이지다")
    void historyFiltersByOrderWithinMember() throws Exception {
        // given — 주문1 이력 2건, 주문2 이력 1건, 다른 회원 주문3 이력 1건
        PointEarning first = pointEarningRepository.save(PointEarning.pending(member.getId(), orderId(1), 50L));
        pointHistoryRepository.save(PointHistory.earnPending(first));
        first.complete(now);
        pointEarningRepository.save(first);
        pointHistoryRepository.save(PointHistory.earnCompleted(first));
        pointHistoryRepository.save(PointHistory.earnCompleted(completed(member.getId(), 2L, 30L, now)));
        PointEarning others = PointEarning.pending(otherMemberId, orderId(3), 40L);
        others.complete(now);
        pointHistoryRepository.save(PointHistory.earnCompleted(pointEarningRepository.save(others)));

        // when
        JsonNode all = data(memberGet("/api/points/history"));
        JsonNode firstOrder = data(memberGet("/api/points/history?orderId=" + orderId(1)));
        JsonNode othersOrder = data(memberGet("/api/points/history?orderId=" + orderId(3)));

        // then
        assertThat(all.path("totalElements").asLong()).isEqualTo(3L);
        assertThat(firstOrder.path("totalElements").asLong()).isEqualTo(2L);
        assertThat(firstOrder.path("content").get(0).path("type").asText()).isEqualTo("EARN_COMPLETED");
        assertThat(firstOrder.path("content").get(0).path("earningId").asLong()).isEqualTo(first.getId());
        assertThat(othersOrder.path("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("포인트를 다뤄 본 적 없는 회원은 404가 아니라 잔액 0·만료 없음·빈 적립 내역·빈 이력이다")
    void memberWithoutPoint() throws Exception {
        // when
        MockHttpServletResponse balance = memberGet("/api/points");
        JsonNode earnings = data(memberGet("/api/points/earnings"));
        JsonNode history = data(memberGet("/api/points/history"));

        // then
        assertThat(balance.getStatus()).isEqualTo(200);
        assertThat(data(balance).path("availableBalance").asLong()).isZero();
        assertThat(data(balance).path("nearestExpiringAmount").asLong()).isZero();
        assertThat(data(balance).path("nearestExpiresOn").isNull()).isTrue();
        assertThat(earnings.path("totalElements").asLong()).isZero();
        assertThat(history.path("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("SUPER_ADMIN이 회원 포인트를 조회하면 회원 조회와 같은 실제 사용 가능 잔액을 주고, 이력이 0건이어도 조회마다 감사를 남긴다")
    void adminQueriesAreAudited() throws Exception {
        // given — 집계 1,500P 중 실제 사용 가능 500P
        completed(member.getId(), 1L, 1_000L, now.minusYears(1).minusDays(1));
        completed(member.getId(), 2L, 500L, now.minusDays(1));
        memberPointRepository.save(MemberPoint.builder().memberId(member.getId()).balance(1_500L).build());

        // when
        MockHttpServletResponse balance = adminGet("/api/admin/members/" + member.getId() + "/points");
        MockHttpServletResponse history = adminGet("/api/admin/members/" + member.getId() + "/points/history");

        // then
        assertThat(balance.getStatus()).isEqualTo(200);
        assertThat(data(balance).path("availableBalance").asLong()).isEqualTo(500L);
        assertThat(history.getStatus()).isEqualTo(200);
        assertThat(data(history).path("totalElements").asLong()).isZero();
        assertThat(auditCount("POINT_BALANCE_VIEW")).isEqualTo(1);
        assertThat(auditCount("POINT_HISTORY_VIEW")).isEqualTo(1);
    }

    // 확정 시각으로부터 1년 뒤가 만료일이다
    private PointEarning completed(Long memberId, long seq, long amount, LocalDateTime confirmedAt) {
        PointEarning earning = PointEarning.pending(memberId, orderId(seq), amount);
        earning.complete(confirmedAt);
        return pointEarningRepository.save(earning);
    }

    private long orderId(long seq) {
        return ORDER_OFFSET + member.getId() * 10 + seq;
    }

    private MockHttpServletResponse memberGet(String path) throws Exception {
        String token = tokenProvider.createMemberTokens(member.getId()).accessToken();
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn().getResponse();
    }

    private MockHttpServletResponse adminGet(String path) throws Exception {
        String token = tokenProvider.createTokens(
            new AuthPrincipal(SubjectType.ADMIN, superAdmin.getId(), "SUPER_ADMIN", null)).accessToken();
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn().getResponse();
    }

    private int historyCount(Long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ?", Integer.class, memberId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ? AND action = ?",
            Integer.class, superAdmin.getId(), action);
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }
}
