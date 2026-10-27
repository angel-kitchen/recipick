package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.facade.PaymentConfirmProcessor;
import com.recipick.domain.payment.facade.PaymentExpirationProcessor;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 결제 5분 만료를 실제 MySQL·Redis 위에서 잇는다. 큐 → Worker → 만료 Facade → Processor(PAYMENT 잠금) → 주문 종료·재고 복구,
// 늦은 승인은 SYSTEM 환불 실행까지. PortOne만 Mock이다.
// 픽스처는 리포지토리로 만들어 생성 이벤트가 나가지 않으므로, 만료 작업은 테스트가 큐에 직접 넣는다
class PaymentExpirationFlowIntegrationTest extends PaymentFlowIntegrationSupport {

    // 주문 생성 전 재고. 픽스처는 주문으로 2개가 이미 줄어든 5에서 시작한다
    private static final int STOCK_BEFORE_ORDER = STOCK_AFTER_ORDER + ORDER_QUANTITY;

    @Autowired
    PaymentConfirmProcessor paymentConfirmProcessor;

    @Autowired
    PaymentExpirationProcessor paymentExpirationProcessor;

    @Test
    @DisplayName("결제창만 열린 채(READY) 5분이 지나면 Worker가 결제를 FAILED(EXPIRED)로 닫고 주문 종료·대기 해제·재고 복구까지 한 번에 반영한다")
    void expireReadyPayment() {
        // given
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).minusMinutes(1));
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOneReady(flow));
        PaymentDelayTask task = PaymentDelayTask.paymentExpire(flow.paymentId());
        makeDueNow(task);

        // when
        pollOnly(task);

        // then
        Payment payment = paymentOf(flow);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.EXPIRED);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.PAYMENT_TIMEOUT);
        assertThat(order.getPendingMemberId()).isNull();
        assertThat(stockOf(flow)).isEqualTo(STOCK_BEFORE_ORDER);
        assertThat(refundsOf(flow)).isEmpty();
        assertThat(scoreOf(task)).isNull();
    }

    @Test
    @DisplayName("Worker가 만료 뒤에 확인해도 PG 결제 완료 시각이 만료 시각 이내면 정상 승인한다 — 재고는 주문 몫으로 남고 환불은 없다")
    void approveWhenPaidBeforeExpiry() {
        // given
        LocalDateTime expiresAt = LocalDateTime.now(clock).withNano(0).minusMinutes(1);
        Flow flow = pendingPaymentWithStock(expiresAt);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(portOnePaidAt(flow, expiresAt.minusSeconds(1)));
        PaymentDelayTask task = PaymentDelayTask.paymentExpire(flow.paymentId());
        makeDueNow(task);

        // when
        pollOnly(task);

        // then
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        assertThat(order.getPendingMemberId()).isNull();
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        assertThat(refundsOf(flow)).isEmpty();
        assertThat(scoreOf(task)).isNull();
    }

    @Test
    @DisplayName("만료 뒤 승인이 확인되면 주문을 되살리지 않고 닫은 뒤 SYSTEM 환불을 만들어 실행을 예약하고, 다음 Worker가 ADMIN 취소로 돈만 돌려준다")
    void lateApprovalRefundedThroughSystemRefund() {
        // given
        LocalDateTime expiresAt = LocalDateTime.now(clock).withNano(0).minusMinutes(1);
        Flow flow = pendingPaymentWithStock(expiresAt);
        String cancellationId = "cancel_exp_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(portOnePaidAt(flow, expiresAt.plusSeconds(30)));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), eq(AMOUNT)))
            .willReturn(succeeded(cancellationId));
        PaymentDelayTask expireTask = PaymentDelayTask.paymentExpire(flow.paymentId());
        makeDueNow(expireTask);

        // when — 만료 작업
        pollOnly(expireTask);

        // then — 결제·주문은 닫히고 재고는 한 번 돌아왔다. SYSTEM 환불 실행이 지금 시각으로 예약돼 있다
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(paymentOf(flow).getFailureReason()).isEqualTo(PaymentFailureReason.EXPIRED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_BEFORE_ORDER);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        Refund refund = refunds.get(0);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        PaymentDelayTask refundTask = PaymentDelayTask.systemRefundExecute(refund.getId());
        assertThat(scoreOf(refundTask)).isNotNull().isLessThanOrEqualTo((double) clock.instant().toEpochMilli());
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), eq(AMOUNT));

        // when — SYSTEM 환불 실행 작업(#207)
        pollOnly(refundTask);

        // then — 환불만 끝나고 결제·주문·재고는 그대로다. 서버 키로 한 번만 취소를 요청했다
        Refund completed = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_BEFORE_ORDER);
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), eq("recipick-refund-" + refund.getId()), anyString(), eq(AMOUNT));
    }

    @Test
    @DisplayName("만료 작업이 Redis에서 사라져도 재조회 구간(10분)이 지나면 Fallback Sweep이 다시 등록하고 Worker가 만료를 끝낸다")
    void lostTaskRecoveredByFallback() {
        // given — 큐에 작업이 없는 오래된 IN_PROGRESS 결제
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).minusMinutes(11));
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOneReady(flow));
        PaymentDelayTask task = PaymentDelayTask.paymentExpire(flow.paymentId());
        assertThat(scoreOf(task)).isNull();

        // when — Sweep
        fallbackScheduler.sweep();

        // then — DB 원본으로 작업이 지금 시각으로 돌아왔다
        assertThat(scoreOf(task)).isNotNull().isLessThanOrEqualTo((double) clock.instant().toEpochMilli());

        // when — Worker
        pollOnly(task);

        // then
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(paymentOf(flow).getFailureReason()).isEqualTo(PaymentFailureReason.EXPIRED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_BEFORE_ORDER);
        assertThat(scoreOf(task)).isNull();
    }

    @Test
    @DisplayName("결제 확정과 만료가 같은 결제를 동시에 잡아도 PAYMENT 잠금으로 한쪽만 이겨 두 정상 상태 중 하나로만 끝난다 — 재고 이중 복구·환불 중복 없음")
    void confirmAndExpireConverge() throws Exception {
        // given — 이미 만료 시각이 지났지만 PG 결제는 만료 전에 끝난 결제. 확정이 이기면 승인, 만료가 이기면 늦은 승인 환불이다
        LocalDateTime expiresAt = LocalDateTime.now(clock).withNano(0).minusMinutes(1);
        Flow flow = pendingPaymentWithStock(expiresAt);
        PortOnePayment paid = portOnePaidAt(flow, expiresAt.minusSeconds(1));
        List<Callable<?>> tasks = List.of(
            () -> paymentConfirmProcessor.process(flow.orderId(), paid),
            () -> paymentExpirationProcessor.expire(flow.paymentId()));

        // when
        List<Object> results = runConcurrently(tasks);

        // then — 허용 상태는 정확히 둘이다
        Object confirmOutcome = results.get(0);
        Object expireOutcome = results.get(1);
        Payment payment = paymentOf(flow);
        Order order = orderOf(flow);
        List<Refund> refunds = refundsOf(flow);
        if (payment.getStatus() == PaymentStatus.PAID) {
            // 확정이 먼저 잠갔다. 만료는 잠금 뒤 IN_PROGRESS가 아니라 아무것도 바꾸지 않았다
            assertThat(confirmOutcome).isEqualTo(PaymentApprovalOutcome.APPROVED);
            assertThat(expireOutcome).isEqualTo(PaymentExpirationOutcome.SKIPPED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
            assertThat(refunds).isEmpty();
        } else {
            // 만료가 먼저 잠갔다. 확정은 닫힌 결제의 늦은 승인으로 보고 SYSTEM 환불 행만 만들었다
            assertThat(expireOutcome).isEqualTo(PaymentExpirationOutcome.EXPIRED);
            assertThat(confirmOutcome).isEqualTo(PaymentApprovalOutcome.ALREADY_CLOSED);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.EXPIRED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(stockOf(flow)).isEqualTo(STOCK_BEFORE_ORDER);
            assertThat(refunds).hasSize(1);
            assertThat(refunds.get(0).getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
            assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.REQUESTED);
        }
        // 어느 쪽이 이겨도 깨지면 안 되는 불변식
        assertThat(stockOf(flow)).isLessThanOrEqualTo(STOCK_BEFORE_ORDER);
        assertThat(refunds).hasSizeLessThanOrEqualTo(1);
        assertThat(payment.getStatus() == PaymentStatus.PAID).isEqualTo(order.getStatus() == OrderStatus.PAID);
    }

    // ---------- PortOne 응답 ----------

    // 결제창만 열리고 승인되지 않은 결제
    private PortOnePayment portOneReady(Flow flow) {
        return new PortOnePayment(flow.portonePaymentId(), null, PortOnePaymentStatus.READY,
            AMOUNT, "KRW", null, flow.orderId(), null);
    }

    // 주문 ID·금액이 맞는 승인 완료. 만료 판정은 이 PG 완료 시각으로 한다
    private PortOnePayment portOnePaidAt(Flow flow, LocalDateTime paidAt) {
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.PAID,
            AMOUNT, "KRW", paidAt, flow.orderId(), null);
    }

    private PortOneCancelResult succeeded(String cancellationId) {
        return new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, cancellationId,
            LocalDateTime.now(clock).withNano(0), "cancellation SUCCEEDED");
    }
}
