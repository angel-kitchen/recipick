package com.recipick.domain.order.facade;

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
import org.springframework.transaction.support.TransactionTemplate;

class OrderRefundConcurrencyTest extends IntegrationTestSupport {

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
    @DisplayName("같은 주문에 환불 성공이 5건 동시에 와도 재고는 한 번만 복구되고 이력도 한 건만 남는다")
    void markRefunded_concurrent() throws Exception {
        // given
        Order cancelRequested = Order.builder()
                .memberId(9001L)
                .martId(saveMartAndProduct())
                .status(OrderStatus.PAID)
                .martStatus(MartStatus.RECEIVED)
                .build();
        cancelRequested.requestMemberCancel(null);
        saveOrderWithItem(cancelRequested);

        // when
        // 결제 Facade처럼 한 트랜잭션 안에서 환불 완료 후 재고를 복구한다
        runConcurrently(5, () -> transactionTemplate.executeWithoutResult(status -> {
            List<StockQuantity> items = orderFacade.markRefunded(orderId);
            orderFacade.restoreStock(items);
        }));

        // then
        assertThat(productRepository.findById(productId).orElseThrow().getTotalQuantity()).isEqualTo(7);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 주문에 외부 취소가 5건 동시에 와도 재고는 한 번만 복구되고 이력도 한 건만 남는다")
    void closeByExternalCancel_concurrent() throws Exception {
        // given
        saveOrderWithItem(Order.builder()
                .memberId(9001L)
                .martId(saveMartAndProduct())
                .status(OrderStatus.PAID)
                .martStatus(MartStatus.PREPARING)
                .build());

        // when
        runConcurrently(5, () -> transactionTemplate.executeWithoutResult(status -> {
            List<StockQuantity> items = orderFacade.closeByExternalCancel(orderId, "PortOne 콘솔 전액 취소");
            orderFacade.restoreStock(items);
        }));

        // then
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(productRepository.findById(productId).orElseThrow().getTotalQuantity()).isEqualTo(7);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.EXTERNAL_CANCELED);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).hasSize(1);
    }

    // 재고 5개짜리 상품 하나를 가진 마트를 만들고 마트 ID를 돌려준다
    private Long saveMartAndProduct() {
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
        martId = mart.getId();
        productId = product.getId();
        return martId;
    }

    private void saveOrderWithItem(Order order) {
        Order saved = orderRepository.save(order);
        orderItemRepository.save(OrderItem.builder()
                .order(saved)
                .productId(productId)
                .quantity(2)
                .build());
        orderId = saved.getId();
    }

    // 모든 스레드가 준비된 뒤 한 번에 출발시켜 같은 주문 행 잠금을 실제로 겹치게 한다
    private void runConcurrently(int threads, Runnable task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                task.run();
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();
    }
}
