package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberPointTest {

    @Test
    @DisplayName("회원 ID가 없거나 잔액이 없거나 음수이면 회원 포인트를 만들 수 없다")
    void rejectInvalidCreation() {
        // when
        Throwable nullMember = catchThrowable(() -> MemberPoint.builder().memberId(null).balance(0L).build());
        Throwable nullBalance = catchThrowable(() -> MemberPoint.builder().memberId(7L).balance(null).build());
        Throwable negativeBalance = catchThrowable(() -> MemberPoint.builder().memberId(7L).balance(-1L).build());

        // then
        assertThat(nullMember).isInstanceOf(IllegalArgumentException.class);
        assertThat(nullBalance).isInstanceOf(IllegalArgumentException.class);
        assertThat(negativeBalance).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("잔액을 늘린다")
    void increase() {
        // given
        MemberPoint memberPoint = memberPoint(300L);

        // when
        memberPoint.increase(200L);

        // then
        assertThat(memberPoint.getBalance()).isEqualTo(500L);
    }

    @Test
    @DisplayName("잔액 이하로 줄일 수 있고 잔액 전부를 줄이면 0이 된다")
    void decrease() {
        // given
        MemberPoint memberPoint = memberPoint(300L);

        // when
        memberPoint.decrease(300L);

        // then
        assertThat(memberPoint.getBalance()).isZero();
    }

    @Test
    @DisplayName("잔액보다 많이 줄이면 잔액과 적립 건이 어긋난 것이라 내부 오류이고 잔액은 그대로다")
    void rejectDecreaseOverBalance() {
        // given
        MemberPoint memberPoint = memberPoint(300L);

        // when
        Throwable thrown = catchThrowable(() -> memberPoint.decrease(301L));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(memberPoint.getBalance()).isEqualTo(300L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("0 이하 금액으로는 늘리거나 줄일 수 없다")
    void rejectNonPositiveAmount(long amount) {
        // given
        MemberPoint memberPoint = memberPoint(300L);

        // when
        Throwable increase = catchThrowable(() -> memberPoint.increase(amount));
        Throwable decrease = catchThrowable(() -> memberPoint.decrease(amount));

        // then
        assertThat(increase).isInstanceOf(IllegalArgumentException.class);
        assertThat(decrease).isInstanceOf(IllegalArgumentException.class);
        assertThat(memberPoint.getBalance()).isEqualTo(300L);
    }

    private static MemberPoint memberPoint(long balance) {
        return MemberPoint.builder().memberId(7L).balance(balance).build();
    }
}
