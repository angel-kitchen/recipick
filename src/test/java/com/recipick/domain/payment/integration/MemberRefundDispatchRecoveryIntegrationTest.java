package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.dto.PaymentCancelPreparation;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.facade.PaymentCancelFacade;
import com.recipick.domain.payment.facade.PaymentCancelProcessor;
import com.recipick.domain.payment.facade.RefundReconciliationFacade;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

// 회원 취소의 TX1(접수)은 커밋됐는데 PortOne 취소 POST가 나가지 못한 상태(POST 직전 서버 중단·연결 실패)를 실제 MySQL·Redis로 재현한다.
// 재확인이 GET으로 "이 환불의 취소 없음"을 확인하면 같은 서버 멱등 키로 POST를 한 번 보내 복구해야 하고,
// 취소가 이미 보이거나 GET이 실패하면 POST하지 않아야 한다. 자동 POST 스위치는 운영 기본 false라 테스트에서만 켠다
@TestPropertySource(properties = "payment.member-refund-recovery.auto-repost-enabled=true")
class MemberRefundDispatchRecoveryIntegrationTest extends PaymentFlowIntegrationSupport {

    private static final String SERVER_KEY_PREFIX = "recipick-refund-";

    @Autowired
    private PaymentCancelProcessor paymentCancelProcessor;

    @Autowired
    private PaymentCancelFacade paymentCancelFacade;

    @Autowired
    private RefundReconciliationFacade refundReconciliationFacade;

    @Test
    @DisplayName("TX1 커밋 뒤 PortOne POST가 나가지 않은 환불은 재확인이 같은 서버 키로 POST 1회를 보내 취소 완료·재고 복구까지 끝낸다")
    void recoverWhenPostNeverSent() {
        // given — TX1만 커밋됐고 PortOne은 한 번도 부르지 않았다
        Flow flow = paidPaymentWithStock();
        PaymentCancelPreparation preparation = paymentCancelProcessor.prepare(
            flow.memberId(), flow.paymentId(), "missing-post-" + flow.paymentId(), CANCEL_REASON);
        assertThat(preparation.requiresPortOneCall()).isTrue();
        Refund refund = refundsOf(flow).get(0);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());

        String serverKey = SERVER_KEY_PREFIX + refund.getId();
        String cancellationId = "recovered_" + refund.getId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(paidWithoutCancellation(flow));
        given(portOnePaymentClient.cancelPayment(flow.portonePaymentId(), serverKey, CANCEL_REASON, AMOUNT))
            .willReturn(succeeded(cancellationId));

        // when — Worker가 재확인을 꺼낸다
        makeDueNow(PaymentDelayTask.refundReconcile(refund.getId()));
        pollOnly(PaymentDelayTask.refundReconcile(refund.getId()));

        // then — 결제·주문·환불·재고가 취소 API의 TX2와 같은 결과다
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        Refund persisted = refundsOf(flow).get(0);
        assertThat(persisted.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(persisted.getPortoneCancellationId()).isEqualTo(cancellationId);
        verify(portOnePaymentClient, times(1))
            .cancelPayment(flow.portonePaymentId(), serverKey, CANCEL_REASON, AMOUNT);

        // then — 끝난 뒤 Worker가 다시 돌아도 POST·재고 복구를 반복하지 않는다
        makeDueNow(PaymentDelayTask.refundReconcile(refund.getId()));
        pollOnly(PaymentDelayTask.refundReconcile(refund.getId()));
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(refundsOf(flow)).hasSize(1);
        verify(portOnePaymentClient, times(1))
            .cancelPayment(flow.portonePaymentId(), serverKey, CANCEL_REASON, AMOUNT);
    }

