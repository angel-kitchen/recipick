package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.service.TrialPhoneHasher;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

// 탈퇴 API(POST /api/members/me/withdraw)를 결제 Checker 두 개(PRODUCT_PAYMENT·POINTS)까지 끝까지 태운다.
// 운영 구성에는 MEMBERSHIP_PAYMENT·AUTO_BILLING Checker가 아직 없어 MEMBER-008로 끝나므로(채빈님 테스트가 그대로 확인),
// 이 테스트만 테스트 전용 스텁 Checker 두 개를 더해 네 Scope를 채운다. 스텁은 이 클래스의 컨텍스트에만 있다.
// API가 각자 커밋하므로 테스트 트랜잭션을 쓰지 않고, 만든 행은 끝나고 직접 지운다.
// 주문·결제는 회원과 외래키로 묶이지 않아 회원 ID만 쓰고, 포인트 행도 회원 ID로 묶인다
@Import(WithdrawalSettlementTestCheckers.class)
class WithdrawalSettlementApiIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Original1!";
    private static final long MART_ID = 1L;
    private static final long AMOUNT = 23_800L;
    private static final long PENDING_EARNING = 102L;
    // 적립 건의 주문 ID는 실제 주문과 겹치지 않게 크게 띄운다(point_earning.order_id UNIQUE)
    private static final long LOT_ORDER_OFFSET = 9_500_000_000L;

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    TrialPhoneHasher hasher;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    RedisRefreshTokenStore sessions;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    OrderRepository orders;

    @Autowired
    PaymentRepository payments;

    @Autowired
    RefundRepository refunds;

    @Autowired
    MemberPointRepository memberPoints;

    @Autowired
    PointEarningRepository earnings;

    @Autowired
    PointCommandService pointCommandService;

    @Autowired
    WithdrawalSettlementTestCheckers.SettlementGate gate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    private Member member;
    private String access;
    private String phoneHash;
    private long lotSequence;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        member = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
            .name("탈퇴정산테스트").passwordHash(encoder.encode(PASSWORD)).build());
        phoneHash = hasher.hash(member.getPhone());
        access = tokens.createMemberTokens(member.getId()).accessToken();
        lotSequence = LOT_ORDER_OFFSET + member.getId() * 100;
    }

    // 손잡이를 풀고, 외래키 역순(포인트 이력 → 사용 → 적립 → 회원 포인트, 환불 → 결제 → 주문, 체험 차단, 세션 → 회원)으로 지운다
    @AfterEach
    void cleanUp() {
        gate.clear();
        Long memberId = member.getId();
        jdbc.update("DELETE FROM point_history WHERE member_id = ?", memberId);
        jdbc.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
            + "WHERE pe.member_id = ?", memberId);
        jdbc.update("DELETE FROM point_earning WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        jdbc.update("DELETE r FROM refund r JOIN payment p ON r.payment_id = p.id JOIN orders o ON p.order_id = o.id "
            + "WHERE o.member_id = ?", memberId);
        jdbc.update("DELETE p FROM payment p JOIN orders o ON p.order_id = o.id WHERE o.member_id = ?", memberId);
        jdbc.update("DELETE FROM orders WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM trial_block WHERE phone_hash = ?", phoneHash);
        sessions.deleteSessions(sessions.findSessionKeys("MEMBER", memberId));
        members.deleteById(memberId);
    }

    // ---------- T-5 ----------

    @Test
    @DisplayName("결제·배송이 끝난 회원이 안내받은 잔액 그대로 탈퇴하면 204이고, 포인트는 소멸되며 회원은 탈퇴 상태가 된다")
    void withdrawsAndForfeitsPoints() throws Exception {
        // given — 닫힌 주문(PAID·배송완료) + PAID 결제 + 유효 1,000P
        deliveredOrderWithPaidPayment();
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);

        // when
        MockHttpServletResponse response = withdraw(1_000L);

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isTrue();
        assertThat(statusOf(lotId)).isEqualTo("FORFEITED");
        assertThat(remainingOf(lotId)).isZero();
        assertThat(balance()).isZero();
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isEqualTo(1L);
        assertThat(historyAmount("WITHDRAWAL_FORFEITED")).isEqualTo(1_000L);
        assertThat(historyCount("EXPIRED")).isZero();
    }

    @Test
    @DisplayName("안내받은 잔액이 다르면 409 POINT-002이고 회원·포인트·지연 만료가 모두 롤백된다")
    void rejectsBalanceMismatchAndRollsBackEverything() throws Exception {
        // given — 어제 만료 600P + 유효 400P. 회원 화면은 만료 전 잔액 1,000을 보여줬다
        LocalDateTime now = LocalDateTime.now(clock);
        Long stale = saveCompletedLot(600L, now.minusYears(1).minusDays(1));
        Long valid = saveCompletedLot(400L, now.minusDays(1));
        saveMemberPoint(1_000L);

        // when
        MockHttpServletResponse response = withdraw(1_000L);

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("POINT-002");
        Member unchanged = members.findById(member.getId()).orElseThrow();
        assertThat(unchanged.isWithdrawn()).isFalse();
        assertThat(unchanged.getEmail()).isEqualTo(member.getEmail());
        assertThat(statusOf(stale)).isEqualTo("COMPLETED");
        assertThat(remainingOf(stale)).isEqualTo(600L);
        assertThat(statusOf(valid)).isEqualTo("COMPLETED");
        assertThat(balance()).isEqualTo(1_000L);
        assertThat(historyCount("EXPIRED")).isZero();
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isZero();
    }

    @Test
    @DisplayName("미해결 결제(SYSTEM 환불 REQUESTED)가 있으면 409 PAYMENT-012이고 포인트는 건드리지 않는다 — 결제 검사가 포인트보다 먼저다")
    void rejectsUnsettledPaymentBeforeTouchingPoints() throws Exception {
        // given
        canceledOrderWithRequestedSystemRefund();
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);

        // when
        MockHttpServletResponse response = withdraw(1_000L);

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("PAYMENT-012");
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
        assertThat(statusOf(lotId)).isEqualTo("COMPLETED");
        assertThat(balance()).isEqualTo(1_000L);
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isZero();
    }

    @Test
    @DisplayName("배송완료 전 적립 예정이 남아 있으면 409 POINT-003이다")
    void rejectsPendingEarning() throws Exception {
        // given — 닫힌 주문이지만 배송완료 적립 확정이 아직 안 돼 PENDING이 남아 있다
        Long orderId = deliveredOrderWithPaidPayment();
        earnings.save(PointEarning.pending(member.getId(), orderId, PENDING_EARNING));
        saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);

        // when
        MockHttpServletResponse response = withdraw(1_000L);

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("POINT-003");
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
        assertThat(balance()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("포인트를 가진 적이 없는 회원은 안내 잔액 0으로 탈퇴하고 포인트 행이 새로 생기지 않는다")
    void withdrawsMemberWithoutPointRow() throws Exception {
        // when
        MockHttpServletResponse response = withdraw(0L);

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isTrue();
        assertThat(memberPointRows()).isZero();
    }

    // ---------- T-6 ----------

    @Test
    @DisplayName("탈퇴 TX가 스냅숏을 만든 뒤 배송완료가 커밋되면 옛 스냅숏의 PENDING으로 POINT-003이 나고(막는 방향), 최신 잔액으로 재시도하면 통과한다")
    void deliveryCommittedAfterSnapshotBlocksThenRetrySucceeds() throws Exception {
        // given — 닫힌 주문의 적립 예정 102P + 유효 1,000P. 회원은 1,000을 확인했다
        Long orderId = deliveredOrderWithPaidPayment();
        Long pending = earnings.save(PointEarning.pending(member.getId(), orderId, PENDING_EARNING)).getId();
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);
        CountDownLatch snapshotTaken = new CountDownLatch(1);
        CountDownLatch deliveryCommitted = new CountDownLatch(1);
        // 탈퇴 TX가 진행 중 주문 일반 조회(스냅숏 생성)와 결제 검사를 마친 자리에서 배송완료 커밋을 기다린다
        gate.set(() -> {
            snapshotTaken.countDown();
            await(deliveryCommitted);
        });

        // when
        List<Object> results = runConcurrently(List.of(
            () -> withdraw(1_000L),
            () -> {
                await(snapshotTaken);
                transactionTemplate.executeWithoutResult(status -> pointCommandService.confirmEarning(orderId));
                deliveryCommitted.countDown();
                return "DELIVERED";
            }));

        // then — 배송완료는 반영됐고(1,102P), 탈퇴는 옛 스냅숏의 PENDING을 보고 막혔다. 이중 처리·음수 없음
        MockHttpServletResponse response = (MockHttpServletResponse) results.get(0);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("POINT-003");
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
        assertThat(statusOf(pending)).isEqualTo("COMPLETED");
        assertThat(remainingOf(pending)).isEqualTo(PENDING_EARNING);
        assertThat(statusOf(lotId)).isEqualTo("COMPLETED");
        assertThat(balance()).isEqualTo(1_000L + PENDING_EARNING);
        assertThat(historyCount("EARN_COMPLETED")).isEqualTo(1L);
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isZero();

        // when — 재안내된 최신 잔액으로 다시 탈퇴
        gate.clear();
        MockHttpServletResponse retry = withdraw(1_000L + PENDING_EARNING);

        // then
        assertThat(retry.getStatus()).isEqualTo(204);
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isTrue();
        assertThat(statusOf(pending)).isEqualTo("FORFEITED");
        assertThat(statusOf(lotId)).isEqualTo("FORFEITED");
        assertThat(balance()).isZero();
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isEqualTo(2L);
        assertThat(historyAmount("WITHDRAWAL_FORFEITED")).isEqualTo(1_000L + PENDING_EARNING);
    }

    // ---------- T-7 ----------

    @Test
    @DisplayName("탈퇴와 만료 배치가 같은 회원을 동시에 처리해도 만료 소멸·탈퇴 소멸은 각각 한 번이고 잔액은 음수 없이 0이 된다")
    void withdrawalAndExpirationBatchAtOnce() throws Exception {
        // given — 어제 만료 600P + 유효 400P. 재안내 잔액은 유효기간을 반영해 400이다
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        Long stale = saveCompletedLot(600L, now.minusYears(1).minusDays(1));
        Long valid = saveCompletedLot(400L, now.minusDays(1));
        saveMemberPoint(1_000L);

        // when — 어느 쪽이 MEMBER_POINT를 먼저 잠그든 만료분은 한 번만 소멸하고, 탈퇴는 그 뒤 잔액 400을 소멸시킨다
        List<Object> results = runConcurrently(List.of(
            () -> withdraw(400L),
            () -> pointCommandService.expireMember(member.getId(), today)));

        // then
        assertThat(((MockHttpServletResponse) results.get(0)).getStatus()).isEqualTo(204);
        assertThat(results.get(1)).isIn(600L, 0L);
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isTrue();
        assertThat(statusOf(stale)).isEqualTo("EXPIRED");
        assertThat(remainingOf(stale)).isZero();
        assertThat(statusOf(valid)).isEqualTo("FORFEITED");
        assertThat(remainingOf(valid)).isZero();
        assertThat(balance()).isZero();
        assertThat(historyCount("EXPIRED")).isEqualTo(1L);
        assertThat(historyAmount("EXPIRED")).isEqualTo(600L);
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isEqualTo(1L);
        assertThat(historyAmount("WITHDRAWAL_FORFEITED")).isEqualTo(400L);
    }

    // ---------- API ----------

    private MockHttpServletResponse withdraw(long acknowledgedBalance) throws Exception {
        return mvc.perform(post("/api/members/me/withdraw")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + PASSWORD + "\",\"acknowledgedPointBalance\":"
                    + acknowledgedBalance + "}"))
            .andReturn().getResponse();
    }

    // ---------- 픽스처 ----------

    // 결제·배송까지 끝난 닫힌 주문 + PAID 결제. 회원 쪽 진행 중 주문 검사(MEMBER-006)를 통과하고 결제 검사도 통과하는 자리다
    private Long deliveredOrderWithPaidPayment() {
        Order order = orders.save(Order.builder()
            .memberId(member.getId())
            .martId(MART_ID)
            .status(OrderStatus.PAID)
            .martStatus(MartStatus.DELIVERED)
            .build());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Payment payment = Payment.builder()
            .orderId(order.getId())
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_withdrawal_" + order.getId())
            .amount(AMOUNT)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(now.minusMinutes(5))
            .build();
        // 결제 완료 상태는 빌더로 못 만들고 실제 전이를 거쳐야 한다
        payment.markPaid("tx_withdrawal_" + order.getId(), now.minusMinutes(8));
        payments.save(payment);
        return order.getId();
    }

    // 결제 대기 만료로 닫힌 주문(CANCELED)에 늦은 승인이 확인돼 SYSTEM 환불을 요청한 상태. 아직 PG 반환이 끝나지 않은 미해결이다
    private void canceledOrderWithRequestedSystemRefund() {
        Order order = orders.save(Order.builder()
            .memberId(member.getId())
            .martId(MART_ID)
            .status(OrderStatus.CANCELED)
            .martStatus(MartStatus.NONE)
            .build());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Payment payment = Payment.builder()
            .orderId(order.getId())
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_withdrawal_" + order.getId())
            .amount(AMOUNT)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(now.minusMinutes(10))
            .build();
        payment.fail(PaymentFailureReason.EXPIRED, null);
        payments.save(payment);
        refunds.save(Refund.requestedBySystem(payment, AMOUNT, "늦은 승인 자동 반환", UUID.randomUUID().toString(), now));
    }

    private Long saveCompletedLot(long amount, LocalDateTime completedAt) {
        PointEarning lot = PointEarning.pending(member.getId(), lotSequence++, amount);
        lot.complete(completedAt);
        return earnings.save(lot).getId();
    }

    private void saveMemberPoint(long balance) {
        memberPoints.save(MemberPoint.builder().memberId(member.getId()).balance(balance).build());
    }

    // ---------- 동시 실행 ----------

    // 모든 스레드가 준비된 뒤 한 번에 출발시킨다. 결과는 넘긴 작업 순서대로 돌려준다.
    // 락 대기 초과(5초)보다 넉넉히 기다리고, 예상 밖 예외는 Future.get이 그대로 던진다
    private List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    // 상대 트랜잭션이 15초 안에 오지 않으면 멈추지 않고 실패로 끝낸다
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("15초 안에 상대 트랜잭션이 도착하지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("상대 트랜잭션을 기다리다 중단됐습니다.", e);
        }
    }

    // ---------- 조회 ----------

    private String statusOf(Long lotId) {
        return jdbc.queryForObject("SELECT status FROM point_earning WHERE id = ?", String.class, lotId);
    }

    private long remainingOf(Long lotId) {
        return jdbc.queryForObject("SELECT remaining_amount FROM point_earning WHERE id = ?", Long.class, lotId);
    }

    private long balance() {
        return jdbc.queryForObject("SELECT balance FROM member_point WHERE member_id = ?", Long.class, member.getId());
    }

    private long memberPointRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member_point WHERE member_id = ?", Long.class, member.getId());
    }

    private long historyCount(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Long.class, member.getId(), type);
    }

    private long historyAmount(String type) {
        return jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM point_history WHERE member_id = ? AND type = ?",
            Long.class, member.getId(), type);
    }
}
