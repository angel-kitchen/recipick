package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// SYSTEM 환불 요청 팩토리. 실제 수납액을 명시적으로 받고, PAID + 환불 필요 표시가 된 시도에서만 만든다.
// 저장된 시도(ID 있음)가 전제라 단위 테스트는 ReflectionTestUtils로 ID만 채운다
class MembershipRefundSystemRefundTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 10);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 6, 10, 3);

    @Test
    @DisplayName("환불 필요 표시가 된 PAID 시도에서 실제 수납액으로 REQUESTED·SYSTEM·키 mrf_sys_{attemptId}_{n} 행을 만든다")
    void systemRefundRequested() {
        MembershipPaymentAttempt attempt = paidRequiringRefund(42L);

        MembershipRefund refund = MembershipRefund.systemRefundRequested(attempt, 3_800L,
            MembershipOpsReviewReason.PAID_AMOUNT_MISMATCH.name(), 1, NOW);

        assertThat(refund.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(refund.getAmount()).isEqualTo(3_800L);
        assertThat(refund.getUsedDays()).isZero();
        assertThat(refund.getUsedDayFee()).isZero();
        assertThat(refund.getPenaltyAmount()).isZero();
        assertThat(refund.getReason()).isEqualTo("PAID_AMOUNT_MISMATCH");
        assertThat(refund.getIdempotencyKey()).isEqualTo("mrf_sys_42_1");
        assertThat(refund.getMembershipPaymentAttempt()).isSameAs(attempt);
        assertThat(refund.getMembershipPayment()).isSameAs(attempt.getMembershipPayment());
        assertThat(refund.getRequestedAt()).isEqualTo(NOW);
        assertThat(refund.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("PAID가 아니거나 환불 필요 표시가 없는 시도로는 만들 수 없다")
    void rejectsAttemptWithoutRefundRequirement() {
        MembershipPaymentAttempt dispatching = attempt(1L);
        dispatching.claim(NOW);
        assertThatThrownBy(() -> MembershipRefund.systemRefundRequested(dispatching, 3_900L, "PAID_CONFLICT", 1, NOW))
            .isInstanceOf(IllegalArgumentException.class);

        MembershipPaymentAttempt paidWithoutFlag = attempt(2L);
        paidWithoutFlag.claim(NOW);
        paidWithoutFlag.markPaid(PAID_AT, NOW);
        assertThatThrownBy(() -> MembershipRefund.systemRefundRequested(paidWithoutFlag, 3_900L, "PAID_CONFLICT", 1,
            NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("금액 0 이하·자유 문장 사유·순번 0·저장되지 않은 시도는 예외다 — 회차 금액을 기본값으로 쓰지 않는다")
    void rejectsInvalidArguments() {
        MembershipPaymentAttempt attempt = paidRequiringRefund(3L);

        assertThatThrownBy(() -> MembershipRefund.systemRefundRequested(attempt, 0L, "PAID_CONFLICT", 1, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipRefund.systemRefundRequested(attempt, 3_900L, "card declined", 1, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipRefund.systemRefundRequested(attempt, 3_900L, "PAID_CONFLICT", 0, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipRefund.systemRefundRequested(attempt, 3_900L, "PAID_CONFLICT", 1, null))
            .isInstanceOf(IllegalArgumentException.class);

        MembershipPaymentAttempt unsaved = attempt(null);
        unsaved.claim(NOW);
        unsaved.markPaid(PAID_AT, NOW);
        unsaved.requireRefund(NOW, MembershipOpsReviewReason.PAID_CONFLICT);
        assertThatThrownBy(() -> MembershipRefund.systemRefundRequested(unsaved, 3_900L, "PAID_CONFLICT", 1, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static MembershipPaymentAttempt paidRequiringRefund(Long id) {
        MembershipPaymentAttempt attempt = attempt(id);
        attempt.claim(NOW);
        attempt.markPaid(PAID_AT, NOW);
        attempt.requireRefund(NOW, MembershipOpsReviewReason.PAID_CONFLICT);
        return attempt;
    }

    private static MembershipPaymentAttempt attempt(Long id) {
        MembershipPayment payment = MembershipPayment.pendingFirst(10L, null, null, 3_900L,
            LocalDate.of(2026, 11, 6), LocalDate.of(2026, 12, 5));
        BillingKey key = BillingKey.builder().memberId(7L).portoneBillingKey("billing-key-test-0001")
            .status(BillingKeyStatus.ACTIVE).issuedAt(NOW).build();
        MembershipPaymentAttempt attempt = MembershipPaymentAttempt.ready(payment, 1, key, NOW.minusMinutes(5),
            Duration.ofMinutes(10));
        if (id != null) {
            ReflectionTestUtils.setField(attempt, "id", id);
        }
        return attempt;
    }
}
