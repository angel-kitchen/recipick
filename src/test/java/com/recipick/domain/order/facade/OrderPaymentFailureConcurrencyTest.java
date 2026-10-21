package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class OrderPaymentFailureConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    OrderFacade orderFacade;
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
    TransactionTemplate transactionTemplate;

    private Long martId;
    private Long productId;
    private Long orderId;

    @AfterEach
    void cleanUp() {
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
    @DisplayName("같은 주문에 결제 실패가 5건 동시에 와도 재고는 한 번만 복구되고 이력도 한 건만 남는다")
    void closeByPaymentFailure_concurrent() throws Exception {
        // given
        Mart mart = martRepository.save(Mart.builder()
                .regionId(31L)
                .name("동시성 테스트 마트")
                .latitude(new BigDecimal("37.3670000"))
                .longitude(new BigDecimal("127.1080000"))
                .serviceRadiusM(2000)
                .build());
        Product product = productRepository.save(Product.builder()
                .mart(mart)
                .name("애호박")
                .totalQuantity(5)
                .deleted(false)
                .build());
        Order order = orderRepository.save(Order.builder()
                .memberId(9001L)
                .martId(mart.getId())
                .pendingMemberId(9001L)
                .status(OrderStatus.PENDING_PAYMENT)
                .martStatus(MartStatus.NONE)
                .build());
        orderItemRepository.save(OrderItem.builder()
                .order(order)
                .productId(product.getId())
                .quantity(2)
                .build());
        martId = mart.getId();
        productId = product.getId();
        orderId = order.getId();

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                // 결제 Facade처럼 한 트랜잭션 안에서 주문 종료 후 재고를 복구한다
                transactionTemplate.executeWithoutResult(status -> {
                    List<StockQuantity> items =
                            orderFacade.closeByPaymentFailure(orderId, CancelReasonCode.PAYMENT_TIMEOUT, null);
                    orderFacade.restoreStock(items);
                });
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // then
        assertThat(productRepository.findById(productId).orElseThrow().getTotalQuantity()).isEqualTo(7);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 주문 종료나 재고 복구를 호출하면 거부한다")
    void calledWithoutTransaction() {
        // when, then
        assertThatThrownBy(() -> orderFacade.closeByPaymentFailure(1L, CancelReasonCode.PAYMENT_FAILED, null))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> orderFacade.restoreStock(List.of(new StockQuantity(1L, 1))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
