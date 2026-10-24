package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.global.exception.BusinessException;
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

class OrderAcceptConcurrencyTest extends IntegrationTestSupport {

    // data.sql의 시연 마트(삼청 마트). 주문 ID로만 조회하므로 다른 테스트와 격리할 필요가 없다
    private static final Long MART_ID = 1L;
    private static final Long ADMIN_ID = 20L;

    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderStatusHistoryRepository orderStatusHistoryRepository;

    private Long orderId;

    @AfterEach
    void cleanUp() {
        if (orderId != null) {
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
    }

    @Test
    @DisplayName("같은 주문에 수락이 5건 동시에 와도 모두 성공으로 끝나고 이력은 한 건만 남는다")
    void acceptByMart_concurrent() throws Exception {
        // given
        orderId = receivedOrder().getId();
        int threads = 5;

        // when
        List<Object> results = runConcurrently(threads, i -> accept());

        // then
        assertThat(results).allSatisfy(result -> assertThat(result).isEqualTo(MartStatus.PREPARING));
        assertThat(orderRepository.findById(orderId).orElseThrow().getMartStatus()).isEqualTo(MartStatus.PREPARING);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("수락과 회원 취소가 동시에 들어오면 주문 행 잠금으로 한쪽만 성공하고 이력도 한 건만 남는다")
    void acceptByMart_withMemberCancel() throws Exception {
        // given
        orderId = receivedOrder().getId();

        // when: 짝수 번째는 수락, 홀수 번째는 회원 취소
        List<Object> results = runConcurrently(6, i -> i % 2 == 0
                ? accept()
                : orderService.markMemberCancelRequested(orderId, "단순 변심"));

        // then
        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findAllByOrderId(orderId);
        assertThat(histories).hasSize(1);
        if (order.getMartStatus() == MartStatus.PREPARING) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(histories.get(0).getActor()).isEqualTo(OrderStatusActor.MART);
            // 수락이 먼저 잠그면 취소는 모두 마트가 준비 중이라고 거절되고 수락은 모두 성공(멱등)한다
            assertThat(results).containsOnly(MartStatus.PREPARING, MemberCancelRequestResult.MART_ALREADY_PREPARING);
        } else {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
            assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
            assertThat(histories.get(0).getActor()).isEqualTo(OrderStatusActor.MEMBER);
            // 취소가 먼저 잠그면 수락은 모두 수락 불가로 거절되고, 나머지 취소는 상태 불일치로 끝난다
            assertThat(results).containsOnly(OrderErrorCode.ORDER_NOT_ACCEPTABLE,
                    MemberCancelRequestResult.REQUESTED, MemberCancelRequestResult.INVALID_ORDER_STATUS);
            assertThat(results).containsOnlyOnce(MemberCancelRequestResult.REQUESTED);
        }
    }

    private Object accept() {
        try {
            return orderService.acceptByMart(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, orderId).martStatus();
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    private Order receivedOrder() {
        return orderRepository.save(Order.builder()
                .memberId(9002L)
                .martId(MART_ID)
                .status(OrderStatus.PAID)
                .martStatus(MartStatus.RECEIVED)
                .build());
    }

    private interface IndexedTask {
        Object run(int index) throws Exception;
    }

    private List<Object> runConcurrently(int threads, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.run(index);
            }));
        }
        ready.await();
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }
}
