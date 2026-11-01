package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// MP-11·MP-12·BK-10. 회차·시도·환불 모델의 UNIQUE·FK·인덱스가 실제 MySQL에 설계 이름 그대로 존재하는지 본다.
// 엔티티의 제약 이름과 운영 DDL(docs/deploy-implementation.md)의 이름이 같아야 ddl-auto: update가 중복 인덱스를 만들지 않는다
class MembershipPaymentModelRepositoryTest extends IntegrationTestSupport {

    private static final long MEMBER_ID = 9_800_001L;
    private static final AtomicLong MEMBERSHIP_SEQUENCE = new AtomicLong(9_800_100L);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 1);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 14, 0);
    private static final List<MembershipPaymentAttemptStatus> UNRESOLVED_ATTEMPT = List.of(
        MembershipPaymentAttemptStatus.READY, MembershipPaymentAttemptStatus.DISPATCHING,
        MembershipPaymentAttemptStatus.UNKNOWN, MembershipPaymentAttemptStatus.MANUAL_REVIEW);

    @Autowired
    MembershipPaymentRepository payments;

    @Autowired
    MembershipPaymentAttemptRepository attempts;

    @Autowired
    BillingKeyRepository billingKeys;

    @Autowired
    JdbcTemplate jdbc;

    // 환불 → 시도 → 회차 → 빌링키 순(외래키 역순)
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE r FROM membership_refund r JOIN membership_payment p ON r.membership_payment_id = p.id "
            + "WHERE p.membership_id BETWEEN 9800100 AND 9809999");
        jdbc.update("DELETE a FROM membership_payment_attempt a JOIN membership_payment p "
            + "ON a.membership_payment_id = p.id WHERE p.membership_id BETWEEN 9800100 AND 9809999");
        jdbc.update("DELETE FROM membership_payment WHERE membership_id BETWEEN 9800100 AND 9809999");
        jdbc.update("DELETE FROM billing_key WHERE member_id = ?", MEMBER_ID);
    }

    // ---------- MP-11 ----------

    @Test
    @DisplayName("같은 이용권의 같은 이용기간 회차는 두 번 만들 수 없다")
    void rejectsDuplicatePeriod() {
        // given
        long membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
        savePayment(membershipId, PERIOD_START);

        // when
        Throwable thrown = catchThrowable(() -> savePayment(membershipId, PERIOD_START));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 회차의 같은 시도 번호는 두 번 만들 수 없다")
    void rejectsDuplicateAttemptNo() {
        // given
        BillingKey key = saveKey(BillingKeyStatus.ACTIVE);
        MembershipPayment payment = savePayment(MEMBERSHIP_SEQUENCE.incrementAndGet(), PERIOD_START);
        saveAttempt(payment, key, 1, MembershipPaymentAttemptStatus.READY);

        // when
        Throwable thrown = catchThrowable(() -> saveAttempt(payment, key, 1, MembershipPaymentAttemptStatus.READY));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 PortOne 결제 ID는 두 시도에 쓸 수 없다")
    void rejectsDuplicatePortonePaymentId() {
        // given
        BillingKey key = saveKey(BillingKeyStatus.ACTIVE);
        MembershipPayment payment = savePayment(MEMBERSHIP_SEQUENCE.incrementAndGet(), PERIOD_START);
        String portonePaymentId = "mpay_" + UUID.randomUUID();
        attempts.saveAndFlush(attempt(payment, key, 1, MembershipPaymentAttemptStatus.READY, portonePaymentId));

        // when
        Throwable thrown = catchThrowable(() -> attempts.saveAndFlush(
            attempt(payment, key, 2, MembershipPaymentAttemptStatus.READY, portonePaymentId)));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- MP-12 ----------

    @Test
    @DisplayName("설계 이름의 FK 4개가 실제 DB에 있다")
    void foreignKeysExist() {
        // when
        List<Map<String, Object>> constraints = jdbc.queryForList(
            "SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME IN "
                + "('billing_key_issue_request', 'membership_payment_attempt', 'membership_refund')");

        // then
        assertThat(constraints).extracting(row -> row.get("CONSTRAINT_NAME")).contains(
            "fk_billing_key_issue_request_billing_key",
            "fk_membership_payment_attempt_payment",
            "fk_membership_payment_attempt_billing_key",
            "fk_membership_refund_attempt");
        assertThat(constraints).extracting(row -> row.get("TABLE_NAME") + "->" + row.get("REFERENCED_TABLE_NAME"))
            .contains("membership_payment_attempt->membership_payment", "membership_payment_attempt->billing_key",
                "membership_refund->membership_payment_attempt", "billing_key_issue_request->billing_key");
    }

    @Test
    @DisplayName("설계 이름의 UNIQUE·인덱스가 있고, FK 컬럼이 복합 인덱스의 첫 컬럼이라 FK용 단일 인덱스가 따로 생기지 않는다")
    void indexesExistWithoutDuplicates() {
        // when
        List<Map<String, Object>> indexes = jdbc.queryForList(
            "SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols "
                + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN "
                + "('billing_key', 'billing_key_issue_request', 'membership_payment', 'membership_payment_attempt', "
                + "'membership_refund') GROUP BY TABLE_NAME, INDEX_NAME");
        List<String> names = indexes.stream().map(row -> (String) row.get("INDEX_NAME")).toList();
        List<String> attemptColumns = indexes.stream()
            .filter(row -> "membership_payment_attempt".equals(row.get("TABLE_NAME")))
            .map(row -> (String) row.get("cols")).toList();
        List<String> refundColumns = indexes.stream()
            .filter(row -> "membership_refund".equals(row.get("TABLE_NAME")))
            .map(row -> (String) row.get("cols")).toList();

        // then
        assertThat(names).contains(
            "uk_billing_key_portone_billing_key",
            "idx_billing_key_member_id_status",
            "uk_billing_key_issue_request_issue_id",
            "uk_membership_payment_membership_id_period_start_date",
            "uk_membership_payment_idempotency_key",
            "idx_membership_payment_membership_id_status",
            "uk_membership_payment_attempt_portone_payment_id",
            "uk_membership_payment_attempt_payment_id_attempt_no",
            "idx_membership_payment_attempt_payment_id_status",
            "idx_membership_payment_attempt_billing_key_id_status",
            "idx_membership_refund_payment_id_status",
            "idx_membership_refund_attempt_requester_status");
        assertThat(attemptColumns).doesNotContain("membership_payment_id", "billing_key_id");
        assertThat(refundColumns).doesNotContain("membership_payment_attempt_id");
    }

    // ---------- BK-10 ----------

    @Test
    @DisplayName("시도가 참조하는 키 행은 지울 수 없고, 그 키를 쓰는 미해결 시도가 있는지 조회할 수 있다 — 6단계 삭제 보류의 근거")
    void attemptPinsBillingKey() {
        // given — 교체된 키를 아직 쓰는 시도
        BillingKey replaced = saveKey(BillingKeyStatus.REPLACED);
        MembershipPayment payment = savePayment(MEMBERSHIP_SEQUENCE.incrementAndGet(), PERIOD_START);
        saveAttempt(payment, replaced, 1, MembershipPaymentAttemptStatus.DISPATCHING);

        // when
        Throwable thrown = catchThrowable(() -> billingKeys.deleteById(replaced.getId()));

        // then — 커밋 시점의 FK 위반이라 번역 경로에 따라 DataIntegrityViolationException 또는 TransactionSystemException이다.
        // 중요한 것은 행이 남아 있다는 사실이다
        assertThat(thrown).isNotNull();
        assertThat(billingKeys.existsById(replaced.getId())).isTrue();
        assertThat(attempts.existsByBillingKeyIdAndStatusIn(replaced.getId(), UNRESOLVED_ATTEMPT)).isTrue();
    }

    @Test
    @DisplayName("확정된 시도만 있는 키는 미해결 시도 조회가 false다")
    void finishedAttemptsDoNotPin() {
        // given
        BillingKey replaced = saveKey(BillingKeyStatus.REPLACED);
        MembershipPayment payment = savePayment(MEMBERSHIP_SEQUENCE.incrementAndGet(), PERIOD_START);
        saveAttempt(payment, replaced, 1, MembershipPaymentAttemptStatus.PAID);
        saveAttempt(payment, replaced, 2, MembershipPaymentAttemptStatus.FAILED);

        // then
        assertThat(attempts.existsByBillingKeyIdAndStatusIn(replaced.getId(), UNRESOLVED_ATTEMPT)).isFalse();
    }

    // ---------- 픽스처 ----------

    private BillingKey saveKey(BillingKeyStatus status) {
        return billingKeys.saveAndFlush(BillingKey.builder()
            .memberId(MEMBER_ID).portoneBillingKey("billing-key-test-" + UUID.randomUUID())
            .status(status).issuedAt(NOW).build());
    }

    private MembershipPayment savePayment(long membershipId, LocalDate periodStart) {
        return payments.saveAndFlush(MembershipPayment.builder()
            .membershipId(membershipId).amount(3_900L).status(MembershipPaymentStatus.PENDING)
            .billingDate(periodStart).periodStartDate(periodStart)
            .periodEndDate(periodStart.plusMonths(1).minusDays(1))
            .build());
    }

    private MembershipPaymentAttempt saveAttempt(MembershipPayment payment, BillingKey key, int attemptNo,
                                                 MembershipPaymentAttemptStatus status) {
        return attempts.saveAndFlush(attempt(payment, key, attemptNo, status, "mpay_" + UUID.randomUUID()));
    }

    private static MembershipPaymentAttempt attempt(MembershipPayment payment, BillingKey key, int attemptNo,
                                                    MembershipPaymentAttemptStatus status, String portonePaymentId) {
        return MembershipPaymentAttempt.builder()
            .membershipPayment(payment).billingKey(key).attemptNo(attemptNo).portonePaymentId(portonePaymentId)
            .status(status).requestedAt(NOW).leaseExpiresAt(NOW.plusMinutes(5)).build();
    }
}
