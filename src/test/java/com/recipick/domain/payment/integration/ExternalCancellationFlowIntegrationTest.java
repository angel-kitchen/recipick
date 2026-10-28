package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.PaymentCancelProcessor;
import com.recipick.domain.payment.facade.PaymentExpirationProcessor;
import com.recipick.domain.payment.facade.RefundReconciliationFacade;
import com.recipick.domain.payment.facade.SystemRefundExecutionFacade;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.RefundReconcileOutcome;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 외부 전액 취소(#231)를 실제 MySQL·Redis 위에서 잇는다. 웹훅 수신 → 큐 → Worker → 외부 취소 Processor → 결제·환불·주문·재고,
// 그리고 웹훅이 늦거나 유실됐을 때 먼저 움직이는 회원 환불 재확인·SYSTEM 환불 실행까지 같은 판정으로 수렴하는지 본다.
// PortOne만 Mock이다 — 스프링 빈 사이의 연결·트랜잭션·PAYMENT 잠금은 실제로 타고 외부 PG 경계만 고정한다.
// 외부 취소 응답 픽스처는 이 테스트만 쓰는 정책 픽스처라 공통 Support가 아니라 여기에 둔다
class ExternalCancellationFlowIntegrationTest extends PaymentFlowIntegrationSupport {

    private static final String CANCELLED = "Transaction.Cancelled";
    private static final String CANCEL_PENDING = "Transaction.CancelPending";
    // PG가 실제로 청구한 금액이 결제 금액과 달라 생긴 SYSTEM 환불의 금액
    private static final long MISMATCHED_AMOUNT = 25_000L;
    private static final String SYSTEM_REFUND_REASON = "금액 불일치 SYSTEM 환불";

    @Autowired
    RefundReconciliationFacade refundReconciliationFacade;

    @Autowired
    SystemRefundExecutionFacade systemRefundExecutionFacade;

    @Autowired
    PaymentCancelProcessor paymentCancelProcessor;

    @Autowired
    PaymentExpirationProcessor paymentExpirationProcessor;

    @Autowired
    PaymentService paymentService;

    @Test
    @DisplayName("대응 환불이 없는 PAID 결제의 콘솔 전액 취소 웹훅이 오면 결제 CANCELED·주문 REFUNDED·EXTERNAL 환불 완료·재고 복구로 수렴한다 — PortOne에 취소를 요청하지 않는다")
    void reconcileConsoleCancellationThroughWebhook() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        String cancellationId = "cancel_console_" + flow.orderId();
        String webhookId = "wh_external_" + flow.orderId();
        PortOnePayment canceled = consoleSucceeded(flow, cancellationId);
        PortOneCancellation console = cancellationOf(canceled);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(canceled);

        // when
        postWebhook(webhookId, CANCELLED, flow.portonePaymentId(), cancellationId).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        pollOnly(PaymentDelayTask.webhookProcess(event.getId()));

        // then — 웹훅은 끝났고 큐에도 남지 않았다
        assertThat(reload(event).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(scoreOf(PaymentDelayTask.webhookProcess(event.getId()))).isNull();

        Payment payment = paymentOf(flow);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(console.canceledAt());

        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.EXTERNAL_CANCELED);

        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        Refund refund = refunds.get(0);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.EXTERNAL);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getAmount()).isEqualTo(AMOUNT);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(refund.getRequestedAt()).isEqualTo(console.requestedAt());
        assertThat(refund.getCompletedAt()).isEqualTo(console.canceledAt());

        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(externalCanceledHistoryCount(flow)).isEqualTo(1);
        assertNoPortOneCancelRequest();
    }

    @Test
    @DisplayName("배송이 시작된 주문은 외부 전액 취소로 결제·주문·환불은 닫지만 재고는 복구하지 않는다 — 실물은 이미 마트를 떠났다")
    void keepStockWhenShippedOrderCanceledExternally() throws Exception {
        // given
        Flow flow = paidPaymentWithStock(MartStatus.SHIPPING);
        String cancellationId = "cancel_console_" + flow.orderId();
        String webhookId = "wh_external_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(consoleSucceeded(flow, cancellationId));

        // when
        postWebhook(webhookId, CANCELLED, flow.portonePaymentId(), cancellationId).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        pollOnly(PaymentDelayTask.webhookProcess(event.getId()));

        // then
        assertThat(reload(event).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getRequestedBy()).isEqualTo(RefundRequester.EXTERNAL);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        assertNoPortOneCancelRequest();
    }

    @Test
    @DisplayName("회원 취소가 PortOne 응답을 받기 전에 콘솔 전액 취소가 끝났으면 회원 환불 재확인이 그 취소로 기존 회원 환불을 완료한다 — EXTERNAL 환불을 만들지 않는다")
    void completeMemberRefundByConsoleCancellationOnReconcile() {
        // given — 결제 후 취소의 TX1만 실행해 취소 ID 없는 회원 환불을 만든다. PortOne 취소 POST는 나가지 않았다
        Flow flow = paidPaymentWithStock();
        paymentCancelProcessor.prepare(
            flow.memberId(), flow.paymentId(), "external-cancel-it-" + flow.orderId(), CANCEL_REASON);
        Refund requested = refundsOf(flow).get(0);
        assertThat(requested.getRequestedBy()).isEqualTo(RefundRequester.MEMBER);
        assertThat(requested.getPortoneCancellationId()).isNull();
        String cancellationId = "cancel_console_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(consoleSucceeded(flow, cancellationId));

        // when
        RefundReconcileOutcome outcome = refundReconciliationFacade.reconcile(requested.getId());

        // then — 환불은 기존 회원 환불 한 건뿐이고, 콘솔 취소 ID로 완료됐다
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getId()).isEqualTo(requested.getId());
        assertThat(refunds.get(0).getRequestedBy()).isEqualTo(RefundRequester.MEMBER);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refunds.get(0).getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(scoreOf(PaymentDelayTask.refundReconcile(requested.getId()))).isNull();
        assertNoPortOneCancelRequest();
    }

    @Test
    @DisplayName("결제 금액과 다른 SYSTEM 환불도 환불 금액과 같은 콘솔 전액 취소가 끝나 있으면 ADMIN 취소 없이 그 취소로 완료한다 — 결제 FAILED·주문 CANCELED·재고는 그대로")
    void completeMismatchedSystemRefundByConsoleCancellationWithoutPost() {
        // given — 결제 만료로 결제·주문을 닫아 재고를 7로 되돌린 뒤, PG가 실제로 청구한 25,000원의 SYSTEM 환불을 만든다
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).minusMinutes(1).withNano(0));
        assertThat(paymentExpirationProcessor.expire(flow.paymentId())).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        Refund requested = transactionTemplate.execute(tx -> paymentService.requestSystemRefund(
            paymentService.getByIdForUpdate(flow.paymentId()), MISMATCHED_AMOUNT, SYSTEM_REFUND_REASON));
        String cancellationId = "cancel_console_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(consoleSucceeded(flow, cancellationId, MISMATCHED_AMOUNT));

        // when
        RefundReconcileOutcome outcome = systemRefundExecutionFacade.execute(requested.getId());

        // then — 판정 기준은 결제 금액이 아니라 환불 금액이다
        assertThat(outcome).isEqualTo(RefundReconcileOutcome.RESOLVED);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        Refund completed = refunds.get(0);
        assertThat(completed.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getAmount()).isEqualTo(MISMATCHED_AMOUNT);
        assertThat(completed.getPortoneCancellationId()).isEqualTo(cancellationId);

        Payment payment = paymentOf(flow);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getAmount()).isEqualTo(AMOUNT);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(scoreOf(PaymentDelayTask.systemRefundExecute(requested.getId()))).isNull();
        assertNoPortOneCancelRequest();
    }

    @Test
    @DisplayName("콘솔 취소가 아직 처리 중이면 웹훅을 RETRY_PENDING으로 두고 Redis에 다시 예약하며, 다음 실행에서 취소가 끝났으면 한 번만 반영한다")
    void retryUntilConsoleCancellationSucceeds() throws Exception {
        // given — 첫 조회는 처리 중, 두 번째 조회는 완료로 답한다
        Flow flow = paidPaymentWithStock();
        String cancellationId = "cancel_console_" + flow.orderId();
        String webhookId = "wh_external_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(
            consoleRequested(flow, cancellationId),
            consoleSucceeded(flow, cancellationId));
        postWebhook(webhookId, CANCEL_PENDING, flow.portonePaymentId(), cancellationId).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        PaymentDelayTask task = PaymentDelayTask.webhookProcess(event.getId());

        // when ① — 첫 실행
        pollOnly(task);

        // then ① — 아무 상태도 바꾸지 않고 다시 예약만 했다
        assertThat(reload(event).getStatus()).isEqualTo(WebhookEventStatus.RETRY_PENDING);
        assertThat(scoreOf(task)).isNotNull();
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(refundsOf(flow)).isEmpty();
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);

        // when ② — 재시도 시각을 앞당겨 두 번째 실행
        makeDueNow(task);
        pollOnly(task);

        // then ② — 이번에는 반영했고 큐에서도 빠졌다
        WebhookEvent processed = reload(event);
        assertThat(processed.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(processed.getAttemptCount()).isEqualTo(2);
        assertThat(scoreOf(task)).isNull();
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getRequestedBy()).isEqualTo(RefundRequester.EXTERNAL);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        verify(portOnePaymentClient, times(2)).getPayment(flow.portonePaymentId());
        assertNoPortOneCancelRequest();
    }

    @Test
    @DisplayName("주문 종료가 실패하면 앞서 바꾼 결제 CANCELED와 저장한 EXTERNAL 환불까지 모두 롤백되고 웹훅은 MANUAL_REVIEW로 남는다")
    void rollbackAllWhenOrderCloseFails() throws Exception {
        // given — 결제는 PAID인데 주문은 PAID가 아닌 모순 상태. 외부 취소 Processor가 결제·환불을 바꾼 뒤 주문 종료에서 실패한다
        Flow flow = paidPaymentWithStock();
        forceOrderStatus(flow, OrderStatus.CANCEL_REQUESTED);
        String cancellationId = "cancel_console_" + flow.orderId();
        String webhookId = "wh_external_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(consoleSucceeded(flow, cancellationId));

        // when
        postWebhook(webhookId, CANCELLED, flow.portonePaymentId(), cancellationId).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        pollOnly(PaymentDelayTask.webhookProcess(event.getId()));

        // then — 운영 확인으로 넘겼고, 부분 커밋된 상태가 하나도 없다
        WebhookEvent reviewed = reload(event);
        assertThat(reviewed.getStatus()).isEqualTo(WebhookEventStatus.MANUAL_REVIEW);
        assertThat(reviewed.getFailureReason()).contains("BusinessException");

        Payment payment = paymentOf(flow);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getCanceledAt()).isNull();
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(refundsOf(flow)).isEmpty();
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        assertThat(externalCanceledHistoryCount(flow)).isZero();
        assertNoPortOneCancelRequest();
    }

    // ---------- helpers ----------

    // 콘솔·PortOne 관리자 화면의 취소 한 건이 든 조회 결과. SUCCEEDED면 결제도 CANCELED이고 PG가 확인한 완료 시각이 있다.
    // 아니면 결제는 아직 PAID로 보이고 완료 시각이 없다. DB 시각 정밀도와 맞추려고 초 단위로 자른다
    private PortOnePayment externalCancellation(Flow flow, String cancellationId, PortOneCancellationStatus status,
                                                PortOneCancellationTrigger trigger, long amount) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        boolean succeeded = status == PortOneCancellationStatus.SUCCEEDED;
        PortOneCancellation cancellation = new PortOneCancellation(cancellationId, status, amount,
            now.minusMinutes(6), succeeded ? now.minusMinutes(1) : null, trigger);
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(),
            succeeded ? PortOnePaymentStatus.CANCELED : PortOnePaymentStatus.PAID,
            amount, "KRW", flow.paidAt(), flow.orderId(), null, List.of(cancellation));
    }

    private PortOnePayment consoleSucceeded(Flow flow, String cancellationId) {
        return consoleSucceeded(flow, cancellationId, AMOUNT);
    }

    private PortOnePayment consoleSucceeded(Flow flow, String cancellationId, long amount) {
        return externalCancellation(flow, cancellationId, PortOneCancellationStatus.SUCCEEDED,
            PortOneCancellationTrigger.CONSOLE, amount);
    }

    private PortOnePayment consoleRequested(Flow flow, String cancellationId) {
        return externalCancellation(flow, cancellationId, PortOneCancellationStatus.REQUESTED,
            PortOneCancellationTrigger.CONSOLE, AMOUNT);
    }

    // 기대값을 응답에서 꺼내 비교한다. 같은 시각을 테스트에서 따로 계산하면 두 now 사이에 초가 넘어가 어긋날 수 있다
    private static PortOneCancellation cancellationOf(PortOnePayment portOnePayment) {
        return portOnePayment.cancellations().get(0);
    }

    // 정상 흐름으로는 만들 수 없는 모순 상태다. 주문 종료가 실패하는 지점을 만들어 Processor 롤백만 보려고 상태 컬럼 하나만 바꾼다.
    // 한 행이 바뀌었는지 확인해 준비 실패를 시나리오 실패와 섞지 않는다
    private void forceOrderStatus(Flow flow, OrderStatus status) {
        int updated = jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", status.name(), flow.orderId());
        assertThat(updated).isEqualTo(1);
    }

    // 외부 취소로 주문을 닫은 이력. 같은 취소가 두 번 반영되면 두 건이 된다
    private long externalCanceledHistoryCount(Flow flow) {
        return orderStatusHistoryRepository.findAllByOrderId(flow.orderId()).stream()
            .filter(history -> OrderStatus.REFUNDED.name().equals(history.getToStatus()))
            .filter(history -> CancelReasonCode.EXTERNAL_CANCELED.name().equals(history.getReason()))
            .count();
    }

    // 외부 취소는 PG가 이미 돈을 돌려준 뒤라 레시픽 서버가 회원 취소·ADMIN 취소 어느 쪽도 요청하면 안 된다
    private void assertNoPortOneCancelRequest() {
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
        verify(portOnePaymentClient, never()).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }
}
