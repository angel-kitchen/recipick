package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.PaymentConfirmFacade;
import com.recipick.domain.payment.facade.SystemRefundExecutionFacade;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.RefundReconcileOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

// SYSTEM 환불(#207)을 실제 MySQL·Redis 위에서 잇는다. 결제 확정 흐름 → SYSTEM 환불 생성 → 큐 → Worker → ADMIN 취소 → 환불 완료.
// PortOne만 Mock이다 — 스프링 빈 사이의 연결·트랜잭션·PAYMENT 잠금은 실제로 타고 외부 PG 경계만 고정한다.
// 결제 확정은 HTTP Controller가 아니라 PaymentConfirmFacade를 직접 부른다. HTTP 매핑은 Controller 테스트의 몫이다
class SystemRefundFlowIntegrationTest extends PaymentFlowIntegrationSupport {

    private static final String EXPIRED_REASON = "결제 대기 5분 만료";

    @Autowired
    PaymentConfirmFacade paymentConfirmFacade;

    @Autowired
    SystemRefundExecutionFacade systemRefundExecutionFacade;

    @Test
    @DisplayName("결제 대기 만료 뒤 승인이 확인되면 결제 확정 흐름이 SYSTEM 환불을 만들어 바로 실행을 예약하고, Worker가 ADMIN 취소로 환불을 완료한다 — 결제는 FAILED 그대로")
    void lateApprovalRefundedThroughWorker() {
        // given
        Flow flow = pendingPayment();
        String cancellationId = "cancel_sys_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaidAfterExpiry(flow));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(succeeded(cancellationId));

        // when — 결제 확정 흐름. 만료라 PAYMENT_EXPIRED로 끝나지만 SYSTEM 환불과 실행 예약은 커밋돼 있다
        Refund refund = confirmLateApproval(flow);
        PaymentDelayTask task = PaymentDelayTask.systemRefundExecute(refund.getId());

        // then — 결제·주문은 닫혔고, 실행 작업이 지금 시각으로 큐에 있다
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getAmount()).isEqualTo(AMOUNT);
        assertThat(scoreOf(task)).isNotNull().isLessThanOrEqualTo((double) clock.instant().toEpochMilli());

        // when — Worker 한 번
        pollOnly(task);

        // then — 환불만 완료되고 결제는 FAILED로 남는다. 취소는 환불 행마다 고정된 서버 키로 한 번만 요청했다
        Refund completed = reloadRefund(refund);
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(scoreOf(task)).isNull();
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(
            flow.portonePaymentId(), serverKey(refund), EXPIRED_REASON, AMOUNT);
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("ADMIN 취소가 REQUESTED로 끝나면 취소 ID를 남기고 10분 뒤 재실행을 예약하고, 뒤이어 온 Cancelled 웹훅이 MANUAL_REVIEW가 아니라 PROCESSED로 환불을 완료한다 — 남은 재실행은 SKIPPED")
    void cancelledWebhookCompletesSystemRefund() throws Exception {
        // given — 확정 흐름과 실행은 결제를 PAID로 보고, 웹훅 시점에는 취소가 완료돼 있다
        Flow flow = pendingPayment();
        String cancellationId = "cancel_sys_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(
            portOnePaidAfterExpiry(flow),
            portOnePaidAfterExpiry(flow),
            portOneWithCancellation(flow, cancellationId, PortOneCancellationStatus.SUCCEEDED));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(new PortOneCancelResult(
                PortOneCancelOutcome.REQUESTED, cancellationId, null, "cancellation REQUESTED"));
        Refund refund = confirmLateApproval(flow);
        PaymentDelayTask task = PaymentDelayTask.systemRefundExecute(refund.getId());

        // when — 실행. PortOne은 아직 처리 중이라고 답한다
        Instant before = clock.instant();
        pollOnly(task);

