package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCancelProcessorTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final String REASON = "단순 변심";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 21, 10, 5);
    private static final List<StockQuantity> RESTORE_ITEMS = List.of(new StockQuantity(100L, 2));

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderFacade orderFacade;

    @InjectMocks
    private PaymentCancelProcessor processor;

    @Test
    @DisplayName("본인의 IN_PROGRESS 결제면 FAILED(CANCELED_BEFORE_PAY)로 바꾸고 주문 종료 → 재고 복구 순으로 부른다")
    void cancelInProgress() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(orderFacade.closeByPaymentFailure(ORDER_ID, CancelReasonCode.MEMBER_CANCELED, REASON))
            .willReturn(RESTORE_ITEMS);

        // when
        PaymentCancelResponse response = processor.cancelBeforePay(MEMBER_ID, PAYMENT_ID, REASON);

        // then — PG를 거치지 않았으니 거래번호·취소 완료 시각은 비어 있고 환불 정보는 NONE이다
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.CANCELED_BEFORE_PAY);
        assertThat(payment.getPortoneTransactionId()).isNull();
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo(PaymentFailureReason.CANCELED_BEFORE_PAY);
        assertThat(response.canceledAt()).isNull();
        assertThat(response.refundStatus()).isNull();
        assertThat(response.refundAmount()).isZero();
        InOrder inOrder = inOrder(orderFacade);
        inOrder.verify(orderFacade).closeByPaymentFailure(ORDER_ID, CancelReasonCode.MEMBER_CANCELED, REASON);
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
        verifyNoMoreInteractions(orderFacade);
    }

    @Test
    @DisplayName("다른 회원의 결제면 존재를 숨기고 PAYMENT_NOT_FOUND다. 상태도 바꾸지 않고 주문도 부르지 않는다")
    void otherMembersPayment() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(99L);

        // when
        Throwable thrown = catchThrowable(() -> processor.cancelBeforePay(MEMBER_ID, PAYMENT_ID, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_FOUND);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        verifyNoInteractions(orderFacade);
    }

    @Test
    @DisplayName("이미 결제 전 취소된 결제라도 남의 것이면 멱등 200이 아니라 PAYMENT_NOT_FOUND다")
    void otherMembersCanceledPayment() {
        // given — 소유자 확인이 멱등 판정보다 앞이어야 남의 결제가 취소됐는지를 응답 차이로 알 수 없다
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(failedPayment(PaymentFailureReason.CANCELED_BEFORE_PAY));
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(99L);

        // when
        Throwable thrown = catchThrowable(() -> processor.cancelBeforePay(MEMBER_ID, PAYMENT_ID, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_FOUND);
        verifyNoInteractions(orderFacade);
    }

    @Test
    @DisplayName("이미 FAILED(CANCELED_BEFORE_PAY)면 아무것도 바꾸지 않고 같은 응답을 돌려준다")
    void alreadyCanceledBeforePay() {
        // given
        Payment payment = failedPayment(PaymentFailureReason.CANCELED_BEFORE_PAY);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        PaymentCancelResponse response = processor.cancelBeforePay(MEMBER_ID, PAYMENT_ID, REASON);

        // then — 두 번째 요청이 재고를 또 올리면 안 된다
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo(PaymentFailureReason.CANCELED_BEFORE_PAY);
        assertThat(response.refundAmount()).isZero();
        verifyNoInteractions(orderFacade);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PAID", "CANCEL_REQUESTED", "CANCELED"})
    @DisplayName("PAID·취소 진행·취소 완료 결제는 결제 전 취소 대상이 아니라 INVALID_PAYMENT_STATUS다")
    void notInProgress(PaymentStatus status) {
        // given
        Payment payment = payment(status);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        Throwable thrown = catchThrowable(() -> processor.cancelBeforePay(MEMBER_ID, PAYMENT_ID, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.INVALID_PAYMENT_STATUS);
        assertThat(payment.getStatus()).isEqualTo(status);
        verifyNoInteractions(orderFacade);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentFailureReason.class, names = {"EXPIRED", "PG_FAILED", "AMOUNT_MISMATCH"})
    @DisplayName("다른 사유로 실패한 결제는 취소된 게 아니므로 멱등 200이 아니라 INVALID_PAYMENT_STATUS다")
    void failedForOtherReason(PaymentFailureReason reason) {
        // given
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(failedPayment(reason));
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        Throwable thrown = catchThrowable(() -> processor.cancelBeforePay(MEMBER_ID, PAYMENT_ID, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.INVALID_PAYMENT_STATUS);
        verifyNoInteractions(orderFacade);
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(status)
            .expiresAt(EXPIRES_AT)
            .build();
        // 응답의 paymentId가 orderId와 섞이지 않는지 보려면 id가 있어야 한다
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    // fail()은 IN_PROGRESS에서만 허용되므로 실제 전이를 거쳐 만든다
    private Payment failedPayment(PaymentFailureReason reason) {
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        payment.fail(reason, null);
        return payment;
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