    @Test
    @DisplayName("PortOne 취소는 성공했는데 응답만 잃은 환불은 GET으로 찾은 취소를 반영하고 POST를 다시 보내지 않는다")
    void recoverFromLostResponseWithoutAnotherPost() {
        // given — 취소 ID를 못 받았지만 PortOne에는 서버 API 전액 취소가 끝나 있다.
        // 조회 픽스처의 취소 요청 시각(6분 전)보다 환불 접수가 앞서야 이 환불의 취소로 본다(TX1 커밋 뒤 POST하므로 실제 순서와 같다)
        Flow flow = paidPaymentWithStock();
        paymentCancelProcessor.prepare(
            flow.memberId(), flow.paymentId(), "lost-response-" + flow.paymentId(), CANCEL_REASON);
        Refund refund = refundsOf(flow).get(0);
        moveRefundRequestedAt(refund.getId(), Duration.ofMinutes(7));
        String cancellationId = "existing_" + refund.getId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(portOneWithCancellation(flow, cancellationId, PortOneCancellationStatus.SUCCEEDED));

        // when
        makeDueNow(PaymentDelayTask.refundReconcile(refund.getId()));
        pollOnly(PaymentDelayTask.refundReconcile(refund.getId()));

        // then
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(refundsOf(flow).get(0).getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Redis 예약이 사라진 환불은 Fallback Sweep이 DB 기준으로 다시 넣고, Worker가 꺼내 같은 복구를 한다")
    void recoverAfterQueueLossByFallbackSweep() {
        // given — 큐에는 아무 작업도 없고 환불은 첫 재확인 시각(5분)을 넘겼다
        Flow flow = paidPaymentWithStock();
        paymentCancelProcessor.prepare(
            flow.memberId(), flow.paymentId(), "lost-queue-" + flow.paymentId(), CANCEL_REASON);
        Refund refund = refundsOf(flow).get(0);
        removeTask(PaymentDelayTask.refundReconcile(refund.getId()));
        moveRefundRequestedAt(refund.getId(), Duration.ofMinutes(6));
        String serverKey = SERVER_KEY_PREFIX + refund.getId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(paidWithoutCancellation(flow));
        given(portOnePaymentClient.cancelPayment(flow.portonePaymentId(), serverKey, CANCEL_REASON, AMOUNT))
            .willReturn(succeeded("fallback_" + refund.getId()));

        // when
        fallbackScheduler.sweep();
        assertThat(scoreOf(PaymentDelayTask.refundReconcile(refund.getId()))).isNotNull();
        pollOnly(PaymentDelayTask.refundReconcile(refund.getId()));

        // then
        assertThat(refundsOf(flow).get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        verify(portOnePaymentClient, times(1))
            .cancelPayment(flow.portonePaymentId(), serverKey, CANCEL_REASON, AMOUNT);
    }

    @Test
    @DisplayName("PortOne 조회가 실패하면 POST하지 않고 다시 예약만 한다 — 이전 POST가 닿았을 가능성을 열어 둔다")
    void noPostWhenLookupFails() {
        // given
        Flow flow = paidPaymentWithStock();
        paymentCancelProcessor.prepare(
            flow.memberId(), flow.paymentId(), "get-error-" + flow.paymentId(), CANCEL_REASON);
        Refund refund = refundsOf(flow).get(0);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        // when
        makeDueNow(PaymentDelayTask.refundReconcile(refund.getId()));
        pollOnly(PaymentDelayTask.refundReconcile(refund.getId()));

        // then — 상태는 그대로, 10분 뒤 재확인이 큐에 남는다
        assertThat(refundsOf(flow).get(0).getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        assertThat(scoreOf(PaymentDelayTask.refundReconcile(refund.getId()))).isNotNull();
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("요청 뒤 3시간이 지난 환불은 PortOne에 취소가 없어도 자동 POST하지 않는다 — 멱등 보장이 끝나 이중 취소가 될 수 있다")
    void noPostAfterIdempotencyWindow() {
        // given
        Flow flow = paidPaymentWithStock();
        paymentCancelProcessor.prepare(
            flow.memberId(), flow.paymentId(), "window-over-" + flow.paymentId(), CANCEL_REASON);
        Refund refund = refundsOf(flow).get(0);
        moveRefundRequestedAt(refund.getId(), Duration.ofHours(3));
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(paidWithoutCancellation(flow));

        // when
        makeDueNow(PaymentDelayTask.refundReconcile(refund.getId()));
        pollOnly(PaymentDelayTask.refundReconcile(refund.getId()));

        // then
        assertThat(refundsOf(flow).get(0).getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("회원의 같은 키 재요청과 Worker 복구가 동시에 돌아도 PortOne 키는 하나, 환불 완료 1회, 재고 복구 1회다")
    void memberReplayAndWorkerRecoverOnce() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        String clientKey = "concurrent-" + flow.paymentId();
        paymentCancelProcessor.prepare(flow.memberId(), flow.paymentId(), clientKey, CANCEL_REASON);
        Refund refund = refundsOf(flow).get(0);
        String serverKey = SERVER_KEY_PREFIX + refund.getId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(paidWithoutCancellation(flow));
        given(portOnePaymentClient.cancelPayment(flow.portonePaymentId(), serverKey, CANCEL_REASON, AMOUNT))
            .willAnswer(invocation -> succeeded("shared_" + refund.getId()));

        // when
        runConcurrently(List.<Callable<?>>of(
            () -> paymentCancelFacade.cancel(flow.memberId(), flow.paymentId(), clientKey, CANCEL_REASON),
            () -> refundReconciliationFacade.reconcile(refund.getId())));

        // then
        assertThat(refundsOf(flow)).hasSize(1);
        assertThat(refundsOf(flow).get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(portOnePaymentClient, atLeastOnce())
            .cancelPayment(eq(flow.portonePaymentId()), keys.capture(), eq(CANCEL_REASON), eq(AMOUNT));
        assertThat(keys.getAllValues()).allMatch(serverKey::equals);
    }

    // PG에는 결제가 PAID 그대로고 취소 내역이 없다. 누적 취소액 0은 PortOne 응답의 amount.cancelled다
    private PortOnePayment paidWithoutCancellation(Flow flow) {
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.PAID,
            AMOUNT, "KRW", flow.paidAt(), flow.orderId(), null, List.of(), STORE_ID, 0L);
    }

    private PortOneCancelResult succeeded(String cancellationId) {
        return new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, cancellationId,
            LocalDateTime.now(clock).withNano(0), "cancellation SUCCEEDED");
    }
}
