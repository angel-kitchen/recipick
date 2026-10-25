package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.dto.PaymentCancelPreparation;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 스레드들이 각자 커밋하므로 테스트 트랜잭션 롤백을 쓸 수 없다. OrderPaymentFailureConcurrencyTest(준희)처럼 직접 정리한다
class PaymentCancelConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 5;
    private static final Long MEMBER_ID = 9102L;
    private static final String REASON = "동시 취소";
    // 결제 전 취소는 키를 저장하지 않으므로 다섯 요청이 같은 키여도 서로를 "같은 키 재요청"으로 보지 않는다
    private static final String KEY = "cancel-concurrency-key";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 21, 10, 5);
    // 주문 생성 때 이미 차감된 뒤의 재고와 주문 수량. 복구가 한 번이면 7, 두 번 이상이면 9 이상이 된다
    private static final int STOCK_AFTER_ORDER = 5;
    private static final int ORDER_QUANTITY = 2;

    @Autowired
    PaymentCancelProcessor paymentCancelProcessor;
    @Autowired
    PaymentRepository paymentRepository;
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
    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long martId;
    private Long productId;
    private Long orderId;
    private Long paymentId;

    @AfterEach
    void cleanUp() {
        if (paymentId != null) {
            jdbcTemplate.update("DELETE FROM refund WHERE payment_id = ?", paymentId);
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

    @Test
    @DisplayName("같은 결제에 취소가 5건 동시에 와도 재고는 주문 수량만큼 한 번만 복구되고 나머지는 멱등 응답으로 끝난다")
    void concurrentCancels() throws Exception {
        // given
        Mart mart = martRepository.save(Mart.builder()
            .regionId(31L)
            .name("결제 취소 동시성 테스트 마트")
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
        Order order = orderRepository.save(Order.builder()
            .memberId(MEMBER_ID)
            .martId(mart.getId())
            .pendingMemberId(MEMBER_ID)
            .status(OrderStatus.PENDING_PAYMENT)
            .martStatus(MartStatus.NONE)
            .build());
        orderItemRepository.save(OrderItem.builder()
            .order(order)
            .productId(product.getId())
            .quantity(ORDER_QUANTITY)
            .build());
        martId = mart.getId();
        productId = product.getId();
        orderId = order.getId();
        paymentId = paymentRepository.save(inProgressPayment(orderId)).getId();

        // when
        List<PaymentCancelPreparation> preparations =
            runConcurrently(() -> paymentCancelProcessor.prepare(MEMBER_ID, paymentId, KEY, REASON));

        // then — 다섯 요청 모두 예외 없이 TX1에서 같은 종료 결과를 받고 PortOne은 부르지 않는다
        assertThat(preparations).hasSize(THREADS);
        assertThat(preparations).allSatisfy(preparation -> {
            assertThat(preparation.requiresPortOneCall()).isFalse();
            assertThat(preparation.finishedResult().outcome()).isEqualTo(PaymentCancelOutcome.CANCELED_BEFORE_PAY);
            assertThat(preparation.finishedResult().response().refundAmount()).isZero();
        });

        // 재고가 정확히 주문 수량만큼 한 번만 돌아왔다
        assertThat(productRepository.findById(productId).orElseThrow().getTotalQuantity())
            .isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.CANCELED_BEFORE_PAY);
        assertThat(payment.getCanceledAt()).isNull();

        Order canceledOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceledOrder.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
        assertThat(canceledOrder.getCancelReasonDetail()).isEqualTo(REASON);
        assertThat(canceledOrder.getPendingMemberId()).isNull();
        // 주문 종료 상태 전이도 한 번뿐이다
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).hasSize(1);
        // 결제 전 취소는 REFUND를 만들지 않는다
        assertThat(refundCount()).isZero();
    }

    // 모든 스레드가 준비된 뒤 한 번에 출발시켜 실제로 같은 행을 두고 경쟁하게 만든다
    private List<PaymentCancelPreparation> runConcurrently(Callable<PaymentCancelPreparation> task)
        throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentCancelPreparation>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<PaymentCancelPreparation> preparations = new ArrayList<>();
        for (Future<PaymentCancelPreparation> future : futures) {
            preparations.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return preparations;
    }

    private long refundCount() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refund WHERE payment_id = ?", Long.class, paymentId);
        return count == null ? 0 : count;
    }

    private Payment inProgressPayment(Long orderId) {
        return Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_cancel_concurrency_" + orderId)
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(EXPIRES_AT)
            .build();
    }
}
