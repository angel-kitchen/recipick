package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class OrderRejectConcurrencyTest extends IntegrationTestSupport {

    // data.sql의 시연 마트(삼청 마트). 주문 ID로만 조회하므로 다른 테스트와 격리할 필요가 없다
    private static final Long MART_ID = 1L;
    private Long adminId;

    @Autowired
    AdminRepository adminRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    TransactionTemplate transactionTemplate;

    private Long orderId;

    @BeforeEach
    void setupAdmin() {
        adminId = adminRepository.saveAndFlush(Admin.builder()
                .loginId(UUID.randomUUID().toString()).passwordHash("hash")
                .role(AdminRole.MART_ADMIN).martId(MART_ID).active(true).build()).getId();
    }

    @AfterEach
    void cleanUp() {
        if (orderId != null) {
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        jdbcTemplate.update("DELETE FROM admin_audit_log WHERE admin_id = ?", adminId);
        adminRepository.deleteById(adminId);
    }

    @Test
    @DisplayName("같은 주문에 거절이 5건 동시에 와도 한 건만 접수되고 나머지는 이미 거절됐다고 끝나며 이력은 두 건만 남는다")
    void requestMartRejection_concurrent() throws Exception {
        // given
        orderId = receivedOrder().getId();
        int threads = 5;

        // when
        List<Object> results = runConcurrently(threads, i -> reject());

        // then
        assertThat(results).containsOnly(MartRejectRequestResult.REJECTED, MartRejectRequestResult.ALREADY_REJECTED);
        assertThat(results).containsOnlyOnce(MartRejectRequestResult.REJECTED);
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).hasSize(2);
    }

    @Test
    @DisplayName("거절과 수락이 동시에 들어오면 주문 행 잠금으로 한쪽만 성공한다")
    void requestMartRejection_withAccept() throws Exception {
        // given
        orderId = receivedOrder().getId();

        // when: 짝수 번째는 거절, 홀수 번째는 수락
        List<Object> results = runConcurrently(6, i -> i % 2 == 0 ? reject() : accept());

        // then
        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findAllByOrderId(orderId);
        if (order.getMartStatus() == MartStatus.PREPARING) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(histories).hasSize(1);
            // 수락이 먼저 잠그면 거절은 모두 거절 불가로 끝나고 수락은 모두 성공(멱등)한다
            assertThat(results).containsOnly(MartStatus.PREPARING, MartRejectRequestResult.NOT_REJECTABLE);
        } else {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
            assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
            assertThat(histories).hasSize(2);
            // 거절이 먼저 잠그면 수락은 모두 수락 불가, 나머지 거절은 이미 거절됐다고 끝난다
            assertThat(results).containsOnly(MartRejectRequestResult.REJECTED, OrderErrorCode.ORDER_NOT_ACCEPTABLE,
                    MartRejectRequestResult.ALREADY_REJECTED);
            assertThat(results).containsOnlyOnce(MartRejectRequestResult.REJECTED);
        }
    }

    @Test
    @DisplayName("거절과 회원 취소가 동시에 들어오면 주문 행 잠금으로 한쪽만 성공하고 최초 취소 사유만 남는다")
    void requestMartRejection_withMemberCancel() throws Exception {
        // given
        orderId = receivedOrder().getId();

        // when: 짝수 번째는 거절, 홀수 번째는 회원 취소
        List<Object> results = runConcurrently(6, i -> i % 2 == 0
                ? reject()
                : orderService.markMemberCancelRequested(orderId, "단순 변심"));

        // then
        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findAllByOrderId(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        if (order.getMartStatus() == MartStatus.REJECTED) {
            assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MART_REJECTED);
            assertThat(histories).hasSize(2);
            // 거절이 먼저 잠그면 회원 취소는 모두 상태 불일치, 나머지 거절은 이미 거절됐다고 끝난다
            assertThat(results).containsOnly(MartRejectRequestResult.REJECTED, MartRejectRequestResult.ALREADY_REJECTED,
                    MemberCancelRequestResult.INVALID_ORDER_STATUS);
            assertThat(results).containsOnlyOnce(MartRejectRequestResult.REJECTED);
        } else {
            assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
            assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
            assertThat(histories).hasSize(1);
            // 회원 취소가 먼저 잠그면 거절은 모두 거절 불가, 나머지 취소는 상태 불일치로 끝난다
            assertThat(results).containsOnly(MartRejectRequestResult.NOT_REJECTABLE,
                    MemberCancelRequestResult.REQUESTED, MemberCancelRequestResult.INVALID_ORDER_STATUS);
            assertThat(results).containsOnlyOnce(MemberCancelRequestResult.REQUESTED);
        }
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 거절 접수를 호출하면 거부한다")
    void requestMartRejection_withoutTransaction() {
        // when, then
        assertThatThrownBy(() -> orderService.requestMartRejection(
                AdminRole.MART_ADMIN, MART_ID, adminId, 1L, "재료 소진"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // 결제 Facade의 TX1처럼 바깥 트랜잭션 안에서 부른다. 상태 판정은 반환값이라 예외로 바꿀 것이 없다
    private Object reject() {
        return transactionTemplate.execute(status -> orderService.requestMartRejection(
                AdminRole.MART_ADMIN, MART_ID, adminId, orderId, "재료 소진"));
    }

    private Object accept() {
        AuthPrincipal principal = new AuthPrincipal(SubjectType.ADMIN, adminId, "MART_ADMIN", MART_ID);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
        try {
            return orderService.acceptByMart(AdminRole.MART_ADMIN, MART_ID, adminId, orderId).martStatus();
        } catch (BusinessException e) {
            return e.getErrorCode();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Order receivedOrder() {
        return orderRepository.save(Order.builder()
                .memberId(9003L)
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
