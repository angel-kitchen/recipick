package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PointEarningTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1L;
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 10, 28, 14, 0);

    @Test
    @DisplayName("적립 예정은 PENDING이고 아직 쓸 수 없어 잔량이 0이다 — 적립액이 0원이어도 만든다")
    void pending() {
        // when
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID, 0L);

        // then
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getAmount()).isZero();
        assertThat(earning.getRemainingAmount()).isZero();
        assertThat(earning.getExpiresOn()).isNull();
    }

    @Test
    @DisplayName("적립 예정액이 음수면 만들 수 없다")
    void rejectNegativePending() {
        // when
        Throwable thrown = catchThrowable(() -> PointEarning.pending(MEMBER_ID, ORDER_ID, -1L));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("회원 ID나 주문 ID가 없으면 적립 예정을 만들 수 없다")
    void rejectNullIds() {
        // when
        Throwable nullMember = catchThrowable(() -> PointEarning.pending(null, ORDER_ID, 500L));
        Throwable nullOrder = catchThrowable(() -> PointEarning.pending(MEMBER_ID, null, 500L));

        // then
        assertThat(nullMember).isInstanceOf(NullPointerException.class);
        assertThat(nullOrder).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("확정 시각 없이 확정하면 거부하고 PENDING 상태와 잔량 0을 그대로 유지한다")
    void rejectCompleteWithoutTime() {
        // given
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID, 500L);

        // when
        Throwable thrown = catchThrowable(() -> earning.complete(null));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getRemainingAmount()).isZero();
        assertThat(earning.getConfirmedAt()).isNull();
        assertThat(earning.getExpiresOn()).isNull();
    }

    @Test
    @DisplayName("적립 확정 시 COMPLETED가 되고 확정 시각·1년 뒤 만료일·잔량이 채워진다")
    void complete() {
        // given
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID, 500L);

        // when
        boolean changed = earning.complete(DELIVERED_AT);

        // then
        assertThat(changed).isTrue();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(earning.getConfirmedAt()).isEqualTo(DELIVERED_AT);
        assertThat(earning.getExpiresOn()).isEqualTo(LocalDate.of(2027, 10, 28));
        assertThat(earning.getRemainingAmount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("이미 확정된 적립을 다시 확정하면 아무것도 바꾸지 않고 false다 — 중복 적립 방지")
    void completeTwice() {
        // given
        PointEarning earning = completed(500L, DELIVERED_AT);
        earning.use(200L);

        // when
        boolean changed = earning.complete(DELIVERED_AT.plusDays(3));

        // then
        assertThat(changed).isFalse();
        assertThat(earning.getConfirmedAt()).isEqualTo(DELIVERED_AT);
        assertThat(earning.getRemainingAmount()).isEqualTo(300L);
    }

    @Test
    @DisplayName("적립 예정을 취소하면 CANCELED이고 다시 취소하면 false다")
    void cancel() {
        // given
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID, 500L);

        // when
        boolean first = earning.cancel();
        boolean second = earning.cancel();

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.CANCELED);
    }

    @Test
    @DisplayName("취소된 적립은 확정할 수 없다")
    void rejectCompleteAfterCancel() {
        // given
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID, 500L);
        earning.cancel();

        // when
        Throwable thrown = catchThrowable(() -> earning.complete(DELIVERED_AT));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.CANCELED);
    }

    @Test
    @DisplayName("확정된 적립은 취소할 수 없다 — 배송완료 뒤 포인트 회수(clawback)는 없다")
    void rejectCancelAfterComplete() {
        // given
        PointEarning earning = completed(500L, DELIVERED_AT);

        // when
        Throwable thrown = catchThrowable(earning::cancel);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(earning.getRemainingAmount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("확정된 적립 건에서 잔량만큼 쓰고, 쓴 만큼 원래 금액까지 되돌린다")
    void useAndRestore() {
        // given
        PointEarning earning = completed(500L, DELIVERED_AT);

        // when
        earning.use(500L);
        earning.restore(300L);

        // then
        assertThat(earning.getRemainingAmount()).isEqualTo(300L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 501L})
    @DisplayName("잔량을 넘거나 0 이하로 쓸 수 없다")
    void rejectUseOutOfRange(long amount) {
        // given
        PointEarning earning = completed(500L, DELIVERED_AT);

        // when
        Throwable thrown = catchThrowable(() -> earning.use(amount));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(earning.getRemainingAmount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("원래 적립액을 넘겨 복원하면 같은 사용을 두 번 되돌린 것이라 내부 오류다")
    void rejectRestoreOverAmount() {
        // given
        PointEarning earning = completed(500L, DELIVERED_AT);
        earning.use(200L);

        // when
        Throwable thrown = catchThrowable(() -> earning.restore(201L));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(earning.getRemainingAmount()).isEqualTo(300L);
    }

    @Test
    @DisplayName("적립 예정 건은 아직 쓸 수 없다")
    void rejectUsePending() {
        // given
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID, 500L);

        // when
        Throwable thrown = catchThrowable(() -> earning.use(100L));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료하면 소멸한 잔량을 돌려주고 잔량은 0이 되며, 다시 만료하면 0을 돌려준다")
    void expire() {
        // given
        PointEarning earning = completed(500L, DELIVERED_AT);
        earning.use(200L);

        // when
        long first = earning.expire();
        long second = earning.expire();

        // then
        assertThat(first).isEqualTo(300L);
        assertThat(second).isZero();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.EXPIRED);
        assertThat(earning.getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("만료일 당일까지 사용할 수 있고 다음 날부터는 쓸 수 없다")
    void usableUntilExpiryDate() {
        // given — 만료일 2027-10-28
        PointEarning earning = completed(500L, DELIVERED_AT);

        // then
        assertThat(earning.isUsableOn(LocalDate.of(2027, 10, 28))).isTrue();
        assertThat(earning.isUsableOn(LocalDate.of(2027, 10, 29))).isFalse();
    }

    @Test
    @DisplayName("기준 날짜 없이 사용 가능 여부를 물으면 거부한다")
    void rejectUsableCheckWithoutDate() {
        // given
        PointEarning earning = completed(500L, DELIVERED_AT);

        // when
        Throwable thrown = catchThrowable(() -> earning.isUsableOn(null));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("다 써서 잔량이 0인 적립 건은 만료일이 지나도 COMPLETED로 남지만 날짜 기준으로 사용 불가다 — 복원 판단은 상태가 아니라 날짜로 한다")
    void exhaustedLotIsNotUsableAfterExpiryDate() {
        // given — 만료일이 어제인 적립 건을 전부 썼다
        LocalDate today = LocalDate.of(2026, 10, 28);
        PointEarning earning = completed(500L, today.minusDays(1).minusYears(1).atTime(10, 0));
        earning.use(500L);

        // then
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(earning.getRemainingAmount()).isZero();
        assertThat(earning.getExpiresOn()).isEqualTo(today.minusDays(1));
        assertThat(earning.isUsableOn(today)).isFalse();
    }

    private static PointEarning completed(long amount, LocalDateTime deliveredAt) {
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID, amount);
        earning.complete(deliveredAt);
        return earning;
    }
}
