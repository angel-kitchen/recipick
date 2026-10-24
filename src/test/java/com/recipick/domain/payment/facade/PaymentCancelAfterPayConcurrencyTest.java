package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// PAID 결제의 동시 취소. 스레드들이 각자 커밋하므로 테스트 트랜잭션 롤백을 쓸 수 없다 — 직접 정리한다.
// PortOne은 mock이다: HTTP 해석은 1단계가, 트랜잭션 경계는 PaymentCancelFacadeIntegrationTest가 맡았고 여기서는 잠금·UNIQUE만 본다
class PaymentCancelAfterPayConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 5;
    private static final Long MEMBER_ID = 9104L;
    private static final String REASON = "동시 취소";
    private static final String SHARED_KEY = "cancel-after-pay-shared-key";
    private static final String CANCELLATION_ID = "cancel_after_pay_cc";
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 23, 19, 0);
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 23, 19, 8, 40);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 23, 19, 5);
    private static final int STOCK_AFTER_ORDER = 5;
    private static final int ORDER_QUANTITY = 2;
    private static final PortOneCancelResult SUCCEEDED = new PortOneCancelResult(
        PortOneCancelOutcome.SUCCEEDED, CANCELLATION_ID, CANCELED_AT, "cancellation SUCCEEDED");

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
    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        refundRepository.deleteAll(refundRepository.findAllByPaymentIdIn(paymentIds));
        paymentRepository.deleteAllById(paymentIds);
        for (Long orderId : orderIds) {
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findAllByOrderId(orderId));
        }
        orderRepository.deleteAllById(orderIds);
        productRepository.deleteAllById(productIds);
        if (martId != null) {
            martRepository.deleteById(martId);
        }
    }

    @Test
    @DisplayName("같은 PAID 결제에 다른 키로 취소가 5건 동시에 오면 환불 행은 하나, PortOne 호출도 한 번이고 나머지는 COMMON-006이다")
    void differentKeysOnSamePaidPayment() throws Exception {
        // given — 먼저 잠금을 잡은 요청이 PortOne을 기다리는 동안 나머지 넷이 TX1에 들어오도록 PortOne 응답을 붙잡아 둔다.
        // 그래야 넷이 CANCELED(PAYMENT-007)가 아니라 CANCEL_REQUESTED(COMMON-006)를 확정적으로 본다
        givenPaidOrder();
        Long paymentId = paymentIds.get(0);
        CountDownLatch othersRejected = new CountDownLatch(THREADS - 1);
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willAnswer(invocation -> {
                // 시간 안에 넷이 못 오면 결과가 006/007로 갈려 원인이 흐려진다. 무엇을 기다리다 실패했는지 바로 드러낸다
                if (!othersRejected.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("다른 네 요청이 CANCEL_REQUESTED를 보고 거절되기 전에 시간이 지났습니다.");
                }
                return SUCCEEDED;
            });

        // when
        List<String> results = runConcurrently(THREADS, index -> attempt(
            () -> paymentCancelFacade.cancel(MEMBER_ID, paymentId, "cancel-after-pay-key-" + index, REASON),
            othersRejected));

        // then — 하나만 끝까지 가고 넷은 "같은 요청 처리 중"이다
        assertThat(results).filteredOn(PaymentCancelOutcome.CANCELED.name()::equals).hasSize(1);
        assertThat(results).filteredOn(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS.getCode()::equals)
            .hasSize(THREADS - 1);
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), anyString(), anyString(), anyLong());

        List<Refund> refunds = refundRepository.findAllByPaymentIdIn(List.of(paymentId));
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderRepository.findById(orderIds.get(0)).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.REFUNDED);
        // 재고는 한 번만 돌아온다
        assertThat(stock(productIds.get(0))).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        // 주문 이력도 한 번의 취소만 남는다: PAID → CANCEL_REQUESTED → REFUNDED
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderIds.get(0))).hasSize(2);
    }

    @Test
    @DisplayName("같은 PAID 결제에 같은 키로 동시에 오면 PortOne은 같은 서버 키로 두 번 불릴 수 있지만 환불 행·취소·재고 복구는 한 번이다")
    void sameKeyOnSamePaidPayment() throws Exception {
        // given — 먼저 간 요청이 PortOne에서 기다리는 동안 뒤 요청이 TX1에서 "REQUESTED + 취소 ID 없음 + 3시간 안"을 보고
        // 같은 서버 키로 재호출하게 만든다. 둘 다 PortOne에 닿은 뒤에야 응답을 돌려준다 — 설계한 멱등 재시도 정책 그대로다
        givenPaidOrder();
        Long paymentId = paymentIds.get(0);
        CountDownLatch bothCalledPortOne = new CountDownLatch(2);
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willAnswer(invocation -> {
                bothCalledPortOne.countDown();
                if (!bothCalledPortOne.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("같은 키의 두 요청이 모두 PortOne 호출까지 도달하지 못했습니다.");
                }
                return SUCCEEDED;
            });

        // when
        List<String> results = runConcurrently(2, index -> attempt(
            () -> paymentCancelFacade.cancel(MEMBER_ID, paymentId, SHARED_KEY, REASON), null));

        // then — 둘 다 CANCELED. 먼저 끝난 TX2가 완료시키고, 뒤 TX2는 COMPLETED 환불을 보고 그 결과를 그대로 돌려준다
        assertThat(results).hasSize(2).containsOnly(PaymentCancelOutcome.CANCELED.name());

        List<Refund> refunds = refundRepository.findAllByPaymentIdIn(List.of(paymentId));
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refunds.get(0).getIdempotencyKey()).isEqualTo(SHARED_KEY);
        // 우리 쪽 호출은 2회지만 서버 키가 같으므로 PortOne 쪽 취소 작업은 하나다
        ArgumentCaptor<String> serverKeys = ArgumentCaptor.forClass(String.class);
        verify(portOnePaymentClient, times(2))
            .cancelPayment(anyString(), serverKeys.capture(), eq(REASON), eq(23800L));
        assertThat(serverKeys.getAllValues()).containsOnly("recipick-refund-" + refunds.get(0).getId());

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderRepository.findById(orderIds.get(0)).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.REFUNDED);
        assertThat(stock(productIds.get(0))).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderIds.get(0))).hasSize(2);
    }

    @Test
    @DisplayName("두 PAID 결제에 같은 키로 동시에 오면 하나만 성공하고 다른 하나는 COMMON-011이며 진 쪽의 결제·주문은 PAID 그대로다")
    void sameKeyOnTwoPaidPayments() throws Exception {
        // given — idempotency_key는 REFUND 전체에서 UNIQUE라 다른 결제라도 같은 키의 환불 행은 하나만 생긴다
        givenPaidOrder();
        givenPaidOrder();
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willReturn(SUCCEEDED);

        // when
        List<String> results = runConcurrently(2, index -> attempt(
            () -> paymentCancelFacade.cancel(MEMBER_ID, paymentIds.get(index), SHARED_KEY, REASON), null));

        // then
        assertThat(results).filteredOn(PaymentCancelOutcome.CANCELED.name()::equals).hasSize(1);
        assertThat(results).filteredOn(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH.getCode()::equals)
            .hasSize(1);
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), anyString(), anyString(), anyLong());

        // 환불 행은 전체에서 하나, 그 키로 저장됐다
        List<Refund> refunds = refundRepository.findAllByPaymentIdIn(paymentIds);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getIdempotencyKey()).isEqualTo(SHARED_KEY);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);

        // 이긴 쪽은 끝까지 갔고, 진 쪽은 TX1이 통째로 롤백돼 아무 흔적이 없다 — 주문 CANCEL_REQUESTED 전이·이력도 남지 않는다
        boolean firstWon = !refundRepository.findAllByPaymentIdIn(List.of(paymentIds.get(0))).isEmpty();
        int winner = firstWon ? 0 : 1;
        int loser = firstWon ? 1 : 0;
        assertThat(paymentRepository.findById(paymentIds.get(winner)).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderRepository.findById(orderIds.get(winner)).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.REFUNDED);
        assertThat(stock(productIds.get(winner))).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);

        Payment loserPayment = paymentRepository.findById(paymentIds.get(loser)).orElseThrow();
        assertThat(loserPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        Order loserOrder = orderRepository.findById(orderIds.get(loser)).orElseThrow();
        assertThat(loserOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(loserOrder.getCancelReasonCode()).isNull();
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderIds.get(loser))).isEmpty();
        assertThat(stock(productIds.get(loser))).isEqualTo(STOCK_AFTER_ORDER);
    }

    // 마트 하나에 주문·상품·결제를 한 벌 만든다. 두 번 부르면 같은 회원의 PAID 주문이 둘이 된다
    private void givenPaidOrder() {
        if (martId == null) {
            martId = martRepository.save(Mart.builder()
                .regionId(31L)
                .name("결제 후 취소 동시성 테스트 마트")
                .latitude(new BigDecimal("37.3670000"))
                .longitude(new BigDecimal("127.1080000"))
                .serviceRadiusM(2000)
                .build()).getId();
        }
        Mart mart = martRepository.findById(martId).orElseThrow();
        Product product = productRepository.save(Product.builder()
            .mart(mart)
            .name("애호박")
            .totalQuantity(STOCK_AFTER_ORDER)
            .deleted(false)
            .build());
        Order order = orderRepository.save(Order.builder()
            .memberId(MEMBER_ID)
            .martId(martId)
            .status(OrderStatus.PAID)
            .martStatus(MartStatus.RECEIVED)
            .build());
        orderItemRepository.save(OrderItem.builder()
            .order(order)
            .productId(product.getId())
            .quantity(ORDER_QUANTITY)
            .build());
        Payment payment = Payment.builder()
            .orderId(order.getId())
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_cancel_after_pay_cc_" + order.getId())
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(EXPIRES_AT)
            .build();
        payment.markPaid("txn_cancel_after_pay_cc_" + order.getId(), PAID_AT);
        productIds.add(product.getId());
        orderIds.add(order.getId());
        paymentIds.add(paymentRepository.save(payment).getId());
    }

    // 성공이면 outcome 이름, 업무 예외면 오류 코드를 돌려준다. 예외를 셀 때 latch가 있으면 하나 내린다
    private static String attempt(Supplier<PaymentCancelResult> call, CountDownLatch onRejected) {
        try {
            return call.get().outcome().name();
        } catch (BusinessException e) {
            if (onRejected != null) {
                onRejected.countDown();
            }
            return e.getErrorCode().getCode();
        }
    }

    // 모든 스레드가 준비된 뒤 한 번에 출발시켜 실제로 같은 행을 두고 경쟁하게 만든다.
    // 어느 Future가 실패해도 executor를 반드시 정리한다 — 안 하면 @AfterEach의 DB 삭제와 남은 worker가 겹쳐 엉뚱한 오류가 덧붙는다
    private static List<String> runConcurrently(int threads, IntFunction<String> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        Throwable failure = null;
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.apply(index);
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("동시성 테스트 스레드가 모두 준비되지 못했습니다.");
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            // 준비 단계에서 실패해도 start.await()에 묶인 worker를 풀어 준다
            start.countDown();
            executor.shutdownNow();
            // finally에서 던지면 원래 실패 원인을 덮어쓴다. 원인이 있으면 거기에 덧붙인다 — 대기 중 인터럽트도 마찬가지다
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    AssertionError stuck = new AssertionError("동시성 테스트 스레드가 종료되지 않았습니다.");
                    if (failure == null) {
                        throw stuck;
                    }
                    failure.addSuppressed(stuck);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (failure == null) {
                    throw interrupted;
                }
                failure.addSuppressed(interrupted);
            }
        }
    }

    private int stock(Long productId) {
        return productRepository.findById(productId).orElseThrow().getTotalQuantity();
    }
}
