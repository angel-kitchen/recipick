package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// F-4(회차). 상세 설계 5-3 표: 허용 전이·거절·멱등. 확정 회차는 어떤 메서드도 바꾸지 않는다(I-16)
class MembershipPaymentTransitionTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 6);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 6, 10, 3);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 5);

    @Test
    @DisplayName("첫 결제 회차는 PENDING·임시 기간·유예 마감 없음으로 만들어진다")
    void pendingFirst() {
        MembershipPayment payment = MembershipPayment.pendingFirst(10L, "7:key", "hash", 3_900L, START,
            START.plusMonths(1).minusDays(1));

        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(payment.getBillingDate()).isEqualTo(START);
        assertThat(payment.getPeriodStartDate()).isEqualTo(START);
        assertThat(payment.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(payment.getGraceDeadlineAt()).isNull();
        assertThat(payment.isFirstPayment()).isTrue();
        assertThat(payment.isUnresolved()).isTrue();
        assertThat(payment.isApplied()).isFalse();
    }

    @Test
    @DisplayName("기간 반영과 PAID 확정은 PG 시각 기준 기간·결제 시각·반영 시각을 남긴다")
    void applyPeriodAndMarkPaid() {
        MembershipPayment payment = pending();

        payment.applyFirstPeriod(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 12, 6));
        payment.markPaid(PAID_AT, NOW);

        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.PAID);
        assertThat(payment.getBillingDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(payment.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(payment.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
        assertThat(payment.getMembershipAppliedAt()).isEqualTo(NOW);
        assertThat(payment.isApplied()).isTrue();
        assertThat(payment.isUnresolved()).isFalse();
    }

    @Test
    @DisplayName("PENDING → UNKNOWN → MANUAL_REVIEW 순으로 갈 수 있고 각각 멱등이다")
    void unknownAndManualReview() {
        MembershipPayment payment = pending();

        payment.markUnknown();
        payment.markUnknown();
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.UNKNOWN);

        payment.markManualReview();
        payment.markManualReview();
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.MANUAL_REVIEW);
        assertThat(payment.isUnresolved()).isTrue();
    }

    @Test
    @DisplayName("UNKNOWN·MANUAL_REVIEW에서도 늦은 결과로 PAID·FAILED 확정이 가능하다")
    void lateResultFromUnresolved() {
        MembershipPayment unknown = pending();
        unknown.markUnknown();
        unknown.markPaid(PAID_AT, NOW);
        assertThat(unknown.getStatus()).isEqualTo(MembershipPaymentStatus.PAID);

        MembershipPayment review = pending();
        review.markUnknown();
        review.markManualReview();
        review.closeUnapplied("PAID_CONFLICT");
        assertThat(review.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(review.getFailureReason()).isEqualTo("PAID_CONFLICT");
    }

    @ParameterizedTest
    @EnumSource(value = MembershipPaymentStatus.class, names = {"PAID", "FAILED"})
    @DisplayName("확정 회차는 기간 반영·PAID·FAILED·UNKNOWN·MANUAL_REVIEW 어느 것도 받지 않는다")
    void finalStatesRejectEveryTransition(MembershipPaymentStatus finalStatus) {
        MembershipPayment payment = pending();
        if (finalStatus == MembershipPaymentStatus.PAID) {
            payment.markPaid(PAID_AT, NOW);
        } else {
            payment.markFailed("PG_FAILED");
        }

        assertThatThrownBy(() -> payment.applyFirstPeriod(START, START.plusDays(30)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.markPaid(PAID_AT, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.markFailed("PG_FAILED")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(payment::markUnknown).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(payment::markManualReview).isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(finalStatus);
    }

    @Test
    @DisplayName("잘못된 생성·기간 값은 예외다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> MembershipPayment.pendingFirst(null, null, null, 3_900L, START, START.plusDays(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipPayment.pendingFirst(10L, null, null, 0L, START, START.plusDays(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipPayment.pendingFirst(10L, null, null, 3_900L, START, START))
            .isInstanceOf(IllegalArgumentException.class);
        MembershipPayment payment = pending();
        assertThatThrownBy(() -> payment.applyFirstPeriod(START, START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.markPaid(null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private static MembershipPayment pending() {
        return MembershipPayment.pendingFirst(10L, null, null, 3_900L, START, START.plusMonths(1).minusDays(1));
    }
}