        // then — 환불은 REQUESTED로 취소 ID만 기록됐고, 재실행이 10분 뒤로 예약됐다
        Refund requested = reloadRefund(refund);
        assertThat(requested.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(requested.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(scoreOf(task)).isGreaterThanOrEqualTo((double) before.plus(Duration.ofMinutes(10)).toEpochMilli());

        // when — Cancelled 웹훅 수신 → Worker. 재실행은 아직 시각이 안 돼 함께 꺼내지 않는다
        String webhookId = "wh_sys_cancel_" + flow.orderId();
        postWebhook(webhookId, "Transaction.Cancelled", flow.portonePaymentId(), cancellationId)
            .andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        pollOnly(PaymentDelayTask.webhookProcess(event.getId()));

        // then — 웹훅이 SYSTEM 환불을 찾아 SystemRefundProcessor로 완료했다
        assertThat(reload(event).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        Refund completed = reloadRefund(refund);
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);

        // when — 10분이 지난 것으로 보고 남은 재실행을 돌린다
        makeDueNow(task);
        pollOnly(task);

        // then — 이미 끝난 환불이라 PortOne을 다시 묻지도 취소하지도 않는다
        assertThat(reloadRefund(refund).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(scoreOf(task)).isNull();
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        verify(portOnePaymentClient, times(3)).getPayment(flow.portonePaymentId());
    }

    @Test
    @DisplayName("SYSTEM 환불 실행 작업이 Redis에서 사라져도 재실행 간격(10분)이 지나면 Fallback Sweep이 다시 등록하고 Worker가 환불을 완료한다")
    void lostTaskRecoveredByFallback() {
        // given
        Flow flow = pendingPayment();
        String cancellationId = "cancel_sys_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaidAfterExpiry(flow));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(succeeded(cancellationId));
        Refund refund = confirmLateApproval(flow);
        PaymentDelayTask task = PaymentDelayTask.systemRefundExecute(refund.getId());

        // when — 큐 작업이 사라졌고 11분이 지났다
        removeTask(task);
        moveRefundRequestedAt(refund.getId(), Duration.ofMinutes(11));
        assertThat(scoreOf(task)).isNull();
        fallbackScheduler.sweep();

        // then — DB의 REQUESTED 환불을 보고 실행 작업을 다시 넣었다
        assertThat(scoreOf(task)).isNotNull();

        // when
        pollOnly(task);

        // then
        assertThat(reloadRefund(refund).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(scoreOf(task)).isNull();
    }

    @Test
    @DisplayName("같은 SYSTEM 환불을 두 실행이 동시에 처리해도 취소 요청은 모두 같은 서버 키이고 환불은 한 번만 완료된다")
    void concurrentExecutionUsesSameServerKey() throws Exception {
        // given — 두 실행 모두 GET에서 취소가 없다고 볼 수 있어 POST가 1~2번 나갈 수 있다.
        // Mock은 PortOne의 멱등 처리를 흉내 내지 못하므로 POST 횟수가 아니라 키가 하나뿐인지를 본다
        Flow flow = pendingPayment();
        String cancellationId = "cancel_sys_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaidAfterExpiry(flow));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(succeeded(cancellationId));
        Refund refund = confirmLateApproval(flow);
        // Worker를 거치지 않고 실행 Facade를 직접 두 번 부른다
        removeTask(PaymentDelayTask.systemRefundExecute(refund.getId()));
        Callable<Object> execute = () -> systemRefundExecutionFacade.execute(refund.getId());

        // when
        List<Object> outcomes = runConcurrently(List.of(execute, execute));

        // then — 먼저 끝낸 쪽이 RESOLVED, 늦은 쪽은 끝난 환불을 보고 RESOLVED 또는 SKIPPED
        assertThat(outcomes)
            .isSubsetOf(RefundReconcileOutcome.RESOLVED, RefundReconcileOutcome.SKIPPED)
            .contains(RefundReconcileOutcome.RESOLVED);
        Refund completed = reloadRefund(refund);
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(portOnePaymentClient, atLeastOnce())
            .cancelPaymentByAdmin(eq(flow.portonePaymentId()), keys.capture(), anyString(), anyLong());
        verify(portOnePaymentClient, atMost(2)).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        assertThat(keys.getAllValues()).isNotEmpty().containsOnly(serverKey(refund));
    }

    // ---------- helpers ----------

    // 결제 대기 시각이 지난 뒤 PG 승인이 확인된 응답. Verifier가 paidAt이 expiresAt보다 늦으면 EXPIRED로 판정한다
    private PortOnePayment portOnePaidAfterExpiry(Flow flow) {
        LocalDateTime paidAt = paymentOf(flow).getExpiresAt().plusSeconds(30);
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.PAID,
            AMOUNT, "KRW", paidAt, flow.orderId(), null);
    }

    // 결제 확정 흐름을 돌려 SYSTEM 환불을 만든다. 만료라 BusinessException(PAYMENT_EXPIRED)로 끝나는 것이 정상이다
    private Refund confirmLateApproval(Flow flow) {
        Throwable thrown = catchThrowable(() ->
            paymentConfirmFacade.confirm(flow.memberId(), flow.orderId(), flow.portonePaymentId()));
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_EXPIRED));
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        return refunds.get(0);
    }

    private PortOneCancelResult succeeded(String cancellationId) {
        return new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, cancellationId,
            LocalDateTime.now(clock).withNano(0), "cancellation SUCCEEDED");
    }

    private Refund reloadRefund(Refund refund) {
        return refundRepository.findById(refund.getId()).orElseThrow();
    }

    // 실행 Facade와 같은 규칙. 환불 행 하나가 PortOne 취소 작업 하나다
    private static String serverKey(Refund refund) {
        return "recipick-refund-" + refund.getId();
    }
}
