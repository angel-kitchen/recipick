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

// 강한 차단 표시·늦은 수납 정정의 엔티티 규칙
class MembershipPaymentAttemptSettlementTransitionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 6, 10, 3);

    @Test
    @DisplayName("강한 차단은 약한 표시를 올려 쓰고, 이미 차단이면 첫 차단을 유지하며, 상태는 바꾸지 않는다")
    void blockOverwritesWeakFlagAndKeepsFirstBlock() {
        MembershipPaymentAttempt attempt = ready();
        attempt.flagOpsReview(NOW, MembershipOpsReviewReason.RENEWAL_LONG_DELAY);

        attempt.blockSettlement(NOW.plusMinutes(1), MembershipOpsReviewReason.UNDISPATCHED_PAID);
        attempt.blockSettlement(NOW.plusMinutes(2), MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT);

        assertThat(attempt.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.UNDISPATCHED_PAID);
        assertThat(attempt.getOpsReviewRequiredAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(attempt.isSettlementBlocked()).isTrue();
    }

    @Test
    @DisplayName("강한 차단 사유가 아니면 차단 메서드는 받지 않고, 약한 표시 메서드는 차단 사유를 받지 않는다")
    void blockingReasonsGoOnlyThroughBlockSettlement() {
        MembershipPaymentAttempt attempt = ready();

        assertThatThrownBy(() -> attempt.blockSettlement(NOW, MembershipOpsReviewReason.PAID_CONFLICT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt.flagOpsReview(NOW, MembershipOpsReviewReason.UNDISPATCHED_PG_ACTIVITY))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(attempt.getOpsReviewRequiredAt()).isNull();
    }

    @Test
    @DisplayName("차단된 READY는 임대 안이어도 선점되지 않는다")
    void blockedReadyIsNotClaimed() {
        MembershipPaymentAttempt attempt = ready();
        attempt.blockSettlement(NOW, MembershipOpsReviewReason.UNDISPATCHED_PG_ACTIVITY);

        assertThat(attempt.claim(NOW.plusMinutes(1))).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(attempt.isDispatched()).isFalse();
    }

    @Test
    @DisplayName("발송된 FAILED의 늦은 수납 정정은 PAID·환불 필요로 바꾸고 최초 실패 사유·확정 시각은 보존한다")
    void unexpectedPaidKeepsFailureHistory() {
        MembershipPaymentAttempt attempt = failed();
        String firstReason = attempt.getFailureReason();
        LocalDateTime firstCompletedAt = attempt.getCompletedAt();

        attempt.recordUnexpectedPaidForRefund(PAID_AT, NOW.plusHours(1));

        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt.getPgPaidAt()).isEqualTo(PAID_AT);
        assertThat(attempt.getRefundRequiredAt()).isEqualTo(NOW.plusHours(1));
        assertThat(attempt.getFailureReason()).isEqualTo(firstReason);
        assertThat(attempt.getCompletedAt()).isEqualTo(firstCompletedAt);
        assertThat(attempt.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.LATE_PAID_AFTER_FAILED);
    }

    @Test
    @DisplayName("늦은 수납 정정은 발송된 FAILED에서만 — 미발송·READY·PAID는 거절하고 markPaid의 허용 상태도 넓히지 않는다")
    void unexpectedPaidOnlyOnDispatchedFailed() {
        MembershipPaymentAttempt ready = ready();
        MembershipPaymentAttempt undispatchedFailed = MembershipPaymentAttempt.builder()
            .membershipPayment(payment()).attemptNo(1).billingKey(key()).portonePaymentId("mpay_fixture")
            .status(MembershipPaymentAttemptStatus.FAILED).requestedAt(NOW).leaseExpiresAt(NOW.plusMinutes(10))
            .completedAt(NOW).failureReason("PG_FAILED").build();

        assertThatThrownBy(() -> ready.recordUnexpectedPaidForRefund(PAID_AT, NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> undispatchedFailed.recordUnexpectedPaidForRefund(PAID_AT, NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed().markPaid(PAID_AT, NOW)).isInstanceOf(IllegalStateException.class);
    }

    private static MembershipPaymentAttempt ready() {
        return MembershipPaymentAttempt.ready(payment(), 1, key(), NOW, Duration.ofMinutes(10));
    }

    private static MembershipPaymentAttempt failed() {
        MembershipPaymentAttempt attempt = ready();
        attempt.claim(NOW.plusMinutes(1));
        attempt.markFailed("PG_FAILED", NOW.plusMinutes(2));
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
