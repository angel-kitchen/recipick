package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

// MP-01~10·15. C-17 미정산 판정의 네 조건을 실제 MySQL 위에서 상태별로 본다. 바깥 트랜잭션 안에서 불리는 계약(MANDATORY)이라
// 테스트가 TransactionTemplate로 트랜잭션을 열어 부른다. 경합(P1·P2)은 MembershipPaymentQueryConcurrencyTest가 맡는다.
// 픽스처는 4단계 전이 메서드 없이 빌더로 상태를 만든다 — 2단계에 빌더를 남기는 이유다
class MembershipPaymentQueryServiceIntegrationTest extends IntegrationTestSupport {

    private static final long MEMBER_ID = 9_810_001L;
    private static final AtomicLong MEMBERSHIP_SEQUENCE = new AtomicLong(9_810_100L);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 1);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 14, 0);
    private static final Logger SQL_LOGGER = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");

    @Autowired
    MembershipPaymentQueryService queryService;

    @Autowired
    MembershipPaymentRepository payments;

    @Autowired
    MembershipPaymentAttemptRepository attempts;

    @Autowired
    MembershipRefundRepository refunds;

    @Autowired
    BillingKeyRepository billingKeys;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        // 환불은 시도 FK로 찾는다 — 회차 직접 참조가 NULL인 환불도 지워야 시도를 지울 수 있다
        jdbc.update("DELETE r FROM membership_refund r JOIN membership_payment_attempt a "
            + "ON r.membership_payment_attempt_id = a.id JOIN membership_payment p ON a.membership_payment_id = p.id "
            + "WHERE p.membership_id BETWEEN 9810100 AND 9819999");
        jdbc.update("DELETE a FROM membership_payment_attempt a JOIN membership_payment p "
            + "ON a.membership_payment_id = p.id WHERE p.membership_id BETWEEN 9810100 AND 9819999");
        jdbc.update("DELETE FROM membership_payment WHERE membership_id BETWEEN 9810100 AND 9819999");
        jdbc.update("DELETE FROM billing_key WHERE member_id = ?", MEMBER_ID);
    }

    // ---------- MP-01 ----------

    @Test
    @DisplayName("이용권 ID가 없으면 조회 없이 false다")
    void emptyIdsAreFalseWithoutQuery() {
        // given
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previous = SQL_LOGGER.getLevel();
        SQL_LOGGER.setLevel(Level.DEBUG);
        SQL_LOGGER.addAppender(appender);
        String thread = Thread.currentThread().getName();
        try {
            // when
            boolean unresolved = existsUnresolved(List.of());

            // then
            assertThat(unresolved).isFalse();
            assertThat(appender.list).noneMatch(event -> thread.equals(event.getThreadName()));
        } finally {
            SQL_LOGGER.detachAppender(appender);
            SQL_LOGGER.setLevel(previous);
        }
    }

    // ---------- MP-02 ----------

    @ParameterizedTest
    @EnumSource(value = MembershipPaymentStatus.class,
        names = {"PENDING", "RETRY_WAITING", "PAYMENT_METHOD_REQUIRED", "UNKNOWN", "MANUAL_REVIEW"})
    @DisplayName("미해결 상태의 회차가 있으면 true다")
    void unresolvedPaymentStatuses(MembershipPaymentStatus status) {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        savePayment(membershipId, status);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MembershipPaymentStatus.class, names = {"PAID", "FAILED"})
    @DisplayName("확정된 회차만 있으면 false다")
    void finishedPaymentStatuses(MembershipPaymentStatus status) {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        savePayment(membershipId, status);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isFalse();
    }

    // ---------- MP-03 ----------

    @ParameterizedTest
    @EnumSource(value = MembershipPaymentAttemptStatus.class,
        names = {"READY", "DISPATCHING", "UNKNOWN", "MANUAL_REVIEW"})
    @DisplayName("회차가 확정됐어도 미해결 시도가 남아 있으면 true다")
    void unresolvedAttemptStatuses(MembershipPaymentAttemptStatus status) {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.FAILED);
        saveAttempt(payment, status, null);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MembershipPaymentAttemptStatus.class, names = {"PAID", "FAILED", "CANCELED"})
    @DisplayName("확정된 시도(환불 의무 없음)만 있으면 false다")
    void finishedAttemptStatuses(MembershipPaymentAttemptStatus status) {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.PAID);
        saveAttempt(payment, status, null);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isFalse();
    }

    // ---------- MP-04 ----------

    @Test
    @DisplayName("결과를 기다리는 환불(REQUESTED)이 있으면 true다")
    void requestedRefund() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.PAID);
        MembershipPaymentAttempt attempt = saveAttempt(payment, MembershipPaymentAttemptStatus.PAID, null);
        saveRefund(payment, attempt, RefundRequester.MEMBER, MembershipRefundStatus.REQUESTED);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isTrue();
    }

    @Test
    @DisplayName("환불이 회차를 직접 가리키지 않아도(시도로만 연결) REQUESTED면 true다 — 판정은 필수 FK인 시도 경로로 본다")
    void requestedRefundLinkedOnlyThroughAttempt() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.PAID);
        MembershipPaymentAttempt attempt = saveAttempt(payment, MembershipPaymentAttemptStatus.PAID, null);
        saveRefund(null, attempt, RefundRequester.MEMBER, MembershipRefundStatus.REQUESTED);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MembershipRefundStatus.class, names = {"COMPLETED", "FAILED"})
    @DisplayName("끝난 환불만 있고 시도에 환불 의무가 없으면 false다")
    void finishedRefundWithoutObligation(MembershipRefundStatus status) {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.PAID);
        MembershipPaymentAttempt attempt = saveAttempt(payment, MembershipPaymentAttemptStatus.PAID, null);
        saveRefund(payment, attempt, RefundRequester.MEMBER, status);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isFalse();
    }

    // ---------- MP-05~08: 조건 ④ ----------

    @Test
    @DisplayName("확정 실패한 회차에 돌려줘야 하는 PAID 시도가 있고 SYSTEM 환불이 FAILED로 끝났으면 true다 — 숨은 미정산")
    void paidAttemptRequiringRefundWithFailedSystemRefund() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.FAILED);
        MembershipPaymentAttempt attempt = saveAttempt(payment, MembershipPaymentAttemptStatus.PAID, NOW);
        saveRefund(payment, attempt, RefundRequester.SYSTEM, MembershipRefundStatus.FAILED);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isTrue();
    }

    @Test
    @DisplayName("같은 구성에서 SYSTEM 환불이 COMPLETED면 false다 — 돈이 돌아갔다")
    void paidAttemptRequiringRefundWithCompletedSystemRefund() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.FAILED);
        MembershipPaymentAttempt attempt = saveAttempt(payment, MembershipPaymentAttemptStatus.PAID, NOW);
        saveRefund(payment, attempt, RefundRequester.SYSTEM, MembershipRefundStatus.FAILED);
        saveRefund(payment, attempt, RefundRequester.SYSTEM, MembershipRefundStatus.COMPLETED);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isFalse();
    }

    @Test
    @DisplayName("돌려줄 의무가 없는 PAID 시도는 환불이 없어도 false다")
    void paidAttemptWithoutObligation() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.PAID);
        saveAttempt(payment, MembershipPaymentAttemptStatus.PAID, null);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isFalse();
    }

    @Test
    @DisplayName("돌려줘야 하는 시도에 MEMBER 환불 COMPLETED만 있고 SYSTEM 환불이 없으면 여전히 true다")
    void memberRefundDoesNotSettleObligation() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.FAILED);
        MembershipPaymentAttempt attempt = saveAttempt(payment, MembershipPaymentAttemptStatus.PAID, NOW);
        saveRefund(payment, attempt, RefundRequester.MEMBER, MembershipRefundStatus.COMPLETED);

        // then
        assertThat(existsUnresolved(List.of(membershipId))).isTrue();
    }

    // ---------- MP-09·10 ----------

    @Test
    @DisplayName("종료·만료 이용권 ID가 섞여 있어도 하나라도 미정산이면 true다 — 호출자는 모든 이용권 ID를 넘긴다")
    void anyMembershipUnresolved() {
        // given
        long finished = MEMBERSHIP_SEQUENCE.incrementAndGet();
        long expired = MEMBERSHIP_SEQUENCE.incrementAndGet();
        long current = MEMBERSHIP_SEQUENCE.incrementAndGet();
        savePayment(finished, MembershipPaymentStatus.PAID);
        savePayment(expired, MembershipPaymentStatus.UNKNOWN);

        // then
        assertThat(existsUnresolved(List.of(current, finished, expired))).isTrue();
    }

    @Test
    @DisplayName("다른 이용권의 미정산은 이 목록의 판정을 바꾸지 않는다")
    void otherMembershipDoesNotCount() {
        // given
        long mine = MEMBERSHIP_SEQUENCE.incrementAndGet();
        long other = MEMBERSHIP_SEQUENCE.incrementAndGet();
        savePayment(mine, MembershipPaymentStatus.PAID);
        savePayment(other, MembershipPaymentStatus.PENDING);

        // then
        assertThat(existsUnresolved(List.of(mine))).isFalse();
    }

    // ---------- MP-15 ----------

    @Test
    @DisplayName("바깥 트랜잭션 없이 부르면 거절한다 — 회원 잠금 트랜잭션 안에서만 뜻이 있다")
    void requiresOuterTransaction() {
        // when
        Throwable thrown = catchThrowable(() -> queryService.existsUnresolved(List.of(1L)));

        // then
        assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
    }

    // ---------- 실행·픽스처 ----------

    private boolean existsUnresolved(List<Long> membershipIds) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> queryService.existsUnresolved(membershipIds)));
    }

    private MembershipPayment savePayment(long membershipId, MembershipPaymentStatus status) {
        return payments.saveAndFlush(MembershipPayment.builder()
            .membershipId(membershipId).amount(3_900L).status(status)
            .billingDate(PERIOD_START).periodStartDate(PERIOD_START)
            .periodEndDate(PERIOD_START.plusMonths(1).minusDays(1))
            .build());
    }

    private MembershipPaymentAttempt saveAttempt(MembershipPayment payment, MembershipPaymentAttemptStatus status,
                                                 LocalDateTime refundRequiredAt) {
        BillingKey key = billingKeys.saveAndFlush(BillingKey.builder()
            .memberId(MEMBER_ID).portoneBillingKey("billing-key-test-" + UUID.randomUUID())
            .status(BillingKeyStatus.ACTIVE).issuedAt(NOW).build());
        Long existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM membership_payment_attempt WHERE membership_payment_id = ?", Long.class,
            payment.getId());
        int attemptNo = existing.intValue() + 1;
        return attempts.saveAndFlush(MembershipPaymentAttempt.builder()
            .membershipPayment(payment).billingKey(key).attemptNo(attemptNo)
            .portonePaymentId("mpay_" + UUID.randomUUID()).status(status)
            .requestedAt(NOW).leaseExpiresAt(NOW.plusMinutes(5)).refundRequiredAt(refundRequiredAt).build());
    }

    private MembershipRefund saveRefund(MembershipPayment payment, MembershipPaymentAttempt attempt,
                                        RefundRequester requester, MembershipRefundStatus status) {
        return refunds.saveAndFlush(MembershipRefund.builder()
            .membershipPayment(payment).membershipPaymentAttempt(attempt).amount(3_900L).usedDays(0)
            .usedDayFee(0L).penaltyAmount(780L).status(status).reason("테스트").requestedBy(requester)
            .idempotencyKey("mrf-" + UUID.randomUUID()).requestedAt(NOW).build());
    }
}
