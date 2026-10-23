package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.repository.PaymentRepository;
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

// 스레드들이 각자 커밋하므로 테스트 트랜잭션 롤백을 쓸 수 없다. PaymentConfirmConcurrencyTest처럼 직접 정리한다
class PaymentCancelConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 5;
    private static final Long MEMBER_ID = 9102L;
    private static final String REASON = "동시 취소";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 21, 10, 5);

    @Autowired
    PaymentCancelProcessor paymentCancelProcessor;
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
    @DisplayName("같은 결제에 취소가 5건 동시에 와도 주문 종료는 한 번만 일어나고 나머지는 멱등 응답으로 끝난다")
    void concurrentCancels() throws Exception {
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

        // when
        List<PaymentCancelResponse> responses =
            runConcurrently(() -> paymentCancelProcessor.cancelBeforePay(MEMBER_ID, paymentId, REASON));

        // then — 다섯 요청 모두 예외 없이 같은 종료 상태를 받는다
        assertThat(responses).hasSize(THREADS);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response.failureReason()).isEqualTo(PaymentFailureReason.CANCELED_BEFORE_PAY);
            assertThat(response.refundAmount()).isZero();
        });

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.CANCELED_BEFORE_PAY);
        assertThat(payment.getCanceledAt()).isNull();

        Order canceledOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceledOrder.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
        assertThat(canceledOrder.getCancelReasonDetail()).isEqualTo(REASON);
        assertThat(canceledOrder.getPendingMemberId()).isNull();
        // ORDER 이력 1건 = 실제 종료 상태 전이가 한 번만 일어났다는 뜻. 상품 fixture가 없어 재고 복구 자체는 빈 목록이다.
        // 멱등 재요청이 OrderFacade에 아예 들어가지 않는 것은 PaymentCancelProcessorTest에서 따로 검증한다
        assertThat(orderStatusHistoryRepository.findAllByOrderId(orderId)).hasSize(1);
        // 결제 전 취소는 REFUND를 만들지 않는다
        assertThat(refundCount()).isZero();
    }

    // 모든 스레드가 준비된 뒤 한 번에 출발시켜 실제로 같은 행을 두고 경쟁하게 만든다
    private List<PaymentCancelResponse> runConcurrently(Callable<PaymentCancelResponse> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentCancelResponse>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<PaymentCancelResponse> responses = new ArrayList<>();
        for (Future<PaymentCancelResponse> future : futures) {
            responses.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return responses;
    }

    private long refundCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refund WHERE payment_id = ?", Long.class, paymentId);
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
