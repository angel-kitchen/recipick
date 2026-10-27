package com.recipick.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @DisplayName("멱등성 키는 소문자로 바꿔 저장한다")
    void create_normalizesIdempotencyKey() {
        // given
        String idempotencyKey = "3F2A9C1E-7B4D-4E8A-9C0F-1A2B3C4D5E6F";

        // when
        Order order = Order.builder()
                .memberId(1L)
                .idempotencyKey(idempotencyKey)
                .build();

        // then
        assertThat(order.getIdempotencyKey()).isEqualTo("3f2a9c1e-7b4d-4e8a-9c0f-1a2b3c4d5e6f");
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

    @Test
    @DisplayName("결제 대기 주문을 취소하면 사유와 취소 시각이 남고 결제 대기 표시가 풀리며 마트 처리 상태는 그대로다")
    void cancel() {
        // given
        Order order = order(OrderStatus.PENDING_PAYMENT, MartStatus.NONE);

        // when
        order.cancel(CancelReasonCode.PAYMENT_TIMEOUT, "결제 시간 만료");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.PAYMENT_TIMEOUT);
        assertThat(order.getCancelReasonDetail()).isEqualTo("결제 시간 만료");
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(order.getPendingMemberId()).isNull();
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.NONE);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("결제 대기가 아닌 주문은 결제 실패로 취소할 수 없다")
    void cancel_notPendingPayment(OrderStatus status) {
        // given
        Order order = order(status, MartStatus.NONE);

        // when, then
        assertThatThrownBy(() -> order.cancel(CancelReasonCode.PAYMENT_FAILED, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
    }

    @ParameterizedTest
    @CsvSource({
            "PAID, RECEIVED, true",
            "PAID, PREPARING, false",
            "PAID, SHIPPING, false",
            "PAID, DELIVERED, false",
            "CANCEL_REQUESTED, RECEIVED, false",
            "PENDING_PAYMENT, NONE, false"
    })
    @DisplayName("결제 완료이면서 마트가 접수중일 때만 회원이 직접 취소할 수 있다")
    void isCancelableByMember(OrderStatus status, MartStatus martStatus, boolean expected) {
        // given
        Order order = order(status, martStatus);

        // when
        boolean cancelable = order.isCancelableByMember();

        // then
        assertThat(cancelable).isEqualTo(expected);
    }

    @Test
    @DisplayName("회원 취소를 요청하면 취소 요청 상태가 되고 회원 취소 사유와 상세가 남으며 마트 처리 상태는 그대로다")
    void requestMemberCancel() {
        // given
        Order order = order(OrderStatus.PAID, MartStatus.RECEIVED);

        // when
        order.requestMemberCancel("단순 변심");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
        assertThat(order.getCancelReasonDetail()).isEqualTo("단순 변심");
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        assertThat(order.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("마트가 준비를 시작한 주문은 회원 취소를 요청할 수 없다")
    void requestMemberCancel_martPreparing() {
        // given
        Order order = order(OrderStatus.PAID, MartStatus.PREPARING);

        // when, then
        assertThatThrownBy(() -> order.requestMemberCancel(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELABLE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @ParameterizedTest
    @CsvSource({
            "PAID, RECEIVED, true",
            "PAID, PREPARING, false",
            "PAID, SHIPPING, false",
            "PAID, DELIVERED, false",
            "CANCEL_REQUESTED, RECEIVED, false",
            "CANCEL_REQUESTED, REJECTED, false",
            "REFUNDED, RECEIVED, false",
            "PENDING_PAYMENT, NONE, false"
    })
    @DisplayName("결제 완료이면서 마트가 접수중일 때만 마트가 수락할 수 있다")
    void isAcceptableByMart(OrderStatus status, MartStatus martStatus, boolean expected) {
        // given
        Order order = order(status, martStatus);

        // when
        boolean acceptable = order.isAcceptableByMart();

        // then
        assertThat(acceptable).isEqualTo(expected);
    }

    @Test
    @DisplayName("마트가 수락하면 마트 처리 상태만 배송준비중이 되고 주문 상태는 결제 완료로 남는다")
    void acceptByMart() {
        // given
        Order order = order(OrderStatus.PAID, MartStatus.RECEIVED);

        // when
        order.acceptByMart();

        // then
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.PREPARING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCancelReasonCode()).isNull();
    }

    @Test
    @DisplayName("회원 취소가 진행 중인 주문은 수락할 수 없고 상태도 그대로다")
    void acceptByMart_cancelRequested() {
        // given
        Order order = order(OrderStatus.PAID, MartStatus.RECEIVED);
        order.requestMemberCancel("단순 변심");

        // when, then
        assertThatThrownBy(order::acceptByMart)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_ACCEPTABLE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
    }

    @Test
    @DisplayName("회원 취소가 실패해 결제 완료로 되돌리면 취소 사유와 상세가 비워진다")
    void restorePaidAfterMemberCancelFailure() {
        // given
        Order order = order(OrderStatus.PAID, MartStatus.RECEIVED);
        order.requestMemberCancel("단순 변심");

        // when
        order.restorePaidAfterMemberCancelFailure();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        assertThat(order.getCancelReasonCode()).isNull();
        assertThat(order.getCancelReasonDetail()).isNull();
    }

    @Test
    @DisplayName("회원 취소 요청이 아닌 취소 요청 주문은 결제 완료로 되돌릴 수 없다")
    void restorePaidAfterMemberCancelFailure_notMemberCanceled() {
        // given
        Order order = order(OrderStatus.CANCEL_REQUESTED, MartStatus.REJECTED);

        // when, then
        assertThatThrownBy(order::restorePaidAfterMemberCancelFailure)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
    }

    @Test
    @DisplayName("취소 요청 주문을 환불 완료로 바꾸면 취소 시각이 남고 취소 사유는 유지된다")
    void markRefunded() {
        // given
        Order order = order(OrderStatus.PAID, MartStatus.RECEIVED);
        order.requestMemberCancel(null);

        // when
        order.markRefunded();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
    }

    @Test
    @DisplayName("취소 사유가 없는 취소 요청 주문은 환불 완료로 바꿀 수 없다")
    void markRefunded_noCancelReason() {
        // given
        Order order = order(OrderStatus.CANCEL_REQUESTED, MartStatus.RECEIVED);

        // when, then
        assertThatThrownBy(order::markRefunded)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "CANCEL_REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("취소 요청이 아닌 주문은 환불 완료로 바꿀 수 없다")
    void markRefunded_notCancelRequested(OrderStatus status) {
        // given
        Order order = order(status, MartStatus.RECEIVED);

        // when, then
        assertThatThrownBy(order::markRefunded)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
    }

    @ParameterizedTest
    @EnumSource(value = MartStatus.class, names = {"RECEIVED", "PREPARING", "SHIPPING", "DELIVERED"})
    @DisplayName("결제 완료 주문을 외부 취소로 종료하면 취소 요청 없이 환불 완료가 되고 외부 취소 사유가 남으며 마트 처리 상태는 그대로다")
    void closeByExternalCancel(MartStatus martStatus) {
        // given
        Order order = order(OrderStatus.PAID, martStatus);

        // when
        order.closeByExternalCancel("PortOne 콘솔 전액 취소");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.EXTERNAL_CANCELED);
        assertThat(order.getCancelReasonDetail()).isEqualTo("PortOne 콘솔 전액 취소");
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(order.getMartStatus()).isEqualTo(martStatus);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("결제 완료가 아닌 주문은 외부 취소로 종료할 수 없다")
    void closeByExternalCancel_notPaid(OrderStatus status) {
        // given
        Order order = order(status, MartStatus.RECEIVED);

        // when, then
        assertThatThrownBy(() -> order.closeByExternalCancel(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("결제 대기 주문에 금액을 확정한다")
    void confirmAmounts() {
        // given
        Order order = order(OrderStatus.PENDING_PAYMENT, MartStatus.NONE);

        // when
        order.confirmAmounts(38000L, 3000L, 41000L);

        // then
        assertThat(order.getProductAmount()).isEqualTo(38000L);
        assertThat(order.getDeliveryFee()).isEqualTo(3000L);
        assertThat(order.getPayAmount()).isEqualTo(41000L);
    }

    @Test
    @DisplayName("이미 금액이 확정된 주문은 금액을 다시 바꿀 수 없다")
    void confirmAmounts_alreadyConfirmed() {
        // given
        Order order = order(OrderStatus.PENDING_PAYMENT, MartStatus.NONE);
        order.confirmAmounts(38000L, 3000L, 41000L);

        // when, then
        assertThatThrownBy(() -> order.confirmAmounts(1000L, 3000L, 4000L))
                .isInstanceOf(IllegalStateException.class);
    }
}
