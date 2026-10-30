package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.entity.enums.PointHistoryType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointHistoryTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long EARNING_ORDER_ID = 100L;
    private static final Long USAGE_ORDER_ID = 200L;
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime RESERVED_AT = LocalDateTime.of(2026, 10, 28, 14, 0);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 28, 14, 3);

    @Test
    @DisplayName("적립 이력 3종은 적립 건의 주문·적립액으로 남고 회원은 적립 건의 회원이다")
    void earningFactories() {
        // given
        PointEarning pending = PointEarning.pending(MEMBER_ID, EARNING_ORDER_ID, 500L);
        PointEarning completed = completedEarning();
        PointEarning canceled = PointEarning.pending(MEMBER_ID, EARNING_ORDER_ID, 500L);
        canceled.cancel();

        // when
        PointHistory earnPending = PointHistory.earnPending(pending);
        PointHistory earnCompleted = PointHistory.earnCompleted(completed);
        PointHistory earnCanceled = PointHistory.earnCanceled(canceled);

        // then
        assertThat(earnPending.getType()).isEqualTo(PointHistoryType.EARN_PENDING);
        assertThat(earnCompleted.getType()).isEqualTo(PointHistoryType.EARN_COMPLETED);
        assertThat(earnCanceled.getType()).isEqualTo(PointHistoryType.EARN_CANCELED);
        assertThat(earnPending.getOrderId()).isEqualTo(EARNING_ORDER_ID);
        assertThat(earnCompleted.getAmount()).isEqualTo(500L);
        assertThat(earnCanceled.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(earnCompleted.getPointEarning()).isSameAs(completed);
    }

    @Test
    @DisplayName("사용 이력 3종은 사용한 주문·사용액으로 남고 적립 건은 사용 내역이 가리키는 적립 건이다")
    void usageFactories() {
        // given
        PointEarning earning = completedEarning();
        PointUsage reserved = PointUsage.reserve(earning, USAGE_ORDER_ID, 300L, RESERVED_AT);
        PointUsage confirmed = PointUsage.reserve(earning, USAGE_ORDER_ID, 300L, RESERVED_AT);
        confirmed.confirm(PAID_AT);
        PointUsage restored = PointUsage.reserve(earning, USAGE_ORDER_ID, 300L, RESERVED_AT);
        restored.restore();

        // when
        PointHistory useReserved = PointHistory.useReserved(reserved);
        PointHistory useConfirmed = PointHistory.useConfirmed(confirmed);
        PointHistory restoredHistory = PointHistory.restored(restored);

        // then
        assertThat(useReserved.getType()).isEqualTo(PointHistoryType.USE_RESERVED);
        assertThat(useConfirmed.getType()).isEqualTo(PointHistoryType.USE_CONFIRMED);
        assertThat(restoredHistory.getType()).isEqualTo(PointHistoryType.RESTORED);
        assertThat(useReserved.getOrderId()).isEqualTo(USAGE_ORDER_ID);
        assertThat(useConfirmed.getAmount()).isEqualTo(300L);
        assertThat(restoredHistory.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(restoredHistory.getPointEarning()).isSameAs(earning);
    }

    @Test
    @DisplayName("소멸 이력은 만료 처리된 적립 건에만 남고, 복원 직후 소멸은 그 주문을, 지연 만료는 주문 없이 남는다")
    void expiredFactory() {
        // given
        PointEarning earning = completedEarning();
        earning.expire();

        // when
        PointHistory expiredAfterRestore = PointHistory.expired(earning, USAGE_ORDER_ID, 300L);
        PointHistory lazyExpired = PointHistory.expired(earning, null, 200L);

        // then
        assertThat(expiredAfterRestore.getType()).isEqualTo(PointHistoryType.EXPIRED);
        assertThat(expiredAfterRestore.getOrderId()).isEqualTo(USAGE_ORDER_ID);
        assertThat(expiredAfterRestore.getAmount()).isEqualTo(300L);
        assertThat(lazyExpired.getOrderId()).isNull();
        assertThat(lazyExpired.getAmount()).isEqualTo(200L);
    }

    @Test
    @DisplayName("탈퇴 소멸 이력은 탈퇴 소멸된 적립 건에만 남고, 탈퇴 소멸은 주문 없이, 복원 직후 소멸은 그 주문으로 남는다")
    void withdrawalForfeitedFactory() {
        // given
        PointEarning earning = completedEarning();
        earning.forfeit();

        // when
        PointHistory forfeited = PointHistory.withdrawalForfeited(earning, null, 500L);
        PointHistory forfeitedAfterRestore = PointHistory.withdrawalForfeited(earning, USAGE_ORDER_ID, 300L);

        // then
        assertThat(forfeited.getType()).isEqualTo(PointHistoryType.WITHDRAWAL_FORFEITED);
        assertThat(forfeited.getOrderId()).isNull();
        assertThat(forfeited.getAmount()).isEqualTo(500L);
        assertThat(forfeited.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(forfeited.getPointEarning()).isSameAs(earning);
        assertThat(forfeitedAfterRestore.getOrderId()).isEqualTo(USAGE_ORDER_ID);
        assertThat(forfeitedAfterRestore.getAmount()).isEqualTo(300L);
    }

    @Test
    @DisplayName("탈퇴 소멸되지 않은 적립 건이나 0원으로는 탈퇴 소멸 이력을 만들 수 없다 — 자연 만료 건과 섞이지 않는다")
    void rejectWithdrawalForfeitedNotForfeited() {
        // given
        PointEarning completed = completedEarning();
        PointEarning expired = completedEarning();
        expired.expire();
        PointEarning forfeited = completedEarning();
        forfeited.forfeit();

        // when
        Throwable fromCompleted = catchThrowable(() -> PointHistory.withdrawalForfeited(completed, null, 500L));
        Throwable fromExpired = catchThrowable(() -> PointHistory.withdrawalForfeited(expired, null, 500L));
        Throwable zeroAmount = catchThrowable(() -> PointHistory.withdrawalForfeited(forfeited, null, 0L));

        // then
        assertThat(fromCompleted).isInstanceOf(IllegalStateException.class);
        assertThat(fromExpired).isInstanceOf(IllegalStateException.class);
        assertThat(zeroAmount).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대상의 현재 상태와 맞지 않는 이력은 만들 수 없다 — 적립 예정 건의 확정 이력, 예약 상태의 확정 이력")
    void rejectMismatchedStatus() {
        // given
        PointEarning pending = PointEarning.pending(MEMBER_ID, EARNING_ORDER_ID, 500L);
        PointUsage reserved = PointUsage.reserve(completedEarning(), USAGE_ORDER_ID, 300L, RESERVED_AT);

        // when
        Throwable earnCompleted = catchThrowable(() -> PointHistory.earnCompleted(pending));
        Throwable useConfirmed = catchThrowable(() -> PointHistory.useConfirmed(reserved));

        // then
        assertThat(earnCompleted).isInstanceOf(IllegalStateException.class);
        assertThat(useConfirmed).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("0원 이동은 이력으로 남기지 않는다 — 0원 적립 예정 건으로 이력을 만들면 내부 오류다")
    void rejectZeroAmount() {
        // given
        PointEarning zero = PointEarning.pending(MEMBER_ID, EARNING_ORDER_ID, 0L);
        PointEarning expired = completedEarning();
        expired.expire();

        // when
        Throwable pending = catchThrowable(() -> PointHistory.earnPending(zero));
        Throwable expiredZero = catchThrowable(() -> PointHistory.expired(expired, null, 0L));

        // then
        assertThat(pending).isInstanceOf(IllegalArgumentException.class);
        assertThat(expiredZero).isInstanceOf(IllegalArgumentException.class);
    }

    private static PointEarning completedEarning() {
        PointEarning earning = PointEarning.pending(MEMBER_ID, EARNING_ORDER_ID, 500L);
        earning.complete(DELIVERED_AT);
        return earning;
    }
}
