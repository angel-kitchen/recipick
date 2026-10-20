package com.recipick.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 20, 12, 0);

    private Order order(OrderStatus status, MartStatus martStatus) {
        return Order.builder()
                .memberId(1L)
                .pendingMemberId(1L)
                .status(status)
                .martStatus(martStatus)
                .build();
    }

    @Test
    @DisplayName("결제 대기 주문을 결제 완료로 바꾸면 마트 처리 상태는 접수중이 되고 결제 대기 표시가 풀린다")
    void markPaid() {
        // given
        Order order = order(OrderStatus.PENDING_PAYMENT, MartStatus.NONE);

        // when
        order.markPaid(PAID_AT);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        assertThat(order.getPendingMemberId()).isNull();
    }

    @Test
    @DisplayName("결제 시각 없이 결제 완료로 바꿀 수 없다")
    void markPaid_nullPaidAt() {
        // given
        Order order = order(OrderStatus.PENDING_PAYMENT, MartStatus.NONE);

        // when, then
        assertThatThrownBy(() -> order.markPaid(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("결제 대기가 아닌 주문은 결제 완료로 바꿀 수 없다")
    void markPaid_notPendingPayment(OrderStatus status) {
        // given
        Order order = order(status, MartStatus.NONE);

        // when, then
        assertThatThrownBy(() -> order.markPaid(PAID_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
    }

    @ParameterizedTest
    @EnumSource(value = MartStatus.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("마트 처리 상태가 시작 전이 아니면 결제 완료로 바꿀 수 없다")
    void markPaid_martStatusNotNone(MartStatus martStatus) {
        // given
        Order order = order(OrderStatus.PENDING_PAYMENT, martStatus);

        // when, then
        assertThatThrownBy(() -> order.markPaid(PAID_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_MART_STATUS_TRANSITION);
    }
}
