package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.MartRejectionPreparation;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.MartRejectionProcessor;
import com.recipick.domain.payment.facade.PaymentCancelProcessor;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 마트 거절의 경합. HTTP가 아니라 TX1(MartRejectionProcessor.prepare)을 직접 동시에 불러 실제 MySQL 잠금으로 직렬화되는지 본다.
// 감사 기록은 요청 스레드의 인증 정보를 읽으므로 여기서는 Mock으로 바꾸고 호출 횟수만 센다 — 실제 기록은 흐름 통합 테스트가 검증한다.
// 진 쪽의 오류 코드는 스케줄에 따라 달라질 수 있어 고정하지 않고, 최종 DB 상태(환불 한 건·전이 한 번)를 강하게 본다
class MartRejectionConcurrencyTest extends PaymentFlowIntegrationSupport {

    private static final Long ADMIN_ID = 17L;
    private static final String REJECT_REASON = "상품 준비가 어렵습니다.";
    private static final String EXECUTE = "EXECUTE";
    private static final String DONE = "DONE";

    @MockitoBean
    AdminAuditRecorder adminAuditRecorder;

    @Autowired
    MartRejectionProcessor martRejectionProcessor;

    @Autowired
    PaymentCancelProcessor paymentCancelProcessor;

    @Autowired
    OrderService orderService;

    @Autowired
    PaymentReconciliationFacade paymentReconciliationFacade;

    @Test
    @DisplayName("같은 키의 거절 5건이 동시에 와도 ADMIN 환불·주문 거절 전이·감사 기록은 한 번이다 — 기다린 요청은 replay 또는 처리 중(COMMON-006)")
    void sameKeyFiveAtOnce() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        Long martId = orderOf(flow).getMartId();
        String key = "mart-reject-same-" + flow.orderId();
        List<Callable<Object>> tasks = IntStream.range(0, 5)
            .mapToObj(i -> (Callable<Object>) () -> prepare(flow, martId, key))
            .toList();

        // when
        List<Object> results = runConcurrently(tasks);

        // then
        assertThat(results).contains(EXECUTE);
        assertThat(results).isSubsetOf(EXECUTE, CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
        assertRejectedOnce(flow);
    }

    @Test
    @DisplayName("다른 키의 거절 5건이 동시에 오면 하나만 접수되고 나머지는 처리 중(COMMON-006)이다")
    void differentKeysFiveAtOnce() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        Long martId = orderOf(flow).getMartId();
        List<Callable<Object>> tasks = IntStream.range(0, 5)
            .mapToObj(i -> (Callable<Object>) () -> prepare(flow, martId, "mart-reject-" + i + "-" + flow.orderId()))
            .toList();

        // when
        List<Object> results = runConcurrently(tasks);

