package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentTest {

    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 23, 19, 8, 40);

    @Test
    @DisplayName("결제 대기 상태에서 결제 완료로 변경한다")
    void markPaid() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 20, 16, 55);

        // when
        payment.markPaid("tx_1", paidAt);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPortoneTransactionId()).isEqualTo("tx_1");
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("실패한 결제는 결제 완료 상태로 변경할 수 없다")
    void markPaidWhenFailed() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.FAILED)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 20, 16, 55);

        // when
        Throwable thrown = catchThrowable(() -> payment.markPaid("tx_1", paidAt));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
    }

    @Test
    @DisplayName("이미 결제 완료된 결제는 다시 결제 완료 상태로 변경할 수 없다")
    void markPaidWhenAlreadyPaid() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.PAID)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 20, 16, 55);

        // when
        Throwable thrown = catchThrowable(() -> payment.markPaid("tx_1", paidAt));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
    }

    @Test
    @DisplayName("결제 대기 상태에서 결제 실패로 변경한다")
    void fail() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        // when
        payment.fail(PaymentFailureReason.PG_FAILED, "tx_1");

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.PG_FAILED);
        assertThat(payment.getPortoneTransactionId()).isEqualTo("tx_1");
    }

    @Test
    @DisplayName("이미 결제 완료된 결제는 결제 실패 상태로 변경할 수 없다")
    void failWhenPaid() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.PAID)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        // when
        Throwable thrown = catchThrowable(() ->
            payment.fail(PaymentFailureReason.PG_FAILED, "tx_1"));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
    }

    @Test
    @DisplayName("결제 완료 상태에서 취소 요청 상태로 변경한다")
    void requestCancel() {
        // given
        Payment payment = paymentWith(PaymentStatus.PAID);

        // when
        payment.requestCancel();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(payment.getCanceledAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("결제 완료가 아닌 결제는 취소 요청 상태로 변경할 수 없다")
    void requestCancelWhenNotPaid(PaymentStatus status) {
        // given — 결제 대기는 PG 취소 없이 fail(CANCELED_BEFORE_PAY)로 끝내므로 여기서도 거부한다
        Payment payment = paymentWith(status);

        // when
        Throwable thrown = catchThrowable(payment::requestCancel);

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
        assertThat(payment.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("취소 요청 상태에서 PG가 확인한 취소 시각과 함께 취소 완료로 변경한다")
    void markCanceled() {
        // given
        Payment payment = paymentWith(PaymentStatus.CANCEL_REQUESTED);

        // when
        payment.markCanceled(CANCELED_AT);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
    }

    @Test
    @DisplayName("취소 시각 없이는 취소 완료로 변경할 수 없다 — 서버 시각으로 대신 채우지 않는다")
    void markCanceledWithoutCanceledAt() {
        // given
        Payment payment = paymentWith(PaymentStatus.CANCEL_REQUESTED);

        // when
        Throwable thrown = catchThrowable(() -> payment.markCanceled(null));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "CANCEL_REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("취소 요청 상태가 아닌 결제는 취소 완료로 변경할 수 없다")
    void markCanceledWhenNotCancelRequested(PaymentStatus status) {
        // given — 서버가 요청한 취소는 PAID에서 바로 CANCELED로 가지 않는다. 반드시 requestCancel을 거친다
        Payment payment = paymentWith(status);

        // when
        Throwable thrown = catchThrowable(() -> payment.markCanceled(CANCELED_AT));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
        assertThat(payment.getStatus()).isEqualTo(status);
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("취소 요청 상태에서 결제 완료로 되돌린다")
    void restorePaid() {
        // given
        Payment payment = paymentWith(PaymentStatus.CANCEL_REQUESTED);

        // when
        payment.restorePaid();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getCanceledAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "CANCEL_REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("취소 요청 상태가 아닌 결제는 결제 완료로 되돌릴 수 없다")
    void restorePaidWhenNotCancelRequested(PaymentStatus status) {
        // given — 이미 CANCELED(환불 완료)인 결제를 PAID로 되돌리면 돈은 나갔는데 주문이 살아난다
        Payment payment = paymentWith(status);

        // when
        Throwable thrown = catchThrowable(payment::restorePaid);

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
        assertThat(payment.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("결제 완료 상태에서 PG가 확인한 외부 취소 시각과 함께 바로 취소 완료로 변경한다")
    void markExternallyCanceled() {
        // given — 콘솔·관리자 화면 취소는 서버가 요청한 적이 없어 CANCEL_REQUESTED를 거치지 않는다
        Payment payment = paymentWith(PaymentStatus.PAID);

        // when
        payment.markExternallyCanceled(CANCELED_AT);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
    }

    @Test
    @DisplayName("외부 취소 시각 없이는 취소 완료로 변경할 수 없다 — 서버 시각으로 대신 채우지 않는다")
    void markExternallyCanceledWithoutCanceledAt() {
        // given
        Payment payment = paymentWith(PaymentStatus.PAID);

        // when
        Throwable thrown = catchThrowable(() -> payment.markExternallyCanceled(null));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getCanceledAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("결제 완료가 아닌 결제는 외부 취소로 취소 완료할 수 없다")
    void markExternallyCanceledWhenNotPaid(PaymentStatus status) {
        // given — 취소 진행 중인 결제는 기존 환불 경로로, 결제 전·실패 결제는 운영 확인으로 처리한다
        Payment payment = paymentWith(status);

        // when
        Throwable thrown = catchThrowable(() -> payment.markExternallyCanceled(CANCELED_AT));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
        assertThat(payment.getStatus()).isEqualTo(status);
        assertThat(payment.getCanceledAt()).isNull();
    }

    private Payment paymentWith(PaymentStatus status) {
        return Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(status)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();
    }
}
