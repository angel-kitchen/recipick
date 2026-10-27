package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.product.dto.StockQuantity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// 잠금 아래 재검증과 상태 전이만 본다. PortOne 상태별 분기는 만료 Facade 테스트, 실제 잠금 경합은 통합 테스트가 맡는다
@ExtendWith(MockitoExtension.class)
class PaymentExpirationProcessorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW_INSTANT = Instant.parse("2026-10-27T13:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, SEOUL);
    private static final Long PAYMENT_ID = 10L;
    private static final Long ORDER_ID = 1L;
    private static final List<StockQuantity> RESTORE_ITEMS = List.of(new StockQuantity(100L, 2));

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderFacade orderFacade;

    private PaymentExpirationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PaymentExpirationProcessor(paymentService, orderFacade, Clock.fixed(NOW_INSTANT, SEOUL));
    }

    @Test
    @DisplayName("만료 시각이 지난 IN_PROGRESS 결제는 FAILED(EXPIRED)로 닫고 주문 종료 뒤 재고를 복구한다")
    void expireInProgressPayment() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderFacade.closeByPaymentFailure(ORDER_ID, CancelReasonCode.PAYMENT_TIMEOUT, "결제 대기 5분 만료"))
            .willReturn(RESTORE_ITEMS);

        // when
        PaymentExpirationOutcome outcome = processor.expire(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.EXPIRED);
        assertThat(payment.getPortoneTransactionId()).isNull();
        InOrder inOrder = inOrder(paymentService, orderFacade);
        inOrder.verify(paymentService).getByIdForUpdate(PAYMENT_ID);
        inOrder.verify(orderFacade).closeByPaymentFailure(ORDER_ID, CancelReasonCode.PAYMENT_TIMEOUT, "결제 대기 5분 만료");
        inOrder.verify(orderFacade).restoreStock(RESTORE_ITEMS);
    }

    @Test
    @DisplayName("현재 시각이 만료 시각과 같으면 만료로 본다")
    void expireAtExactExpiresAt() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS, NOW);
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderFacade.closeByPaymentFailure(ORDER_ID, CancelReasonCode.PAYMENT_TIMEOUT, "결제 대기 5분 만료"))
            .willReturn(RESTORE_ITEMS);

        // when
        PaymentExpirationOutcome outcome = processor.expire(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("주문이 이미 종료돼 복구할 품목이 없으면 빈 목록을 넘겨 재고를 다시 늘리지 않는다")
    void noStockRestoreWhenOrderAlreadyClosed() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS, NOW.minusMinutes(1));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);
        given(orderFacade.closeByPaymentFailure(ORDER_ID, CancelReasonCode.PAYMENT_TIMEOUT, "결제 대기 5분 만료"))
            .willReturn(List.of());

        // when
        PaymentExpirationOutcome outcome = processor.expire(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        verify(orderFacade).restoreStock(List.of());
    }

    @Test
    @DisplayName("아직 만료 시각 전이면 SKIPPED — 결제·주문·재고를 바꾸지 않는다")
    void skipBeforeExpiresAt() {
        // given
        Payment payment = payment(PaymentStatus.IN_PROGRESS, NOW.plusSeconds(1));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);

        // when
        PaymentExpirationOutcome outcome = processor.expire(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.SKIPPED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        verifyNoInteractions(orderFacade);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "IN_PROGRESS")
    @DisplayName("잠금 아래에서 이미 IN_PROGRESS가 아니면 SKIPPED — 확정·웹훅·결제 전 취소가 먼저 끝낸 결과를 바꾸지 않는다")
    void skipWhenNotInProgress(PaymentStatus status) {
        // given
        Payment payment = payment(status, NOW.minusMinutes(1));
        given(paymentService.getByIdForUpdate(PAYMENT_ID)).willReturn(payment);

        // when
        PaymentExpirationOutcome outcome = processor.expire(PAYMENT_ID);

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.SKIPPED);
        assertThat(payment.getStatus()).isEqualTo(status);
        verifyNoInteractions(orderFacade);
    }

    private Payment payment(PaymentStatus status, LocalDateTime expiresAt) {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(status)
            .expiresAt(expiresAt)
            .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }
}
