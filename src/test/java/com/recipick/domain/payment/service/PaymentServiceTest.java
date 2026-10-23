package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.dto.RefundSummary;
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
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 7, 30);
    private static final Long PAYMENT_ID = 10L;
    private static final Long OTHER_PAYMENT_ID = 20L;

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
    @DisplayName("주문 생성 재시도용 조회는 결제 엔티티가 아니라 PaymentCreated로 돌려준다")
    void getCreatedByOrderId() {
        // given
        Payment payment = failedPayment();
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        // when
        PaymentCreated created = paymentService.getCreatedByOrderId(1L);

        // then
        assertThat(created.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(created.portonePaymentId()).isEqualTo("pay_" + PAYMENT_ID);
        assertThat(created.amount()).isEqualTo(23800L);
        assertThat(created.expiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 10, 5));
    }

    @Test
    @DisplayName("재시도용 조회도 없는 결제면 PAYMENT_NOT_FOUND다")
    void getCreatedByOrderIdNotFound() {
        // given
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> paymentService.getCreatedByOrderId(1L));

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
    @DisplayName("SYSTEM 환불이 없으면 PG 청구 금액·UUID 키·멈춘 시계의 시각으로 REQUESTED 행을 만든다")
    void requestSystemRefundCreatesFirstOne() {
        // given
        Payment payment = failedPayment();
        given(refundRepository.findSystemRefundByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
        given(refundRepository.save(any(Refund.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Refund refund = paymentService.requestSystemRefund(payment, 20000L, "결제 금액 불일치");

        // then
        verify(refundRepository).save(refundCaptor.capture());
        assertThat(refund).isSameAs(refundCaptor.getValue());
        assertThat(refund.getPayment()).isSameAs(payment);
        assertThat(refund.getAmount()).isEqualTo(20000L);
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
        Refund refund = paymentService.requestSystemRefund(payment, 20000L, "다른 문구여도 상관없다");

        // then
        assertThat(refund).isSameAs(existing);
        assertThat(refund.getAmount()).isEqualTo(23800L);
        assertThat(refund.getStatus()).isEqualTo(status);
        assertThat(refund.getIdempotencyKey()).isEqualTo("key_existing");
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    @DisplayName("내 결제 내역 조회는 받은 주문 ID와 Pageable을 그대로 Repository에 넘긴다")
    void findHistory() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        Page<Payment> expected = Page.empty(pageable);
        given(paymentRepository.findByOrderIdIn(List.of(1L, 2L), pageable)).willReturn(expected);

        // when
        Page<Payment> found = paymentService.findHistory(List.of(1L, 2L), pageable);

        // then
        assertThat(found).isSameAs(expected);
    }

    @Test
    @DisplayName("환불 행이 없으면 완료 금액 0, 최근 상태 null이다")
    void getRefundSummaryWithoutRefunds() {
        // given
        given(refundRepository.findAllByPaymentIdOrderByIdDesc(PAYMENT_ID)).willReturn(List.of());

        // when
        RefundSummary summary = paymentService.getRefundSummary(PAYMENT_ID);

        // then
        assertThat(summary).isEqualTo(RefundSummary.NONE);
        assertThat(summary.completedAmount()).isZero();
        assertThat(summary.latestStatus()).isNull();
    }

    @Test
    @DisplayName("완료 금액은 COMPLETED 행만 더하고, 최근 상태는 id가 가장 큰 행의 상태다")
    void getRefundSummaryWithMixedStatuses() {
        // given — SYSTEM 시도가 실패한 뒤 ADMIN 재처리가 완료된 결제
        Payment payment = failedPayment();
        given(refundRepository.findAllByPaymentIdOrderByIdDesc(PAYMENT_ID)).willReturn(List.of(
            refund(3L, payment, 20000L, RefundStatus.COMPLETED, RefundRequester.ADMIN),
            refund(1L, payment, 20000L, RefundStatus.FAILED, RefundRequester.SYSTEM)));

        // when
        RefundSummary summary = paymentService.getRefundSummary(PAYMENT_ID);

        // then — FAILED 행의 20000원은 더해지지 않는다
        assertThat(summary.completedAmount()).isEqualTo(20000L);
        assertThat(summary.latestStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("진행 중인 환불만 있으면 완료 금액 0, 최근 상태 REQUESTED다")
    void getRefundSummaryWithRequestedOnly() {
        // given
        Payment payment = failedPayment();
        given(refundRepository.findAllByPaymentIdOrderByIdDesc(PAYMENT_ID)).willReturn(List.of(
            refund(1L, payment, 23800L, RefundStatus.REQUESTED, RefundRequester.SYSTEM)));

        // when
        RefundSummary summary = paymentService.getRefundSummary(PAYMENT_ID);

        // then
        assertThat(summary.completedAmount()).isZero();
        assertThat(summary.latestStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("결제 ID 목록이 비어 있으면 환불을 조회하지 않고 빈 Map을 돌려준다")
    void getRefundSummariesWithEmptyIds() {
        // when
        Map<Long, RefundSummary> summaries = paymentService.getRefundSummaries(List.of());

        // then
        assertThat(summaries).isEmpty();
        verify(refundRepository, never()).findAllByPaymentIdIn(any());
    }

    @Test
    @DisplayName("여러 결제의 환불을 한 번에 읽어 결제별로 나눠 요약한다")
    void getRefundSummaries() {
        // given — 10번은 재시도 끝에 완료, 20번은 진행 중
        Payment payment = failedPayment();
        Payment otherPayment = failedPayment(OTHER_PAYMENT_ID);
        given(refundRepository.findAllByPaymentIdIn(List.of(PAYMENT_ID, OTHER_PAYMENT_ID))).willReturn(List.of(
            refund(1L, payment, 20000L, RefundStatus.FAILED, RefundRequester.SYSTEM),
            refund(3L, payment, 20000L, RefundStatus.COMPLETED, RefundRequester.ADMIN),
            refund(2L, otherPayment, 15000L, RefundStatus.REQUESTED, RefundRequester.MEMBER)));

        // when
        Map<Long, RefundSummary> summaries =
            paymentService.getRefundSummaries(List.of(PAYMENT_ID, OTHER_PAYMENT_ID));

        // then
        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(PAYMENT_ID))
            .isEqualTo(new RefundSummary(20000L, RefundStatus.COMPLETED));
        assertThat(summaries.get(OTHER_PAYMENT_ID))
            .isEqualTo(new RefundSummary(0L, RefundStatus.REQUESTED));
    }

    @Test
    @DisplayName("배치 조회 결과가 id 순서대로 오지 않아도 가장 큰 id의 상태를 최근 상태로 쓴다")
    void getRefundSummariesIgnoresRowOrder() {
        // given — 최신(id 5)이 목록 가운데에 섞여 있다. 첫 행에 기대면 FAILED가 나온다
        Payment payment = failedPayment();
        given(refundRepository.findAllByPaymentIdIn(List.of(PAYMENT_ID))).willReturn(List.of(
            refund(2L, payment, 20000L, RefundStatus.FAILED, RefundRequester.SYSTEM),
            refund(5L, payment, 20000L, RefundStatus.COMPLETED, RefundRequester.ADMIN),
            refund(1L, payment, 20000L, RefundStatus.FAILED, RefundRequester.SYSTEM)));

        // when
        Map<Long, RefundSummary> summaries = paymentService.getRefundSummaries(List.of(PAYMENT_ID));

        // then
        assertThat(summaries.get(PAYMENT_ID).latestStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(summaries.get(PAYMENT_ID).completedAmount()).isEqualTo(20000L);
    }

    private Payment failedPayment() {
        return failedPayment(PAYMENT_ID);
    }

    private Payment failedPayment(Long paymentId) {
        Payment payment = Payment.builder()
            .orderId(paymentId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_" + paymentId)
            .amount(23800L)
            .status(PaymentStatus.FAILED)
            .expiresAt(LocalDateTime.of(2026, 10, 21, 10, 5))
            .build();
        // DB를 거치지 않아 id가 비어 있다. JPA가 채웠을 값을 흉내 내서 orderId와 섞이는 실수를 잡는다
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    // summarize가 가장 큰 id를 최신으로 보므로 테스트 객체에도 id가 있어야 설계가 실제로 검증된다
    private Refund refund(Long refundId, Payment payment, long amount,
                          RefundStatus status, RefundRequester requestedBy) {
        Refund refund = Refund.builder()
            .payment(payment)
            .amount(amount)
            .reason("결제 금액 불일치")
            .status(status)
            .requestedBy(requestedBy)
            .idempotencyKey("key_" + refundId)
            .requestedAt(NOW.minusMinutes(5))
            .build();
        ReflectionTestUtils.setField(refund, "id", refundId);
        return refund;
    }
}
