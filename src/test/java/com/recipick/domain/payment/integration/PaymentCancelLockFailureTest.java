package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.facade.PaymentCancelFacade;
import com.recipick.domain.payment.facade.PaymentCancelProcessor;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

// 회원 취소 TX1·TX2가 실제 MySQL 잠금 대기 초과로 롤백될 때의 복구를 본다. 예외를 Mock으로 던지지 않는다 —
// RowLockHolder의 실제 트랜잭션이 행을 잠그고, 취소 트랜잭션은 MySQL 1205를 받아 실제로 롤백된다.
// 회차마다 잠금 대기를 다 쓰므로 이 클래스의 연결만 대기 한도를 1초로 줄인다. 같은 경로를 기본 5초로 보는 테스트는
// PaymentCancelLockFailureDefaultTimeoutTest다. 몇 번째 회차인지는 Processor spy가 세고, 그 시점에 잠금을 푼다
@TestPropertySource(properties = "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout = 1")
class PaymentCancelLockFailureTest extends PaymentFlowIntegrationSupport {

    @Autowired
    PaymentCancelFacade paymentCancelFacade;

    @MockitoSpyBean
    PaymentCancelProcessor paymentCancelProcessor;

    @MockitoSpyBean
    PaymentService paymentService;

    @Test
    @DisplayName("TX1이 첫 회차에 잠금을 못 얻으면 새 트랜잭션으로 한 번 더 접수해 취소를 끝내고 PortOne은 한 번만 부른다")
    void tx1RetrySucceedsAfterLockFailure() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        given(portOnePaymentClient.cancelPayment(anyString(), anyString(), anyString(), anyLong()))
            .willReturn(succeeded(flow));
        AtomicInteger prepareCalls = new AtomicInteger();
        PaymentCancelResult result;
        try (RowLockHolder holder = new RowLockHolder(transactionTemplate)) {
            holder.hold(() -> lockPayment(flow));
            // 두 번째 접수가 시작될 때 잠금을 푼다. 첫 회차는 이미 잠금 대기 초과로 롤백된 뒤다
            willAnswer(invocation -> {
                if (prepareCalls.incrementAndGet() == 2) {
                    holder.release();
                }
                return invocation.callRealMethod();
            }).given(spyOf(paymentCancelProcessor)).prepare(anyLong(), anyLong(), anyString(), anyString());

            // when
            result = paymentCancelFacade.cancel(flow.memberId(), flow.paymentId(), keyOf(flow), CANCEL_REASON);
        }

        // then
        assertThat(prepareCalls).hasValue(2);
        assertThat(result.outcome()).isEqualTo(PaymentCancelOutcome.CANCELED);
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), anyString(), anyString(), anyLong());
        assertThat(onlyRefund(flow).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        // 첫 회차의 주문 전이는 롤백됐다. PAID → CANCEL_REQUESTED → REFUNDED 한 번만 남는다
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId())).hasSize(2);
    }

    @Test
    @DisplayName("TX1이 두 회차 모두 잠금을 못 얻으면 COMMON-010이고 PortOne을 부르지 않으며 결제·주문·환불은 그대로다")
    void tx1LockFailureExhausted() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        AtomicInteger prepareCalls = new AtomicInteger();
        willAnswer(invocation -> {
            prepareCalls.incrementAndGet();
            return invocation.callRealMethod();
        }).given(spyOf(paymentCancelProcessor)).prepare(anyLong(), anyLong(), anyString(), anyString());
        BusinessException thrown;
        try (RowLockHolder holder = new RowLockHolder(transactionTemplate)) {
            holder.hold(() -> lockPayment(flow));

            // when
            thrown = catchThrowableOfType(BusinessException.class,
                () -> paymentCancelFacade.cancel(flow.memberId(), flow.paymentId(), keyOf(flow), CANCEL_REASON));
        }

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(CommonErrorCode.LOCK_ACQUISITION_TIMEOUT);
        assertThat(prepareCalls).hasValue(2);
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
        assertUntouched(flow);
    }

    @Test
    @DisplayName("다른 결제가 같은 키로 아직 커밋 전이면 조회에 안 보여도 UNIQUE가 상대 커밋을 기다려 COMMON-011로 판정하고 PortOne은 부르지 않는다")
    void sameKeyUncommittedOnOtherPayment() throws Exception {
        // given — 상대는 다른 결제에 같은 키 환불을 INSERT하고 커밋 전에 멈춘다. 우리 요청의 키 조회에는 그 행이 보이지 않는다
        Flow other = paidPaymentWithStock();
        Flow flow = paidPaymentWithStock();
        String sharedKey = keyOf(flow);
        Thread requestThread = Thread.currentThread();
        BusinessException thrown;
        try (RowLockHolder holder = new RowLockHolder(transactionTemplate)) {
            holder.hold(() -> {
                Payment payment = paymentService.getByIdForUpdate(other.paymentId());
                payment.requestCancel();
                paymentService.requestMemberRefund(payment, CANCEL_REASON, sharedKey);
            });
            // 우리 요청이 키 조회를 지나 INSERT 직전에 왔을 때 상대를 커밋시킨다.
            // 상대는 잠금을 더 잡지 않고 커밋만 하므로 두 트랜잭션 사이에 교착 순환이 생기지 않는다
            willAnswer(invocation -> {
                if (Thread.currentThread() == requestThread) {
                    holder.release();
                }
                return invocation.callRealMethod();
            }).given(spyOf(paymentService)).requestMemberRefund(any(Payment.class), anyString(), anyString());

            // when
            thrown = catchThrowableOfType(BusinessException.class,
                () -> paymentCancelFacade.cancel(flow.memberId(), flow.paymentId(), sharedKey, CANCEL_REASON));
        }

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
        assertUntouched(flow);
        assertThat(onlyRefund(other).getIdempotencyKey()).isEqualTo(sharedKey);
    }

    // ---------- helpers ----------

    // spy는 트랜잭션 프록시 뒤에 있다. 프록시로 스텁을 걸면 트랜잭션 경계(MANDATORY 등)를 먼저 지나므로 spy에 직접 건다
    private static <T> T spyOf(T bean) {
        return AopTestUtils.getUltimateTargetObject(bean);
    }

    private void lockPayment(Flow flow) {
        jdbcTemplate.queryForObject("SELECT id FROM payment WHERE id = ? FOR UPDATE", Long.class, flow.paymentId());
    }

    private static String keyOf(Flow flow) {
        return "lock-failure-" + flow.orderId();
    }

    private PortOneCancelResult succeeded(Flow flow) {
        return new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, cancellationIdOf(flow),
            LocalDateTime.now(clock).withNano(0), "cancellation SUCCEEDED");
    }

    private static String cancellationIdOf(Flow flow) {
        return "cancel_lock_" + flow.orderId();
    }

    private Refund onlyRefund(Flow flow) {
        assertThat(refundsOf(flow)).hasSize(1);
        return refundsOf(flow).get(0);
    }

    // TX1이 통째로 롤백된 흔적: 환불 행·주문 전이·이력이 없고 재고도 그대로다
    private void assertUntouched(Flow flow) {
        assertThat(refundsOf(flow)).isEmpty();
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCancelReasonCode()).isNull();
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId())).isEmpty();
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
    }
}
