package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

// 접수·선점·되돌리기·확정·불명·운영 확인·임대 만료. 허용되지 않는 전이는 예외, 선점류는 false
class MembershipPaymentAttemptTransitionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0, 0, 123_456_789);
    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 6, 10, 3);

    @Test
    @DisplayName("접수된 시도는 READY, 임대 = now + L, 결제 ID는 mpay_ + 32자 hex, 빌링키 고정이다")
    void acceptsReadyWithLeaseAndFixedKey() {
        BillingKey key = key();
        MembershipPaymentAttempt attempt = MembershipPaymentAttempt.ready(payment(), 1, key, NOW, LEASE);

        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(attempt.getAttemptNo()).isEqualTo(1);
        assertThat(attempt.getRequestedAt()).isEqualTo(NOW);
        assertThat(attempt.getLeaseExpiresAt()).isEqualTo(NOW.plus(LEASE));
        assertThat(attempt.getBillingKey()).isSameAs(key);
        assertThat(attempt.getPortonePaymentId()).matches("^mpay_[0-9a-f]{32}$");
        assertThat(attempt.isUnresolved()).isTrue();
        assertThat(attempt.toString()).doesNotContain("billing-key-test");
    }

    @Test
    @DisplayName("임대 만료 시각을 직접 주는 접수는 그 시각을 그대로 쓰고, now 이전·같은 시각은 받지 않는다")
    void readyWithExplicitLease() {
        LocalDateTime acceptUntil = NOW.plusMinutes(3);

        MembershipPaymentAttempt attempt = MembershipPaymentAttempt.ready(payment(), 2, key(), NOW, acceptUntil);

        assertThat(attempt.getLeaseExpiresAt()).isEqualTo(acceptUntil);
        assertThat(attempt.getAttemptNo()).isEqualTo(2);
        assertThatThrownBy(() -> MembershipPaymentAttempt.ready(payment(), 1, key(), NOW, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipPaymentAttempt.ready(payment(), 1, key(), NOW, NOW.minusSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("선점은 READY·임대 안에서만 성공하고 dispatchedAt을 DB 정밀도(마이크로초)로 남긴다")
    void claim() {
        MembershipPaymentAttempt attempt = ready();

        assertThat(attempt.claim(NOW.plusMinutes(1))).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(attempt.getDispatchedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 10, 1, 0, 123_456_000));
        // 두 번째 선점은 실패(다른 Worker가 먼저 집었다)
        assertThat(attempt.claim(NOW.plusMinutes(2))).isFalse();
    }

    @Test
    @DisplayName("임대가 끝난 READY는 선점할 수 없고 cancelNotDispatched로만 접는다")
    void expiredLease() {
        MembershipPaymentAttempt attempt = ready();

        assertThat(attempt.claim(NOW.plus(LEASE))).isFalse();
        assertThat(attempt.cancelNotDispatched(NOW.plusMinutes(5))).isFalse();
        assertThat(attempt.cancelNotDispatched(NOW.plus(LEASE))).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(attempt.getFailureReason()).isEqualTo("NOT_DISPATCHED");
        assertThat(attempt.isFinal()).isTrue();
        // 이미 접은 시도는 다시 접지 않는다
        assertThat(attempt.cancelNotDispatched(NOW.plus(LEASE))).isFalse();
    }

    @Test
    @DisplayName("POST를 보내지 않은 같은 선점자만 READY로 되돌릴 수 있다 — 나노초 차이는 마이크로초로 맞춰 같은 선점으로 본다")
    void releaseUnsent() {
        MembershipPaymentAttempt attempt = ready();
        LocalDateTime claimedAt = NOW.plusMinutes(1);
        attempt.claim(claimedAt);

        // 다른 선점 시각(다른 Worker가 집은 것처럼)은 되돌리지 못한다
        assertThat(attempt.releaseUnsent(claimedAt.plusSeconds(1))).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);

        // 같은 선점(나노초만 다름)은 되돌린다
        assertThat(attempt.releaseUnsent(claimedAt.withNano(123_456_999))).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(attempt.getDispatchedAt()).isNull();
        assertThat(attempt.getLeaseExpiresAt()).isEqualTo(NOW.plus(LEASE));
        // READY에서는 되돌릴 것이 없다
        assertThat(attempt.releaseUnsent(claimedAt)).isFalse();
    }

    @Test
    @DisplayName("DISPATCHING → PAID: 결제 시각·확정 시각을 남기고 확정 뒤에는 어떤 전이도 받지 않는다")
    void markPaid() {
        MembershipPaymentAttempt attempt = dispatching();

        attempt.markPaid(PAID_AT, NOW.plusMinutes(2));

        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt.getPgPaidAt()).isEqualTo(PAID_AT);
        assertThat(attempt.getCompletedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThatThrownBy(() -> attempt.markFailed("PG_FAILED", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> attempt.markUnknown("TIMEOUT")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> attempt.markManualReview("UNTRUSTED_ID", NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThat(attempt.claim(NOW)).isFalse();
        assertThat(attempt.cancelNotDispatched(NOW.plusHours(1))).isFalse();
    }

    @Test
    @DisplayName("DISPATCHING → UNKNOWN은 코드를 남기고, UNKNOWN에서 다시 부르면 코드만 바뀐다")
    void markUnknown() {
        MembershipPaymentAttempt attempt = dispatching();

        attempt.markUnknown("TIMEOUT");
        attempt.markUnknown("COMMON-008");

        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.UNKNOWN);
        assertThat(attempt.getFailureReason()).isEqualTo("COMMON-008");
        assertThat(attempt.isUnresolved()).isTrue();
    }

    @Test
    @DisplayName("UNKNOWN·MANUAL_REVIEW에서 늦은 결과로 PAID·FAILED를 확정할 수 있다")
    void lateResultFromUnresolved() {
        MembershipPaymentAttempt unknown = dispatching();
        unknown.markUnknown("TIMEOUT");
        unknown.markPaid(PAID_AT, NOW.plusHours(1));
        assertThat(unknown.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);

        MembershipPaymentAttempt review = dispatching();
        review.markManualReview("UNTRUSTED_ID", NOW);
        assertThat(review.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.MANUAL_REVIEW);
        review.markFailed("PG_FAILED", NOW.plusHours(1));
        assertThat(review.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.FAILED);
    }

    @Test
    @DisplayName("READY(미발송)에서는 PAID·FAILED·UNKNOWN을 적을 수 없다")
    void readyCannotRecordResult() {
        MembershipPaymentAttempt attempt = ready();

        assertThatThrownBy(() -> attempt.markPaid(PAID_AT, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> attempt.markFailed("PG_FAILED", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> attempt.markUnknown("TIMEOUT")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("환불 필요 표시는 PAID에서만, 운영 확인 표시는 처음 값만 남긴다")
    void requireRefundAndOpsReview() {
        MembershipPaymentAttempt attempt = dispatching();
        assertThatThrownBy(() -> attempt.requireRefund(NOW, MembershipOpsReviewReason.PAID_CONFLICT))
            .isInstanceOf(IllegalStateException.class);

        attempt.markPaid(PAID_AT, NOW);
        attempt.requireRefund(NOW.plusMinutes(1), MembershipOpsReviewReason.FIRST_PAYMENT_PERIOD_ENDED);
        attempt.requireRefund(NOW.plusMinutes(2), MembershipOpsReviewReason.PAID_CONFLICT);

        assertThat(attempt.getRefundRequiredAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(attempt.getOpsReviewRequiredAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(attempt.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.FIRST_PAYMENT_PERIOD_ENDED);
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
    }

    @Test
    @DisplayName("정합성 표시(0b)는 DISPATCHING·UNKNOWN에서 상태를 바꾸지 않고 failureReason 코드와 운영 표시(PAID_CONFLICT)만 남긴다")
    void markAppliedStateInconsistent() {
        MembershipPaymentAttempt attempt = dispatching();

        attempt.markAppliedStateInconsistent(NOW.plusMinutes(5));

        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(attempt.getFailureReason()).isEqualTo(MembershipPaymentAttempt.REASON_APPLIED_STATE_INCONSISTENT);
        assertThat(attempt.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_CONFLICT);
        assertThat(attempt.getOpsReviewRequiredAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(attempt.getPgPaidAt()).isNull();
        assertThat(attempt.getRefundRequiredAt()).isNull();
        assertThatThrownBy(() -> ready().markAppliedStateInconsistent(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("운영 확인 표시는 상태를 바꾸지 않는다(확인 지연 D 이상)")
    void flagOpsReviewKeepsStatus() {
        MembershipPaymentAttempt attempt = dispatching();
        attempt.markPaid(PAID_AT, NOW);

        attempt.flagOpsReview(NOW, MembershipOpsReviewReason.FIRST_PAYMENT_LONG_DELAY);

        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt.getRefundRequiredAt()).isNull();
        assertThat(attempt.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.FIRST_PAYMENT_LONG_DELAY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"card declined for a@b.c", "pg message with spaces", "", "lower_case", "한글"})
    @DisplayName("사유 코드가 아닌 자유 문장은 어디에도 저장하지 않는다")
    void rejectsFreeTextReason(String text) {
        MembershipPaymentAttempt attempt = dispatching();

        assertThatThrownBy(() -> attempt.markUnknown(text)).isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(attempt, MembershipPaymentAttemptStatus.DISPATCHING, null, null);
        assertThatThrownBy(() -> attempt.markFailed(text, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(attempt, MembershipPaymentAttemptStatus.DISPATCHING, null, null);
        assertThatThrownBy(() -> attempt.markManualReview(text, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(attempt, MembershipPaymentAttemptStatus.DISPATCHING, null, null);
    }

    @Test
    @DisplayName("거절된 전이는 상태·사유·확정 시각을 하나도 바꾸지 않는다(검증이 변경보다 먼저)")
    void rejectedTransitionLeavesFieldsUntouched() {
        MembershipPaymentAttempt attempt = dispatching();
        attempt.markUnknown("PG_TIMEOUT");

        assertThatThrownBy(() -> attempt.markFailed("PG_FAILED", null)).isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(attempt, MembershipPaymentAttemptStatus.UNKNOWN, "PG_TIMEOUT", null);
        assertThatThrownBy(() -> attempt.markFailed("bad reason", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(attempt, MembershipPaymentAttemptStatus.UNKNOWN, "PG_TIMEOUT", null);
        assertThatThrownBy(() -> attempt.markManualReview("PG_ID_MISMATCH", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(attempt, MembershipPaymentAttemptStatus.UNKNOWN, "PG_TIMEOUT", null);
        assertThatThrownBy(() -> attempt.markUnknown(null)).isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(attempt, MembershipPaymentAttemptStatus.UNKNOWN, "PG_TIMEOUT", null);
        assertThatThrownBy(() -> attempt.markAppliedStateInconsistent(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(attempt, MembershipPaymentAttemptStatus.UNKNOWN, "PG_TIMEOUT", null);
        assertThat(attempt.getOpsReviewRequiredAt()).isNull();
    }

    @Test
    @DisplayName("자동 정합화 중단은 정합성 코드와 운영 표시가 함께 있을 때만이다(다른 사유의 운영 표시는 중단 아님)")
    void autoReconcileHaltNeedsCodeAndFlag() {
        MembershipPaymentAttempt other = dispatching();
        other.flagOpsReview(NOW, MembershipOpsReviewReason.FIRST_PAYMENT_LONG_DELAY);
        MembershipPaymentAttempt inconsistent = dispatching();
        inconsistent.markAppliedStateInconsistent(NOW);

        assertThat(other.isAutoReconcileHalted()).isFalse();
        assertThat(inconsistent.isAutoReconcileHalted()).isTrue();
        assertThat(inconsistent.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(MembershipPaymentAttempt.isAutoReconcileHalted(
            MembershipPaymentAttempt.REASON_APPLIED_STATE_INCONSISTENT, null)).isFalse();
    }

    private static void assertUnchanged(MembershipPaymentAttempt attempt, MembershipPaymentAttemptStatus status,
                                        String failureReason, LocalDateTime completedAt) {
        assertThat(attempt.getStatus()).isEqualTo(status);
        assertThat(attempt.getFailureReason()).isEqualTo(failureReason);
        assertThat(attempt.getCompletedAt()).isEqualTo(completedAt);
    }

    @ParameterizedTest
    @EnumSource(value = MembershipPaymentAttemptStatus.class,
        names = {"READY", "DISPATCHING", "UNKNOWN", "MANUAL_REVIEW"})
    @DisplayName("미해결 4종은 isUnresolved, 그 외는 isFinal")
    void unresolvedStatuses(MembershipPaymentAttemptStatus status) {
        MembershipPaymentAttempt attempt = MembershipPaymentAttempt.builder().membershipPayment(payment())
            .attemptNo(1).billingKey(key()).portonePaymentId(MembershipPaymentAttempt.newPaymentId())
            .status(status).requestedAt(NOW).leaseExpiresAt(NOW.plus(LEASE)).build();

        assertThat(attempt.isUnresolved()).isTrue();
        assertThat(attempt.isFinal()).isFalse();
    }

    private static MembershipPaymentAttempt ready() {
        return MembershipPaymentAttempt.ready(payment(), 1, key(), NOW, LEASE);
    }

    private static MembershipPaymentAttempt dispatching() {
        MembershipPaymentAttempt attempt = ready();
        attempt.claim(NOW.plusMinutes(1));
        return attempt;
    }

    private static MembershipPayment payment() {
        return MembershipPayment.pendingFirst(10L, null, null, 3_900L, LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 12, 5));
    }

    private static BillingKey key() {
        return BillingKey.builder().memberId(7L).portoneBillingKey("billing-key-test-0001")
            .status(BillingKeyStatus.ACTIVE).issuedAt(NOW).build();
    }
}
