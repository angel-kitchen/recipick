package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.entity.enums.PointUsageStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PointUsageTest {

    private static final Long ORDER_ID = 1L;
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime RESERVED_AT = LocalDateTime.of(2026, 10, 28, 14, 0);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 28, 14, 3);

    @Test
    @DisplayName("사용 예약은 RESERVED이고 어느 적립 건에서 얼마를 썼는지 남긴다")
    void reserve() {
        // given
        PointEarning earning = usableEarning();

        // when
        PointUsage usage = PointUsage.reserve(earning, ORDER_ID, 300L, RESERVED_AT);

        // then
        assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.RESERVED);
        assertThat(usage.getPointEarning()).isSameAs(earning);
        assertThat(usage.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(usage.getAmount()).isEqualTo(300L);
        assertThat(usage.getReservedAt()).isEqualTo(RESERVED_AT);
    }

    @Test
    @DisplayName("확정되지 않은 적립 건(PENDING·CANCELED·EXPIRED)에는 사용을 예약할 수 없다")
    void rejectReserveOnNotCompletedEarning() {
        // given
        PointEarning pending = PointEarning.pending(7L, 100L, 500L);
        PointEarning canceled = PointEarning.pending(7L, 101L, 500L);
        canceled.cancel();
        PointEarning expired = usableEarning();
        expired.expire();

        // when
        Throwable fromPending = catchThrowable(() -> PointUsage.reserve(pending, ORDER_ID, 100L, RESERVED_AT));
        Throwable fromCanceled = catchThrowable(() -> PointUsage.reserve(canceled, ORDER_ID, 100L, RESERVED_AT));
        Throwable fromExpired = catchThrowable(() -> PointUsage.reserve(expired, ORDER_ID, 100L, RESERVED_AT));

        // then
        assertThat(fromPending).isInstanceOf(IllegalStateException.class);
        assertThat(fromCanceled).isInstanceOf(IllegalStateException.class);
        assertThat(fromExpired).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("적립 건·주문 ID·예약 시각 중 하나라도 없으면 사용을 예약할 수 없다")
    void rejectReserveWithNull() {
        // when
        Throwable nullEarning = catchThrowable(() -> PointUsage.reserve(null, ORDER_ID, 100L, RESERVED_AT));
        Throwable nullOrder = catchThrowable(() -> PointUsage.reserve(usableEarning(), null, 100L, RESERVED_AT));
        Throwable nullTime = catchThrowable(() -> PointUsage.reserve(usableEarning(), ORDER_ID, 100L, null));

        // then
        assertThat(nullEarning).isInstanceOf(NullPointerException.class);
        assertThat(nullOrder).isInstanceOf(NullPointerException.class);
        assertThat(nullTime).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("0 이하 금액은 사용 예약할 수 없다")
    void rejectNonPositiveReserve(long amount) {
        // when
        Throwable thrown = catchThrowable(() -> PointUsage.reserve(usableEarning(), ORDER_ID, amount, RESERVED_AT));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결제 성공으로 확정하면 CONFIRMED이고 다시 확정하면 아무것도 바꾸지 않고 false다")
    void confirm() {
        // given
        PointUsage usage = reserved();

        // when
        boolean first = usage.confirm(PAID_AT);
        boolean second = usage.confirm(PAID_AT.plusMinutes(1));

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.CONFIRMED);
        assertThat(usage.getConfirmedAt()).isEqualTo(PAID_AT);
    }

    @Test
    @DisplayName("예약 상태를 확정 시각 없이 확정하면 거부하고 RESERVED 그대로 둔다")
    void rejectConfirmWithoutTime() {
        // given
        PointUsage usage = reserved();

        // when
        Throwable thrown = catchThrowable(() -> usage.confirm(null));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.RESERVED);
        assertThat(usage.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("이미 확정된 사용은 확정 시각 없이 다시 불러도 no-op이다")
    void confirmTwiceWithoutTime() {
        // given
        PointUsage usage = reserved();
        usage.confirm(PAID_AT);

        // when
        boolean changed = usage.confirm(null);

        // then
        assertThat(changed).isFalse();
        assertThat(usage.getConfirmedAt()).isEqualTo(PAID_AT);
    }

    @Test
    @DisplayName("예약 상태에서도 확정 상태에서도 복원할 수 있고, 확정됐던 시각은 지우지 않는다")
    void restoreFromReservedOrConfirmed() {
        // given
        PointUsage reserved = reserved();
        PointUsage confirmed = reserved();
        confirmed.confirm(PAID_AT);

        // when
        boolean fromReserved = reserved.restore();
        boolean fromConfirmed = confirmed.restore();

        // then
        assertThat(fromReserved).isTrue();
        assertThat(fromConfirmed).isTrue();
        assertThat(reserved.getStatus()).isEqualTo(PointUsageStatus.RESTORED);
        assertThat(confirmed.getStatus()).isEqualTo(PointUsageStatus.RESTORED);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(PAID_AT);
    }

    @Test
    @DisplayName("이미 복원된 사용을 다시 복원하면 false다 — 이중 복원 방지")
    void restoreTwice() {
        // given
        PointUsage usage = reserved();
        usage.restore();

        // when
        boolean changed = usage.restore();

        // then
        assertThat(changed).isFalse();
        assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.RESTORED);
    }

    @Test
    @DisplayName("복원된 사용은 확정할 수 없다")
    void rejectConfirmAfterRestore() {
        // given
        PointUsage usage = reserved();
        usage.restore();

        // when
        Throwable thrown = catchThrowable(() -> usage.confirm(PAID_AT));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.RESTORED);
    }

    private static PointEarning usableEarning() {
        PointEarning earning = PointEarning.pending(7L, 100L, 500L);
        earning.complete(DELIVERED_AT);
        return earning;
    }

    private static PointUsage reserved() {
        return PointUsage.reserve(usableEarning(), ORDER_ID, 300L, RESERVED_AT);
    }
}
