package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipBillingPeriods.RenewalPeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 갱신 회차의 생성·대기·재개 전이. 확정 회차는 어떤 전이도 받지 않고, 첫 결제 회차에는 갱신 전이를 쓰지 않는다
class MembershipPaymentRenewalTransitionTest {

    private static final RenewalPeriod PERIOD = MembershipBillingPeriods.renewalPeriod(LocalDate.of(2026, 1, 31),
        LocalDateTime.of(2026, 1, 31, 10, 3), LocalDateTime.of(2026, 2, 28, 10, 3));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 28, 10, 5);

    @Test
    @DisplayName("갱신 회차는 PENDING·저장된 기간·결제 마감 G로 만들어지고 멱등 키·재시도 시각은 없다")
    void pendingRenewal() {
        // when
        MembershipPayment payment = renewal(10L);

        // then
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(payment.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(payment.getBillingDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(payment.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(payment.getGraceDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 3, 3, 10, 3));
        assertThat(payment.getIdempotencyKey()).isNull();
        assertThat(payment.getNextRetryAt()).isNull();
        assertThat(payment.isRenewal()).isTrue();
        assertThat(payment.isFirstPayment()).isFalse();
    }

    @Test
    @DisplayName("이용권·금액·검증된 기간이 없으면 갱신 회차를 만들지 않는다 — 기간 값의 관계는 RenewalPeriod가 먼저 막는다")
    void rejectsInvalidRenewal() {
        // when / then
        assertThatThrownBy(() -> MembershipPayment.pendingRenewal(null, 3_900L, PERIOD))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipPayment.pendingRenewal(10L, 0L, PERIOD))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipPayment.pendingRenewal(10L, 3_900L, null))
            .isInstanceOf(IllegalArgumentException.class);
        // 마감이 기간 밖인 값은 RenewalPeriod 자체가 만들어지지 않아 회차 생성까지 오지 못한다
        assertThatThrownBy(() -> new RenewalPeriod(PERIOD.periodStart(), PERIOD.periodEnd(), PERIOD.previousEndAt(),
            PERIOD.endAt().plusDays(1), PERIOD.endAt(), PERIOD.graceEndAt()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("재시도 대기는 마감 전 시각 또는 null(마감에서 정리)만 받고, 같은 상태에서 다시 불러도 된다")
    void waitForRetry() {
        // given
        MembershipPayment payment = renewal(10L);

        // when
        payment.waitForRetry(NOW.plusDays(1), "PG_FAILED");
        LocalDateTime first = payment.getNextRetryAt();
        payment.waitForRetry(null, "PG_FAILED");

        // then
        assertThat(first).isEqualTo(NOW.plusDays(1));
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(payment.getNextRetryAt()).isNull();
        assertThat(payment.getFailureReason()).isEqualTo("PG_FAILED");
        assertThatThrownBy(() -> payment.waitForRetry(PERIOD.graceDeadlineAt(), "PG_FAILED"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.waitForRetry(null, "declined by card"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(payment.getFailureReason()).isEqualTo("PG_FAILED");
    }

    @Test
    @DisplayName("UNKNOWN·MANUAL_REVIEW 회차도 신뢰 가능한 실패 뒤 재시도 대기로 갈 수 있다")
    void waitForRetryFromUnknownAndManualReview() {
        // given
        MembershipPayment unknown = renewal(10L);
        unknown.markUnknown();
        MembershipPayment review = renewal(11L);
        review.markManualReview();

        // when
        unknown.waitForRetry(null, "PG_FAILED");
        review.waitForRetry(null, "PG_FAILED");

        // then
        assertThat(unknown.getStatus()).isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(review.getStatus()).isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
    }

    @Test
    @DisplayName("카드 대기는 앞선 재시도 시각을 지우지 않고, 재개는 PENDING으로 돌리며 예약을 소비한다")
    void paymentMethodWaitKeepsRetryAtAndResumeClearsIt() {
        // given
        MembershipPayment payment = renewal(10L);
        payment.waitForRetry(NOW.plusDays(1), "PG_FAILED");

        // when
        payment.waitForPaymentMethod();
        payment.waitForPaymentMethod();
        LocalDateTime kept = payment.getNextRetryAt();
        MembershipPaymentStatus waiting = payment.getStatus();
        payment.resumePending();

        // then
        assertThat(waiting).isEqualTo(MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED);
        assertThat(kept).isEqualTo(NOW.plusDays(1));
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(payment.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("확정(PAID·FAILED)은 재시도 시각을 비우고, 확정 뒤에는 어떤 갱신 전이도 받지 않는다")
    void resolvedPaymentRejectsRenewalTransitions() {
        // given
        MembershipPayment failed = renewal(10L);
        failed.waitForRetry(NOW.plusDays(1), "PG_FAILED");
        MembershipPayment paid = renewal(11L);
        paid.waitForRetry(NOW.plusDays(1), "PG_FAILED");
        paid.resumePending();

        // when
        failed.markFailed("RENEWAL_ATTEMPTS_EXHAUSTED");
        paid.markPaid(NOW, NOW);

        // then
        assertThat(failed.getNextRetryAt()).isNull();
        assertThat(paid.getNextRetryAt()).isNull();
        assertThatThrownBy(() -> failed.waitForRetry(null, "PG_FAILED")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(failed::waitForPaymentMethod).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(paid::resumePending).isInstanceOf(IllegalStateException.class);
        assertThat(failed.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(paid.getStatus()).isEqualTo(MembershipPaymentStatus.PAID);
    }

    private static MembershipPayment renewal(Long membershipId) {
        return MembershipPayment.pendingRenewal(membershipId, 3_900L, PERIOD);
    }

    @Test
    @DisplayName("첫 결제 회차에는 갱신 전이를 쓸 수 없다")
    void firstPaymentRejectsRenewalTransitions() {
        // given
        MembershipPayment first = MembershipPayment.pendingFirst(10L, "7:key", "hash", 3_900L,
            LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 27));

        // when / then
        assertThatThrownBy(() -> first.waitForRetry(null, "PG_FAILED")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(first::waitForPaymentMethod).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(first::resumePending).isInstanceOf(IllegalStateException.class);
        assertThat(first.getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
    }
}
