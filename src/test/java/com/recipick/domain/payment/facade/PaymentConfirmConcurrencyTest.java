package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
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
import org.springframework.jdbc.core.JdbcTemplate;

// 스레드들이 각자 커밋하므로 테스트 트랜잭션 롤백을 쓸 수 없다. OrderPaymentFailureConcurrencyTest(준희)처럼 직접 정리한다
class PaymentConfirmConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 5;
    private static final Long MEMBER_ID = 9101L;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 21, 10, 3);

    @Autowired
    PaymentConfirmProcessor paymentConfirmProcessor;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

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
            orderRepository.deleteById(orderId);
        }
    }

    @Test
    @DisplayName("같은 결제에 승인이 5건 동시에 와도 결제·주문은 한 번만 PAID가 되고 나머지는 ALREADY_PAID다")
    void concurrentApprovals() throws Exception {
        // given
        Order order = orderRepository.save(Order.builder()
            .memberId(MEMBER_ID)
            .martId(1L)
            .pendingMemberId(MEMBER_ID)
            .status(OrderStatus.PENDING_PAYMENT)
            .martStatus(MartStatus.NONE)
            .build());
        orderId = order.getId();
        paymentId = paymentRepository.save(inProgressPayment(orderId)).getId();
        PortOnePayment portOne = paidPortOne(orderId);

        // when
        List<PaymentApprovalOutcome> outcomes = runConcurrently(() -> paymentConfirmProcessor.process(orderId, portOne));

        // then
        assertThat(outcomes).filteredOn(outcome -> outcome == PaymentApprovalOutcome.APPROVED).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome == PaymentApprovalOutcome.ALREADY_PAID).hasSize(THREADS - 1);

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);

        Order paidOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        assertThat(paidOrder.getPendingMemberId()).isNull();
        // markPaid 한 번 = ORDER 이력 1 + MART 이력 1
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).hasSize(2);
        assertThat(refundCount()).isZero();
    }

    @Test
    @DisplayName("만료로 닫힌 결제에 늦은 승인이 5건 동시에 와도 SYSTEM 환불은 한 건만 만들고 결제·주문은 그대로다")
    void concurrentLateApprovals() throws Exception {
        // given
        Order order = orderRepository.save(Order.builder()
            .memberId(MEMBER_ID)
            .martId(1L)
            .status(OrderStatus.CANCELED)
            .martStatus(MartStatus.NONE)
            .build());
        orderId = order.getId();
        Payment expired = inProgressPayment(orderId);
        expired.fail(PaymentFailureReason.EXPIRED, null);
        paymentId = paymentRepository.save(expired).getId();
        PortOnePayment portOne = paidPortOne(orderId);

        // when
        List<PaymentApprovalOutcome> outcomes = runConcurrently(() -> paymentConfirmProcessor.process(orderId, portOne));

        // then
        assertThat(outcomes).containsOnly(PaymentApprovalOutcome.ALREADY_CLOSED);
        assertThat(refundCount()).isEqualTo(1);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).isEmpty();
    }

    // 모든 스레드가 준비된 뒤 한 번에 출발시켜 실제로 같은 행을 두고 경쟁하게 만든다
    private List<PaymentApprovalOutcome> runConcurrently(java.util.concurrent.Callable<PaymentApprovalOutcome> task)
        throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentApprovalOutcome>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<PaymentApprovalOutcome> outcomes = new ArrayList<>();
        for (Future<PaymentApprovalOutcome> future : futures) {
            outcomes.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return outcomes;
    }

    private long refundCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refund WHERE payment_id = ?", Long.class, paymentId);
        return count == null ? 0 : count;
    }

    private Payment inProgressPayment(Long orderId) {
        return Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_concurrency_" + orderId)
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(PAID_AT.plusMinutes(1))
            .build();
    }

    private PortOnePayment paidPortOne(Long orderId) {
        return new PortOnePayment("pay_concurrency_" + orderId, "tx_concurrency", PortOnePaymentStatus.PAID,
            23800L, "KRW", PAID_AT, orderId, null);
    }
}