        // then
        assertThat(results).filteredOn(EXECUTE::equals).hasSize(1);
        assertThat(results).filteredOn(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS::equals).hasSize(4);
        assertRejectedOnce(flow);
    }

    @Test
    @DisplayName("마트 거절과 마트 수락이 동시에 오면 한쪽만 성공한다 — 거절과 배송 준비가 함께 남지 않는다")
    void rejectVersusAccept() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        Long martId = orderOf(flow).getMartId();
        Callable<Object> reject = () -> prepare(flow, martId, "mart-reject-accept-" + flow.orderId());
        Callable<Object> accept = () -> {
            try {
                orderService.acceptByMart(AdminRole.MART_ADMIN, martId, ADMIN_ID, flow.orderId());
                return DONE;
            } catch (BusinessException e) {
                return e.getErrorCode();
            }
        };

        // when
        List<Object> results = runConcurrently(List.of(reject, accept));

        // then — 정확히 한쪽만 성공했다
        boolean rejected = EXECUTE.equals(results.get(0));
        boolean accepted = DONE.equals(results.get(1));
        assertThat(rejected).isNotEqualTo(accepted);
        Order order = orderOf(flow);
        if (rejected) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
            assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
            assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
            assertThat(refundsOf(flow)).hasSize(1);
        } else {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getMartStatus()).isEqualTo(MartStatus.PREPARING);
            assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(refundsOf(flow)).isEmpty();
        }
    }

    @Test
    @DisplayName("마트 거절과 회원 취소가 동시에 오면 PAYMENT 잠금으로 직렬화돼 진행 중인 환불은 정확히 한 건이다")
    void rejectVersusMemberCancel() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        Long martId = orderOf(flow).getMartId();
        Callable<Object> reject = () -> prepare(flow, martId, "mart-reject-member-" + flow.orderId());
        Callable<Object> memberCancel = () -> {
            try {
                paymentCancelProcessor.prepare(flow.memberId(), flow.paymentId(), "member-cancel-" + flow.orderId(),
                    CANCEL_REASON);
                return DONE;
            } catch (BusinessException e) {
                return e.getErrorCode();
            }
        };

        // when
        List<Object> results = runConcurrently(List.of(reject, memberCancel));

        // then — 두 흐름이 모두 접수되지 않았고, 진행 중인 환불은 이긴 쪽의 한 건뿐이다
        boolean rejected = EXECUTE.equals(results.get(0));
        boolean canceled = DONE.equals(results.get(1));
        assertThat(rejected).isNotEqualTo(canceled);
        List<Refund> requested = refundsOf(flow).stream()
            .filter(refund -> refund.getStatus() == RefundStatus.REQUESTED)
            .toList();
        assertThat(requested).hasSize(1);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        if (rejected) {
            assertThat(requested.get(0).getRequestedBy()).isEqualTo(RefundRequester.ADMIN);
            assertThat(orderOf(flow).getMartStatus()).isEqualTo(MartStatus.REJECTED);
        } else {
            assertThat(requested.get(0).getRequestedBy()).isEqualTo(RefundRequester.MEMBER);
            assertThat(orderOf(flow).getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        }
    }

    @Test
    @DisplayName("같은 취소의 웹훅 두 건을 동시에 처리해도 둘 다 PROCESSED이고 ADMIN 환불 완료·재고 복구·환불 완료 이력은 한 번이다")
    void twoWebhooksForSameAdminCancellation() throws Exception {
        // given — 거절이 접수됐고 PortOne이 처리 중으로 답해 취소 ID만 기록된 ADMIN 환불
        Flow flow = paidPaymentWithStock();
        Long martId = orderOf(flow).getMartId();
        String cancellationId = "cancel_admin_" + flow.orderId();
        MartRejectionPreparation preparation = martRejectionProcessor.prepare(AdminRole.MART_ADMIN, martId, ADMIN_ID,
            flow.orderId(), "mart-reject-webhook-" + flow.orderId(), REJECT_REASON);
        martRejectionProcessor.apply(flow.paymentId(), preparation.refundId(), new PortOneCancelResult(
            PortOneCancelOutcome.REQUESTED, cancellationId, null, "cancellation REQUESTED"));
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(portOneWithCancellation(flow, cancellationId, PortOneCancellationStatus.SUCCEEDED));
        WebhookEvent first = receive("wh_admin_a_" + flow.orderId(), flow, cancellationId);
        WebhookEvent second = receive("wh_admin_b_" + flow.orderId(), flow, cancellationId);
        Callable<Object> processFirst = () -> paymentReconciliationFacade.processWebhook(first.getId());
        Callable<Object> processSecond = () -> paymentReconciliationFacade.processWebhook(second.getId());

        // when
        List<Object> results = runConcurrently(List.of(processFirst, processSecond));

        // then — 먼저 잠근 쪽이 완료하고, 늦은 쪽은 끝난 환불을 보고 그대로 끝낸다
        assertThat(results).containsOnly(WebhookProcessOutcome.PROCESSED);
        assertThat(reload(first).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(reload(second).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getRequestedBy()).isEqualTo(RefundRequester.ADMIN);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId()))
            .filteredOn(history -> OrderStatus.REFUNDED.name().equals(history.getToStatus()))
            .hasSize(1);
    }

    // ---------- helpers ----------

    // 예외는 Future.get이 그대로 던지므로 업무 오류는 오류 코드로 바꿔 돌려준다. 실행이 필요한 접수면 EXECUTE다
    private Object prepare(Flow flow, Long martId, String idempotencyKey) {
        try {
            MartRejectionPreparation preparation = martRejectionProcessor.prepare(AdminRole.MART_ADMIN, martId,
                ADMIN_ID, flow.orderId(), idempotencyKey, REJECT_REASON);
            return preparation.requiresExecution() ? EXECUTE : preparation.result().outcome();
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    // 거절이 정확히 한 번 접수된 상태. 주문 상태 이력은 주문·마트 상태 각 한 건이다
    private void assertRejectedOnce(Flow flow) {
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getRequestedBy()).isEqualTo(RefundRequester.ADMIN);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId())).hasSize(2);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        verify(adminAuditRecorder, times(1)).record(any(AdminAuditCommand.class));
    }

    // 실제 서명으로 수신해 행을 만들고, Worker가 꺼내 가지 않게 큐 작업은 지운다. 처리는 테스트가 직접 동시에 부른다
    private WebhookEvent receive(String webhookId, Flow flow, String cancellationId) throws Exception {
        postWebhook(webhookId, "Transaction.Cancelled", flow.portonePaymentId(), cancellationId)
            .andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        removeTask(PaymentDelayTask.webhookProcess(event.getId()));
        return event;
    }
}
