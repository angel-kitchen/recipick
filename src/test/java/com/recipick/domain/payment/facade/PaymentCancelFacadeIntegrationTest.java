package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Mockito 단위 테스트는 "Processor가 예외 대신 값을 돌려준다"까지만 증명한다. "Spring이 TX2를 정말 커밋한 뒤 Facade 예외가
// DB를 되돌리지 않는다"는 실제 트랜잭션 경계와 DB가 있어야 보인다. PortOne HTTP 해석은 1단계 PortOnePaymentClientCancelTest가
// 맡았으므로 여기서는 클라이언트를 mock으로 바꾸고 응답 종류만 정한다
class PaymentCancelFacadeIntegrationTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9103L;
    private static final String KEY = "cancel-facade-it-key";
    private static final String REASON = "단순 변심";
    private static final String CANCELLATION_ID = "cancel_facade_it";
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 23, 19, 0);
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 23, 19, 8, 40);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 23, 19, 5);
    // 주문 생성 때 이미 차감된 뒤의 재고와 주문 수량. 환불 완료면 7, 실패·대기면 5 그대로다
    private static final int STOCK_AFTER_ORDER = 5;
    private static final int ORDER_QUANTITY = 2;

    @MockitoBean
    private PortOnePaymentClient portOnePaymentClient;

    @Autowired
    PaymentCancelFacade paymentCancelFacade;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    RefundRepository refundRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderItemRepository orderItemRepository;
    @Autowired
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    MartRepository martRepository;

    private Long martId;
    private Long productId;
    private Long orderId;
    private Long paymentId;
    private String portonePaymentId;

    @BeforeEach
    void setUp() {
        Mart mart = martRepository.save(Mart.builder()
            .regionId(31L)
            .name("결제 후 취소 통합 테스트 마트")
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build());
        Product product = productRepository.save(Product.builder()
            .mart(mart)
            .name("애호박")
            .totalQuantity(STOCK_AFTER_ORDER)
            .deleted(false)
            .build());
        // 회원이 취소할 수 있는 주문은 PAID + 마트 접수(RECEIVED) 상태다
        Order order = orderRepository.save(Order.builder()
            .memberId(MEMBER_ID)
            .martId(mart.getId())
            .status(OrderStatus.PAID)
            .martStatus(MartStatus.RECEIVED)
            .build());
        orderItemRepository.save(OrderItem.builder()
            .order(order)
            .productId(product.getId())
            .quantity(ORDER_QUANTITY)
            .build());
        martId = mart.getId();
        productId = product.getId();
        orderId = order.getId();
        portonePaymentId = "pay_cancel_facade_it_" + orderId;
        paymentId = paymentRepository.save(paidPayment(orderId, portonePaymentId)).getId();
    }

    @AfterEach
    void cleanUp() {
        if (paymentId != null) {
            refundRepository.deleteAll(refundRepository.findAllByPaymentIdIn(List.of(paymentId)));
            paymentRepository.deleteById(paymentId);
        }
        if (orderId != null) {
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findAllByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        if (productId != null) {
            productRepository.deleteById(productId);
        }
        if (martId != null) {
            martRepository.deleteById(martId);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "FAILED, PG_FAILED, PAYMENT-009",
        "REJECTED, REQUEST_REJECTED, COMMON-008"
    })
    @DisplayName("PortOne이 FAILED·REJECTED면 Facade는 예외를 던지지만 TX2가 먼저 커밋돼 결제·주문 PAID 복귀와 환불 FAILED가 DB에 남는다")
    void failedCancelKeepsRestoredStateAfterFacadeThrows(
        PortOneCancelOutcome portOneOutcome, RefundFailureType failureType, String expectedCode
    ) {
        // given — REJECTED는 처리 전 거절이라 취소 ID가 없다
        String cancellationId = portOneOutcome == PortOneCancelOutcome.FAILED ? CANCELLATION_ID : null;
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willReturn(new PortOneCancelResult(portOneOutcome, cancellationId, null, "detail"));

        // when
        Throwable thrown = catchThrowable(() -> paymentCancelFacade.cancel(MEMBER_ID, paymentId, KEY, REASON));

        // then — 예외는 났지만 TX2의 결과는 DB에 있다. Processor 안에서 던졌다면 전부 롤백돼 CANCEL_REQUESTED가 남았을 것이다
        assertErrorCode(thrown, expectedCode);
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getCanceledAt()).isNull();

        Refund refund = singleRefund();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(failureType);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(refund.getFailureReason()).isEqualTo("detail");
        assertThat(refund.getAmount()).isEqualTo(23800L);
        assertThat(refund.getIdempotencyKey()).isEqualTo(KEY);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCancelReasonCode()).isNull();
        assertThat(order.getCancelReasonDetail()).isNull();
        // 취소 시도는 이력에 남는다: PAID → CANCEL_REQUESTED, CANCEL_REQUESTED → PAID. from→to 쌍으로 보므로 읽는 순서와 무관하다
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId))
            .extracting(OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus)
            .containsExactlyInAnyOrder(
                tuple(OrderStatus.PAID.name(), OrderStatus.CANCEL_REQUESTED.name()),
                tuple(OrderStatus.CANCEL_REQUESTED.name(), OrderStatus.PAID.name()));
        assertThat(stock()).isEqualTo(STOCK_AFTER_ORDER);

        // 같은 키로 다시 오면 저장된 failureType으로 같은 오류를 재현하고 PortOne은 다시 부르지 않는다
        Throwable replayed = catchThrowable(() -> paymentCancelFacade.cancel(MEMBER_ID, paymentId, KEY, REASON));
        assertErrorCode(replayed, expectedCode);
        assertThat(refundRepository.findAllByPaymentIdIn(List.of(paymentId))).hasSize(1);
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("PortOne이 SUCCEEDED면 결제 CANCELED·환불 COMPLETED·주문 REFUNDED·재고 복구가 함께 커밋되고 같은 키 재요청은 재호출 없이 같은 결과다")
    void succeededCancelCommitsEverything() {
        // given
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willReturn(new PortOneCancelResult(
                PortOneCancelOutcome.SUCCEEDED, CANCELLATION_ID, CANCELED_AT, "cancellation SUCCEEDED"));

        // when
        PaymentCancelResult result = paymentCancelFacade.cancel(MEMBER_ID, paymentId, KEY, REASON);

        // then — 응답은 TX2 안의 값이다
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        assertThat(result.response().paymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.response().canceledAt()).isEqualTo(CANCELED_AT);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(result.response().refundAmount()).isEqualTo(23800L);

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);

        Refund refund = singleRefund();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.MEMBER);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isEqualTo(CANCELED_AT);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
        assertThat(order.getCancelReasonDetail()).isEqualTo(REASON);
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId))
            .extracting(OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus)
            .containsExactlyInAnyOrder(
                tuple(OrderStatus.PAID.name(), OrderStatus.CANCEL_REQUESTED.name()),
                tuple(OrderStatus.CANCEL_REQUESTED.name(), OrderStatus.REFUNDED.name()));
        assertThat(stock()).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);

        // PortOne에는 클라이언트 키가 아니라 TX1에서 만든 환불 행의 서버 키가 간다
        verify(portOnePaymentClient).cancelPayment(
            portonePaymentId, "recipick-refund-" + refund.getId(), REASON, 23800L);

        // 같은 키 재요청은 COMPLETED 환불을 그대로 답한다
        PaymentCancelResult replayed = paymentCancelFacade.cancel(MEMBER_ID, paymentId, KEY, REASON);
        assertThat(replayed.outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        assertThat(replayed.response().refundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refundRepository.findAllByPaymentIdIn(List.of(paymentId))).hasSize(1);
        assertThat(stock()).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("PortOne이 REQUESTED면 취소 ID만 남기고 CANCEL_REQUESTED·REQUESTED를 유지한 채 PENDING이고, 다른 키는 COMMON-006이다")
    void requestedCancelStaysPending() {
        // given
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willReturn(new PortOneCancelResult(
                PortOneCancelOutcome.REQUESTED, CANCELLATION_ID, null, "cancellation REQUESTED"));

        // when
        PaymentCancelResult result = paymentCancelFacade.cancel(MEMBER_ID, paymentId, KEY, REASON);

        // then — 202 응답에도 refundAmount는 전액이다
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.PENDING);
        assertThat(result.response().paymentStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(result.response().refundStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(result.response().refundAmount()).isEqualTo(23800L);

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        Refund refund = singleRefund();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(stock()).isEqualTo(STOCK_AFTER_ORDER);

        // 같은 키 재요청 — 취소 ID가 이미 있으니 PortOne을 다시 묻지 않고 PENDING이다 (웹훅·재조회 1-5 몫)
        PaymentCancelResult replayed = paymentCancelFacade.cancel(MEMBER_ID, paymentId, KEY, REASON);
        assertThat(replayed.outcome()).isEqualTo(PaymentCancelOutcome.PENDING);
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), anyString(), anyString(), anyLong());

        // 다른 키 — 취소가 진행 중이므로 새 환불을 만들지 않는다
        Throwable otherKey = catchThrowable(() ->
            paymentCancelFacade.cancel(MEMBER_ID, paymentId, "cancel-facade-it-other-key", REASON));
        assertErrorCode(otherKey, CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS.getCode());
        assertThat(refundRepository.findAllByPaymentIdIn(List.of(paymentId))).hasSize(1);
        verify(portOnePaymentClient, times(1)).cancelPayment(any(), any(), any(), any());
    }

    // 결제 전 취소 멱등성(5건 동시)은 PaymentCancelConcurrencyTest, PAID 결제의 동시 취소는 PaymentCancelAfterPayConcurrencyTest가 맡는다

    private Refund singleRefund() {
        List<Refund> refunds = refundRepository.findAllByPaymentIdIn(List.of(paymentId));
        assertThat(refunds).hasSize(1);
        return refunds.get(0);
    }

    private int stock() {
        return productRepository.findById(productId).orElseThrow().getTotalQuantity();
    }

    // 결제 완료 상태는 생성자로 못 만들고 실제 전이를 거쳐야 한다
    private static Payment paidPayment(Long orderId, String portonePaymentId) {
        Payment payment = Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(portonePaymentId)
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(EXPIRES_AT)
            .build();
        payment.markPaid("txn_cancel_facade_it", PAID_AT);
        return payment;
    }

    private static void assertErrorCode(Throwable thrown, String expectedCode) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode().getCode()).isEqualTo(expectedCode));
    }
}
