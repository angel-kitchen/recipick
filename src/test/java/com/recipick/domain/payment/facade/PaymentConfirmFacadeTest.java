package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.response.PaymentConfirmResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final String PORTONE_PAYMENT_ID = "pay_1";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 21, 10, 5);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 21, 10, 3);

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @Mock
    private PortOnePaymentClient portOnePaymentClient;

    @Mock
    private PaymentConfirmProcessor paymentConfirmProcessor;

    @Mock
    private SystemRefundExecutionFacade systemRefundExecutionFacade;

    @InjectMocks
    private PaymentConfirmFacade facade;

    @Test
    @DisplayName("검증 1~3 통과 → PortOne 조회 → APPROVED면 반영된 결제를 다시 읽어 PAID 응답을 만든다")
    void approved() {
        // given
        PortOnePayment portOne = paidPortOne();
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment(), paidPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne);
        given(paymentConfirmProcessor.process(ORDER_ID, portOne)).willReturn(PaymentApprovalOutcome.APPROVED);

        // when
        PaymentConfirmResponse response = facade.confirm(MEMBER_ID, ORDER_ID, PORTONE_PAYMENT_ID);

        // then
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.amount()).isEqualTo(23800L);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paidAt()).isEqualTo(PAID_AT);
        verify(paymentService, times(2)).getByOrderId(ORDER_ID);
        verify(systemRefundExecutionFacade).scheduleAfterConfirm(PAYMENT_ID, PaymentApprovalOutcome.APPROVED);
    }

    @Test
    @DisplayName("이미 PAID인 결제에 다시 요청하면 오류 없이 같은 200 응답을 돌려준다")
    void alreadyPaid() {
        // given
        PortOnePayment portOne = paidPortOne();
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(paidPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne);
        given(paymentConfirmProcessor.process(ORDER_ID, portOne)).willReturn(PaymentApprovalOutcome.ALREADY_PAID);

        // when
        PaymentConfirmResponse response = facade.confirm(MEMBER_ID, ORDER_ID, PORTONE_PAYMENT_ID);

        // then
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentService, times(2)).getByOrderId(ORDER_ID);
        verify(systemRefundExecutionFacade).scheduleAfterConfirm(PAYMENT_ID, PaymentApprovalOutcome.ALREADY_PAID);
    }

    @Test
    @DisplayName("결제가 없으면 PAYMENT_NOT_FOUND이고 주문·PortOne·Processor는 부르지 않는다")
    void paymentNotFound() {
        // given
        given(paymentService.getByOrderId(ORDER_ID))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // when
        Throwable thrown = catchThrowable(() -> facade.confirm(MEMBER_ID, ORDER_ID, PORTONE_PAYMENT_ID));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_FOUND);
        verifyNoInteractions(orderService, portOnePaymentClient, paymentConfirmProcessor, systemRefundExecutionFacade);
    }

    @Test
    @DisplayName("다른 회원의 주문이면 존재를 숨기고 PAYMENT_NOT_FOUND다. PortOne·Processor는 부르지 않는다")
    void otherMembersOrder() {
        // given
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(99L);

        // when
        Throwable thrown = catchThrowable(() -> facade.confirm(MEMBER_ID, ORDER_ID, PORTONE_PAYMENT_ID));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_FOUND);
        verifyNoInteractions(portOnePaymentClient, paymentConfirmProcessor, systemRefundExecutionFacade);
    }

    @Test
    @DisplayName("요청 결제번호가 결제의 portonePaymentId와 다르면 PAYMENT_NOT_FOUND다. PortOne·Processor는 부르지 않는다")
    void portonePaymentIdMismatch() {
        // given
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        Throwable thrown = catchThrowable(() -> facade.confirm(MEMBER_ID, ORDER_ID, "pay_other"));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_FOUND);
        verifyNoInteractions(portOnePaymentClient, paymentConfirmProcessor, systemRefundExecutionFacade);
    }

    @Test
    @DisplayName("0원 결제는 PortOne 결제가 없어 확정 대상이 아니므로 PortOne 결제 ID가 없어도 오류 없이 PAYMENT_NOT_FOUND다")
    void pointsOnlyPayment() {
        // given — 주문 생성 때 이미 PAID로 끝난 0원 결제에 확정 API가 잘못 들어왔다
        Payment pointsOnly = Payment.pointsOnly(ORDER_ID, PAID_AT);
        ReflectionTestUtils.setField(pointsOnly, "id", PAYMENT_ID);
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(pointsOnly);
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        Throwable thrown = catchThrowable(() -> facade.confirm(MEMBER_ID, ORDER_ID, PORTONE_PAYMENT_ID));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_NOT_FOUND);
        verifyNoInteractions(portOnePaymentClient, paymentConfirmProcessor, systemRefundExecutionFacade);
    }

    @Test
    @DisplayName("PortOne 조회가 실패하면 그 예외를 그대로 올리고 Processor도 재조회도 하지 않는다")
    void portOneLookupFails() {
        // given
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR));

        // when
        Throwable thrown = catchThrowable(() -> facade.confirm(MEMBER_ID, ORDER_ID, PORTONE_PAYMENT_ID));

        // then
        assertErrorCode(thrown, CommonErrorCode.EXTERNAL_SERVICE_ERROR);
        verify(paymentService, times(1)).getByOrderId(ORDER_ID);
        verifyNoInteractions(paymentConfirmProcessor, systemRefundExecutionFacade);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentApprovalOutcome.class, names = {
        "NOT_PAID", "PG_FAILED", "ORDER_MISMATCH", "AMOUNT_MISMATCH", "EXPIRED", "ALREADY_CLOSED", "INVALID_STATUS"})
    @DisplayName("오류 결과는 Processor 커밋 뒤 SYSTEM 환불 실행 예약 여부 판단을 맡긴 다음 그 결과의 오류 코드로 바꿔 던지고, 결제를 다시 읽지 않는다")
    void failedOutcomeBecomesError(PaymentApprovalOutcome outcome) {
        // given
        PortOnePayment portOne = paidPortOne();
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne);
        given(paymentConfirmProcessor.process(ORDER_ID, portOne)).willReturn(outcome);

        // when
        Throwable thrown = catchThrowable(() -> facade.confirm(MEMBER_ID, ORDER_ID, PORTONE_PAYMENT_ID));

        // then
        assertErrorCode(thrown, outcome.errorCode().orElseThrow());
        verify(systemRefundExecutionFacade).scheduleAfterConfirm(PAYMENT_ID, outcome);
        verify(paymentService, times(1)).getByOrderId(ORDER_ID);
    }

    @Test
    @DisplayName("Processor가 실패하면(락 대기 초과 등) 커밋된 판정이 없으므로 예약 여부를 판단하지 않고 예외를 그대로 올린다")
    void processorFailureSkipsSchedule() {
        // given
        PortOnePayment portOne = paidPortOne();
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(portOnePaymentClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(portOne);
        given(paymentConfirmProcessor.process(ORDER_ID, portOne))
            .willThrow(new CannotAcquireLockException("lock wait timeout"));

        // when
        Throwable thrown = catchThrowable(() -> facade.confirm(MEMBER_ID, ORDER_ID, PORTONE_PAYMENT_ID));

        // then
        assertThat(thrown).isInstanceOf(CannotAcquireLockException.class);
        verifyNoInteractions(systemRefundExecutionFacade);
    }

    private Payment inProgressPayment() {
        return payment(PaymentStatus.IN_PROGRESS);
    }

    private Payment paidPayment() {
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        payment.markPaid("tx_1", PAID_AT);
        return payment;
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(PORTONE_PAYMENT_ID)
            .amount(23800L)
            .status(status)
            .expiresAt(EXPIRES_AT)
            .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    private PortOnePayment paidPortOne() {
        return new PortOnePayment(PORTONE_PAYMENT_ID, "tx_1", PortOnePaymentStatus.PAID,
            23800L, "KRW", PAID_AT, ORDER_ID, null);
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
