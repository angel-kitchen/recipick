package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.response.PaymentDetailResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentQueryFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 22, 10, 5);

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentQueryFacade paymentQueryFacade;

    @Test
    @DisplayName("본인 주문이면 결제 정보를 돌려준다")
    void getByOrderId() {
        // given
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        PaymentDetailResponse response = paymentQueryFacade.getByOrderId(MEMBER_ID, ORDER_ID);

        // then
        assertThat(response.paymentId()).isEqualTo(10L);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.portonePaymentId()).isEqualTo("pay_1");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.amount()).isEqualTo(23800L);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(response.paidAt()).isNull();
        assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("다른 회원의 주문이면 PAYMENT-001이다")
    void getByOrderIdWithOtherMember() {
        // given
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID + 1);

        // when
        Throwable thrown = catchThrowable(() -> paymentQueryFacade.getByOrderId(MEMBER_ID, ORDER_ID));

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("결제가 없으면 PAYMENT-001이고 주문은 조회하지 않는다")
    void getByOrderIdWithoutPayment() {
        // given
        given(paymentService.getByOrderId(ORDER_ID))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // when
        Throwable thrown = catchThrowable(() -> paymentQueryFacade.getByOrderId(MEMBER_ID, ORDER_ID));

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
        verify(orderService, never()).getMemberIdOf(anyLong());
    }

    private Payment inProgressPayment() {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(EXPIRES_AT)
            .build();
        ReflectionTestUtils.setField(payment, "id", 10L);
        return payment;
    }
}
