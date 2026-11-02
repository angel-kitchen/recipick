package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.recipick.domain.payment.service.MembershipPaymentRef;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// 갱신 조회와 재시도 가드를 실제 MySQL에서 본다. 가드는 C-17과 같은 네 조건이되 "현재 회차의 RETRY_WAITING·카드 대기 한 행"만 뺀다.
// 다른 테스트가 남긴 행과 섞이지 않도록 이 클래스의 이용권 ID 범위로만 단언한다
class MembershipRenewalQueryIntegrationTest extends IntegrationTestSupport {

    private static final long MEMBER_ID = 9_820_001L;
    private static final AtomicLong MEMBERSHIP_SEQUENCE = new AtomicLong(9_820_100L);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 10, 0);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 11, 9, 10, 0);
    private static final Set<MembershipPaymentStatus> UNRESOLVED = Set.of(MembershipPaymentStatus.PENDING,
        MembershipPaymentStatus.RETRY_WAITING, MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED,
        MembershipPaymentStatus.UNKNOWN, MembershipPaymentStatus.MANUAL_REVIEW);
    private static final Set<MembershipPaymentStatus> WAITING = Set.of(MembershipPaymentStatus.PENDING,
        MembershipPaymentStatus.RETRY_WAITING, MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED);

    @Autowired MembershipPaymentQueryService queryService;
    @Autowired MembershipPaymentRepository payments;
    @Autowired MembershipPaymentAttemptRepository attempts;
    @Autowired MembershipRefundRepository refunds;
    @Autowired BillingKeyRepository billingKeys;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE r FROM membership_refund r JOIN membership_payment_attempt a "
            + "ON r.membership_payment_attempt_id = a.id JOIN membership_payment p ON a.membership_payment_id = p.id "
            + "WHERE p.membership_id BETWEEN 9820100 AND 9829999");
        jdbc.update("DELETE a FROM membership_payment_attempt a JOIN membership_payment p "
            + "ON a.membership_payment_id = p.id WHERE p.membership_id BETWEEN 9820100 AND 9829999");
        jdbc.update("DELETE FROM membership_payment WHERE membership_id BETWEEN 9820100 AND 9829999");
        jdbc.update("DELETE FROM billing_key WHERE member_id = ?", MEMBER_ID);
    }

    // ---------- 재시도 가드 ----------

    @Test
    @DisplayName("자기 RETRY_WAITING 회차만 있으면 재시도 가드는 통과하고 C-17은 그대로 막는다")
    void ownRetryWaitingDoesNotBlock() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment own = savePayment(membershipId, MembershipPaymentStatus.RETRY_WAITING, PERIOD_START, null);
        saveAttempt(own, MembershipPaymentAttemptStatus.FAILED, null);

        // when
        boolean retryBlocked = existsUnresolvedForRetry(List.of(membershipId), own.getId());
        boolean c17Blocked = existsUnresolved(List.of(membershipId));

        // then
        assertThat(retryBlocked).isFalse();
        assertThat(c17Blocked).isTrue();
    }

    @Test
    @DisplayName("자기 카드 대기 회차도 빠지지만, 자기 회차가 PENDING이면 빠지지 않는다(정규화 전에는 막힌다)")
    void ownPaymentMethodRequiredDoesNotBlockButPendingDoes() {
        // given
        long waitingMembership = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment waiting = savePayment(waitingMembership, MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED,
            PERIOD_START, null);
        long pendingMembership = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment pending = savePayment(pendingMembership, MembershipPaymentStatus.PENDING, PERIOD_START,
            null);

        // when / then
        assertThat(existsUnresolvedForRetry(List.of(waitingMembership), waiting.getId())).isFalse();
        assertThat(existsUnresolvedForRetry(List.of(pendingMembership), pending.getId())).isTrue();
    }

    @Test
    @DisplayName("같은 회원 옛 회차의 반영 안 한 PAID에 SYSTEM 환불이 FAILED면 새 시도를 막는다")
    void oldRefundFailedBlocks() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment old = savePayment(membershipId, MembershipPaymentStatus.FAILED,
            PERIOD_START.minusMonths(1), null);
        MembershipPaymentAttempt paid = saveAttempt(old, MembershipPaymentAttemptStatus.PAID, NOW.minusDays(20));
        saveRefund(old, paid, MembershipRefundStatus.FAILED);
        MembershipPayment own = savePayment(membershipId, MembershipPaymentStatus.RETRY_WAITING, PERIOD_START, null);

        // when / then
        assertThat(existsUnresolvedForRetry(List.of(membershipId), own.getId())).isTrue();
    }

    @Test
    @DisplayName("자기 회차의 살아 있는 시도, 다른 미해결 회차, 환불 REQUESTED는 각각 막는다")
    void otherBlockingConditions() {
        // given
        long liveMembership = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment live = savePayment(liveMembership, MembershipPaymentStatus.RETRY_WAITING, PERIOD_START,
            null);
        saveAttempt(live, MembershipPaymentAttemptStatus.UNKNOWN, null);

        long otherMembership = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment own = savePayment(otherMembership, MembershipPaymentStatus.RETRY_WAITING, PERIOD_START,
            null);
        savePayment(otherMembership, MembershipPaymentStatus.UNKNOWN, PERIOD_START.minusMonths(1), null);

        long refundMembership = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment refundOwn = savePayment(refundMembership, MembershipPaymentStatus.RETRY_WAITING,
            PERIOD_START, null);
        MembershipPayment refunded = savePayment(refundMembership, MembershipPaymentStatus.PAID,
            PERIOD_START.minusMonths(1), null);
        MembershipPaymentAttempt paid = saveAttempt(refunded, MembershipPaymentAttemptStatus.PAID, null);
        saveRefund(refunded, paid, MembershipRefundStatus.REQUESTED);

        // when / then
        assertThat(existsUnresolvedForRetry(List.of(liveMembership), live.getId())).isTrue();
        assertThat(existsUnresolvedForRetry(List.of(otherMembership), own.getId())).isTrue();
        assertThat(existsUnresolvedForRetry(List.of(refundMembership), refundOwn.getId())).isTrue();
    }

    // ---------- 갱신 Sweep 조회 ----------

    @Test
    @DisplayName("재개 조회: 재시도 시각이 된 RETRY_WAITING(정각 포함)·카드 대기·PENDING만, 마감 지난 회차·첫 결제·null 예약은 뺀다")
    void resumableRenewalsBoundary() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment due = savePayment(membershipId, MembershipPaymentStatus.RETRY_WAITING, PERIOD_START,
            NOW);
        MembershipPayment notYet = savePayment(membershipId, MembershipPaymentStatus.RETRY_WAITING,
            PERIOD_START.plusDays(1), NOW.plusNanos(1_000));
        MembershipPayment waitDeadline = savePayment(membershipId, MembershipPaymentStatus.RETRY_WAITING,
            PERIOD_START.plusDays(2), null);
        MembershipPayment card = savePayment(membershipId, MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED,
            PERIOD_START.plusDays(3), null);
        MembershipPayment pending = savePayment(membershipId, MembershipPaymentStatus.PENDING,
            PERIOD_START.plusDays(4), null);
        MembershipPayment unknown = savePayment(membershipId, MembershipPaymentStatus.UNKNOWN,
            PERIOD_START.plusDays(5), null);
        MembershipPayment overdue = savePaymentWithDeadline(membershipId,
            MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED, PERIOD_START.plusDays(6), NOW);
        MembershipPayment first = savePaymentWithDeadline(membershipId, MembershipPaymentStatus.PENDING,
            PERIOD_START.plusDays(7), null);

        // when
        List<Long> found = payments.findResumableRenewals(NOW, 0L, payments.findMaxId(), PageRequest.of(0, 1_000))
            .stream().map(MembershipPaymentRef::paymentId).toList();

        // then
        assertThat(found).contains(due.getId(), card.getId(), pending.getId());
        assertThat(found).doesNotContain(notYet.getId(), waitDeadline.getId(), unknown.getId(), overdue.getId(),
            first.getId());
    }

    @Test
    @DisplayName("상한 ID 뒤에 생긴 회차는 같은 실행에서 다시 읽지 않는다")
    void upperIdFixesScanRange() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment before = savePayment(membershipId, MembershipPaymentStatus.PENDING, PERIOD_START, null);
        long upperId = payments.findMaxId();
        MembershipPayment after = savePayment(membershipId, MembershipPaymentStatus.PENDING, PERIOD_START.plusDays(1),
            null);

        // when
        List<Long> found = payments.findResumableRenewals(NOW, 0L, upperId, PageRequest.of(0, 1_000)).stream()
            .map(MembershipPaymentRef::paymentId).toList();

        // then
        assertThat(found).contains(before.getId()).doesNotContain(after.getId());
    }

    @Test
    @DisplayName("정리 조회: 마감 지난 미해결 회차 전부(정각 포함) + 마감 전 대기 회차. 마감 전 UNKNOWN·확정·첫 결제는 뺀다")
    void maintenanceTargets() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment overdueUnknown = savePaymentWithDeadline(membershipId, MembershipPaymentStatus.UNKNOWN,
            PERIOD_START, NOW);
        MembershipPayment waiting = savePayment(membershipId, MembershipPaymentStatus.RETRY_WAITING,
            PERIOD_START.plusDays(1), NOW.plusHours(1));
        MembershipPayment unknownBeforeDeadline = savePayment(membershipId, MembershipPaymentStatus.UNKNOWN,
            PERIOD_START.plusDays(2), null);
        MembershipPayment resolved = savePaymentWithDeadline(membershipId, MembershipPaymentStatus.FAILED,
            PERIOD_START.plusDays(3), NOW.minusDays(1));
        MembershipPayment first = savePaymentWithDeadline(membershipId, MembershipPaymentStatus.PENDING,
            PERIOD_START.plusDays(4), null);

        // when
        List<Long> found = payments.findRenewalsNeedingMaintenance(NOW, 0L, payments.findMaxId(), UNRESOLVED,
            WAITING, PageRequest.of(0, 1_000)).stream().map(MembershipPaymentRef::paymentId).toList();

        // then
        assertThat(found).contains(overdueUnknown.getId(), waiting.getId());
        assertThat(found).doesNotContain(unknownBeforeDeadline.getId(), resolved.getId(), first.getId());
    }

    @Test
    @DisplayName("회원 카드 대기 회차 조회는 지정 상태의 갱신 회차만 ID 순으로 준다")
    void refsByMembershipAndStatus() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment card = savePayment(membershipId, MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED,
            PERIOD_START, null);
        savePayment(membershipId, MembershipPaymentStatus.RETRY_WAITING, PERIOD_START.plusDays(1), NOW);

        // when
        List<MembershipPaymentRef> found = payments.findRefsByMembershipIdInAndStatus(List.of(membershipId),
            MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED);

        // then
        assertThat(found).extracting(MembershipPaymentRef::paymentId).containsExactly(card.getId());
        assertThat(found.get(0).membershipId()).isEqualTo(membershipId);
    }

    @Test
    @DisplayName("회차 기간 시작일로 회차 ID를 찾고, 회차의 시도 ID를 PK 오름차순으로 준다")
    void idLookups() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        MembershipPayment payment = savePayment(membershipId, MembershipPaymentStatus.PENDING, PERIOD_START, null);
        MembershipPaymentAttempt first = saveAttempt(payment, MembershipPaymentAttemptStatus.CANCELED, null);
        MembershipPaymentAttempt second = saveAttempt(payment, MembershipPaymentAttemptStatus.READY, null);

        // when / then
        assertThat(payments.findIdByMembershipIdAndPeriodStartDate(membershipId, PERIOD_START))
            .contains(payment.getId());
        assertThat(payments.findIdByMembershipIdAndPeriodStartDate(membershipId, PERIOD_START.plusDays(1))).isEmpty();
        assertThat(attempts.findIdsByMembershipPaymentIdOrderByIdAsc(payment.getId()))
            .containsExactly(first.getId(), second.getId());
    }

    private boolean existsUnresolvedForRetry(List<Long> membershipIds, Long paymentId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
            status -> queryService.existsUnresolvedForRetry(membershipIds, paymentId)));
    }

    private boolean existsUnresolved(List<Long> membershipIds) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> queryService.existsUnresolved(membershipIds)));
    }

    // 마감 전(DEADLINE) 갱신 회차
    private MembershipPayment savePayment(long membershipId, MembershipPaymentStatus status, LocalDate periodStart,
                                          LocalDateTime nextRetryAt) {
        return payments.saveAndFlush(MembershipPayment.builder()
            .membershipId(membershipId).amount(3_900L).status(status)
            .billingDate(periodStart).periodStartDate(periodStart).periodEndDate(periodStart.plusMonths(1).minusDays(1))
            .graceDeadlineAt(DEADLINE).nextRetryAt(nextRetryAt)
            .build());
    }

    // graceDeadlineAt을 직접 정한다(null이면 첫 결제 회차)
    private MembershipPayment savePaymentWithDeadline(long membershipId, MembershipPaymentStatus status,
                                                      LocalDate periodStart, LocalDateTime graceDeadlineAt) {
        return payments.saveAndFlush(MembershipPayment.builder()
            .membershipId(membershipId).amount(3_900L).status(status)
            .billingDate(periodStart).periodStartDate(periodStart).periodEndDate(periodStart.plusMonths(1).minusDays(1))
            .graceDeadlineAt(graceDeadlineAt)
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
        return attempts.saveAndFlush(MembershipPaymentAttempt.builder()
            .membershipPayment(payment).billingKey(key).attemptNo(existing.intValue() + 1)
            .portonePaymentId("mpay_" + UUID.randomUUID().toString().replace("-", "")).status(status)
            .requestedAt(NOW).leaseExpiresAt(NOW.plusMinutes(5)).refundRequiredAt(refundRequiredAt).build());
    }

    private MembershipRefund saveRefund(MembershipPayment payment, MembershipPaymentAttempt attempt,
                                        MembershipRefundStatus status) {
        return refunds.saveAndFlush(MembershipRefund.builder()
            .membershipPayment(payment).membershipPaymentAttempt(attempt).amount(3_900L).usedDays(0)
            .usedDayFee(0L).penaltyAmount(0L).status(status).reason("SYSTEM_TEST").requestedBy(RefundRequester.SYSTEM)
            .idempotencyKey("mrf-" + UUID.randomUUID()).requestedAt(NOW).build());
    }
}
