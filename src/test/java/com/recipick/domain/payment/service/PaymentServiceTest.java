package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 7, 30);
    private static final Long PAYMENT_ID = 10L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Captor
    private ArgumentCaptor<Refund> refundCaptor;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Clock은 mock이 아니라 @InjectMocks로는 못 넣는다. 시계를 10:07:30에 멈춰 두고 직접 조립한다
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        paymentService = new PaymentService(paymentRepository, refundRepository, fixedClock);
    }

    @Test
    @DisplayName("orderId로 결제를 돌려준다")
    void getByOrderId() {
        // given
        Payment payment = failedPayment();
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        // when
        Payment found = paymentService.getByOrderId(1L);

        // then
        assertThat(found).isSameAs(payment);
    }

    @Test
    @DisplayName("없는 결제는 PAYMENT_NOT_FOUND다")
    void getByOrderIdNotFound() {
        // given
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> paymentService.getByOrderId(1L));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("잠금 조회는 findByOrderIdForUpdate로 결제를 돌려준다")
    void getByOrderIdForUpdate() {
        // given
        Payment payment = failedPayment();
        given(paymentRepository.findByOrderIdForUpdate(1L)).willReturn(Optional.of(payment));

        // when
        Payment found = paymentService.getByOrderIdForUpdate(1L);

        // then
        assertThat(found).isSameAs(payment);
        verify(paymentRepository).findByOrderIdForUpdate(1L);
    }

    @Test
    @DisplayName("잠금 조회도 없는 결제면 PAYMENT_NOT_FOUND다")
    void getByOrderIdForUpdateNotFound() {
        // given
        given(paymentRepository.findByOrderIdForUpdate(1L)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> paymentService.getByOrderIdForUpdate(1L));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("SYSTEM 환불이 없으면 전액·UUID 키·멈춘 시계의 시각으로 REQUESTED 행을 만든다")
    void requestSystemRefundCreatesFirstOne() {
        // given
        Payment payment = failedPayment();
        given(refundRepository.findSystemRefundByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
        given(refundRepository.save(any(Refund.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Refund refund = paymentService.requestSystemRefund(payment, "결제 금액 불일치");

        // then
        verify(refundRepository).save(refundCaptor.capture());
        assertThat(refund).isSameAs(refundCaptor.getValue());
        assertThat(refund.getPayment()).isSameAs(payment);
        assertThat(refund.getAmount()).isEqualTo(23800L);
        assertThat(refund.getReason()).isEqualTo("결제 금액 불일치");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(refund.getRequestedAt()).isEqualTo(NOW);
        assertThatCode(() -> UUID.fromString(refund.getIdempotencyKey())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = RefundStatus.class, names = {"REQUESTED", "COMPLETED", "FAILED"})
    @DisplayName("SYSTEM 환불이 이미 있으면 상태·사유 문구와 관계없이 새 행·새 키를 만들지 않는다")
    void requestSystemRefundReturnsExisting(RefundStatus status) {
        // given
        Payment payment = failedPayment();
        Refund existing = Refund.builder()
            .payment(payment)
            .amount(23800L)
            .reason("결제 금액 불일치")
            .status(status)
            .requestedBy(RefundRequester.SYSTEM)
            .idempotencyKey("key_existing")
            .requestedAt(NOW.minusMinutes(5))
            .build();
        given(refundRepository.findSystemRefundByPaymentId(PAYMENT_ID)).willReturn(Optional.of(existing));

        // when
        Refund refund = paymentService.requestSystemRefund(payment, "다른 문구여도 상관없다");

        // then
        assertThat(refund).isSameAs(existing);
        assertThat(refund.getStatus()).isEqualTo(status);
        assertThat(refund.getIdempotencyKey()).isEqualTo("key_existing");
        verify(refundRepository, never()).save(any(Refund.class));
    }

    private Payment failedPayment() {
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.FAILED)
            .expiresAt(LocalDateTime.of(2026, 10, 21, 10, 5))
            .build();
        // DB를 거치지 않아 id가 비어 있다. JPA가 채웠을 값을 흉내 내서 orderId와 섞이는 실수를 잡는다
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }
}
