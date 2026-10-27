package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SystemRefundProcessorTest {

    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long REFUND_ID = 77L;
    private static final Long AMOUNT = 23800L;
    private static final String CANCELLATION_ID = "cancel_1";
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 27, 9, 6, 0);
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 27, 9, 6, 40);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 27, 9, 5, 0);

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private SystemRefundProcessor processor;

    @Test
    @DisplayName("SUCCEEDED면 환불을 PG 취소 ID·취소 시각으로 완료하고, 결제는 FAILED 그대로 둔다")
    void succeeded() {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund refund = systemRefund(payment);
        stub(payment, refund);

        // when
        RefundStatus status =
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT));

        // then
        assertThat(status).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isEqualTo(CANCELED_AT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("SUCCEEDED인데 취소 시각이 없으면 완료로 단정하지 않고 취소 ID만 남겨 REQUESTED로 둔다")
    void succeededWithoutCanceledAt() {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund refund = systemRefund(payment);
        stub(payment, refund);

        // when
        RefundStatus status = processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, null));

        // then
        assertThat(status).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("REQUESTED면 PG 비동기 처리 중이라 취소 ID만 남기고 REQUESTED로 둔다")
    void requested() {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund refund = systemRefund(payment);
        stub(payment, refund);

        // when
        RefundStatus status = processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.REQUESTED, null));

        // then
        assertThat(status).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
    }

    @Test
    @DisplayName("PortOne이 FAILED를 명시하면 취소 ID와 함께 PG_FAILED로 실패 처리한다")
    void failed() {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund refund = systemRefund(payment);
        stub(payment, refund);

        // when
        RefundStatus status = processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.FAILED, null));

        // then
        assertThat(status).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.PG_FAILED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("요청 거절(400·401·403)이면 취소 ID 없이 REQUEST_REJECTED로 실패 처리한다")
    void rejected() {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund refund = systemRefund(payment);
        stub(payment, refund);
        PortOneCancelResult rejected = new PortOneCancelResult(
            PortOneCancelOutcome.REJECTED, null, null, "HTTP 401 UNAUTHORIZED");

        // when
        RefundStatus status = processor.apply(PAYMENT_ID, REFUND_ID, rejected);

        // then
        assertThat(status).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.REQUEST_REJECTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PortOneCancelOutcome.class, names = {"MISMATCH", "UNCONFIRMED"})
    @DisplayName("상태 불일치·결과 불명은 성공도 실패도 단정할 수 없어 REQUESTED를 그대로 둔다")
    void unconfirmed(PortOneCancelOutcome outcome) {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund refund = systemRefund(payment);
        stub(payment, refund);
        PortOneCancelResult unknown = new PortOneCancelResult(outcome, null, null, "timeout");

        // when
        RefundStatus status = processor.apply(PAYMENT_ID, REFUND_ID, unknown);

        // then
        assertThat(status).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        assertThat(refund.getFailureType()).isNull();
    }

    @Test
    @DisplayName("이미 완료된 환불은 다른 결과가 와도 다시 바꾸지 않고 COMPLETED를 돌려준다")
    void alreadyCompleted() {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund refund = systemRefund(payment);
        refund.complete("cancel_old", CANCELED_AT);
        stub(payment, refund);

        // when
        RefundStatus status = processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.FAILED, null));

        // then
        assertThat(status).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cancel_old");
        assertThat(refund.getFailureType()).isNull();
    }

    @Test
    @DisplayName("결제가 FAILED가 아니면 SYSTEM 환불 대상이 아니라 호출자 버그로 막는다")
    void rejectsPaymentNotFailed() {
        // given
        Payment payment = payment(PAYMENT_ID, PaymentStatus.PAID);
        Refund refund = systemRefund(payment);
        stub(payment, refund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("FAILED");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("회원 환불을 넘기면 SYSTEM 환불이 아니라 호출자 버그로 막는다")
    void rejectsMemberRefund() {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund refund = memberRefund(payment);
        stub(payment, refund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("SYSTEM 환불이 아닙니다");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("다른 결제의 환불을 넘기면 호출자 버그로 막는다")
    void rejectsOtherPaymentRefund() {
        // given
        Payment payment = failedPayment(PAYMENT_ID);
        Refund otherRefund = systemRefund(failedPayment(99L));
        stub(payment, otherRefund);

        // when
        Throwable thrown = catchThrowable(() ->
            processor.apply(PAYMENT_ID, REFUND_ID, result(PortOneCancelOutcome.SUCCEEDED, CANCELED_AT)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("SYSTEM 환불이 아닙니다");
        assertThat(otherRefund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    private void stub(Payment payment, Refund refund) {
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(paymentService.getRefundById(REFUND_ID)).willReturn(refund);
    }

    private Payment payment(Long paymentId, PaymentStatus status) {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(AMOUNT)
            .status(status)
            .expiresAt(EXPIRES_AT)
            .build();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    // fail()은 IN_PROGRESS에서만 허용되므로 실제 전이를 거쳐 만든다
    private Payment failedPayment(Long paymentId) {
        Payment payment = payment(paymentId, PaymentStatus.IN_PROGRESS);
        payment.fail(PaymentFailureReason.EXPIRED, "tx_1");
        return payment;
    }

    private Refund systemRefund(Payment payment) {
        Refund refund = Refund.requestedBySystem(payment, AMOUNT, "결제 대기 5분 만료", "system-key-1", REQUESTED_AT);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    // requestedByMember는 CANCEL_REQUESTED 결제만 받으므로 잠시 바꿔서 만든다
    private Refund memberRefund(Payment payment) {
        PaymentStatus original = payment.getStatus();
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.CANCEL_REQUESTED);
        Refund refund = Refund.requestedByMember(payment, "단순 변심", "client-key-1", REQUESTED_AT);
        ReflectionTestUtils.setField(payment, "status", original);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    private static PortOneCancelResult result(PortOneCancelOutcome outcome, LocalDateTime canceledAt) {
        return new PortOneCancelResult(outcome, CANCELLATION_ID, canceledAt, "cancellation " + outcome);
    }
}
