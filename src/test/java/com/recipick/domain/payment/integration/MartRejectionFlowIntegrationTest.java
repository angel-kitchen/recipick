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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 마트 주문 거절(#240)을 실제 MySQL·Redis·보안 필터·감사 로그 위에서 잇는다.
// 거절 API → TX1 → 실행기(GET-before-POST) → TX2, 그리고 Worker·웹훅이 같은 환불을 이어받아 한 번만 수렴하는지 본다.
// PortOne만 Mock이다. 감사 기록이 실제 관리자 행과 토큰을 대조하므로 Facade를 직접 부르지 않고 HTTP부터 탄다
class MartRejectionFlowIntegrationTest extends PaymentFlowIntegrationSupport {

    private static final String REJECT_REASON = "상품 준비가 어렵습니다.";
    private static final String REJECT_BODY = "{\"reason\":\"" + REJECT_REASON + "\"}";
    private static final String CANCELLED = "Transaction.Cancelled";

    @Autowired
    AdminRepository adminRepository;

    @Autowired
    JwtTokenProvider tokenProvider;

    private final List<Long> adminIds = new ArrayList<>();

    // 감사 로그가 마트를 참조하므로 부모의 마트 삭제보다 먼저 감사 로그 → 관리자 순으로 지운다
    @AfterEach
    void cleanUpAdmins() {
        for (Long adminId : adminIds) {
            jdbcTemplate.update("DELETE FROM admin_audit_log WHERE admin_id = ?", adminId);
        }
        adminRepository.deleteAllById(adminIds);
        adminIds.clear();
    }

    @Test
    @DisplayName("마트 관리자가 거절하면 200으로 결제 취소까지 끝나고 재고가 한 번 복구된다 — 남은 안전망 작업은 나중에 PortOne을 다시 부르지 않고 소비된다")
    void rejectAndRefund() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        String cancellationId = "cancel_admin_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(succeeded(cancellationId));

        // when
        reject(flow, martAdminToken(flow), "mart-reject-a-" + flow.orderId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentStatus").value("CANCELED"))
            .andExpect(jsonPath("$.data.refundStatus").value("COMPLETED"));

        // then
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MART_REJECTED);
        Refund refund = onlyRefund(flow);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.ADMIN);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getAmount()).isEqualTo(AMOUNT);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(orderRejectAuditCount(flow)).isEqualTo(1);
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(
            flow.portonePaymentId(), serverKey(refund), REJECT_REASON, AMOUNT);

        // when — 접수 직후 걸어 둔 10분 안전망은 정상 완료 뒤에도 남는다. 시각이 된 것으로 보고 돌린다
        PaymentDelayTask safetyNet = PaymentDelayTask.adminRefundExecute(refund.getId());
        assertThat(scoreOf(safetyNet)).isNotNull();
        makeDueNow(safetyNet);
        pollOnly(safetyNet);

        // then — 이미 끝난 환불이라 PortOne을 다시 묻지도 취소하지도 않고 작업만 소비된다
        assertThat(scoreOf(safetyNet)).isNull();
        assertThat(onlyRefund(flow).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        verify(portOnePaymentClient, times(1)).getPayment(flow.portonePaymentId());
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("PortOne이 처리 중으로 답하면 202로 거절만 접수되고, 같은 키 재요청은 같은 환불로 202이며, Worker 재실행이 완료로 수렴한다")
    void pendingThenSameKeyThenWorker() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        String cancellationId = "cancel_admin_" + flow.orderId();
        String key = "mart-reject-b-" + flow.orderId();
        String token = martAdminToken(flow);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(new PortOneCancelResult(PortOneCancelOutcome.REQUESTED, cancellationId, null,
                "cancellation REQUESTED"));

        // when
        reject(flow, token, key)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"));

        // then — 거절은 접수됐지만 돈과 재고는 아직 그대로다
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
        Refund refund = onlyRefund(flow);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        PaymentDelayTask task = PaymentDelayTask.adminRefundExecute(refund.getId());
        assertThat(scoreOf(task)).isNotNull();

        // when — 같은 키 재요청. PortOne은 아직 그 취소를 조회 결과에 보여 주지 않는다
        reject(flow, token, key)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.refundId").value(refund.getId()));

        // then — 새 환불·새 감사 로그·새 취소 요청이 없다
        assertThat(refundsOf(flow)).hasSize(1);
        assertThat(orderRejectAuditCount(flow)).isEqualTo(1);
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());

        // when — 10분이 지났고 PortOne에서 그 취소가 완료됐다
        given(portOnePaymentClient.getPayment(flow.portonePaymentId()))
            .willReturn(portOneWithCancellation(flow, cancellationId, PortOneCancellationStatus.SUCCEEDED));
        makeDueNow(task);
        pollOnly(task);

        // then
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(onlyRefund(flow).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(scoreOf(task)).isNull();
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(portOnePaymentClient, atLeastOnce())
            .cancelPaymentByAdmin(eq(flow.portonePaymentId()), keys.capture(), anyString(), anyLong());
        assertThat(keys.getAllValues()).containsOnly(serverKey(refund));
    }

    @Test
    @DisplayName("PortOne이 취소 실패를 명시하면 409 PAYMENT-009이고 결제만 PAID로 돌아간다 — 거절된 주문은 정상 접수로 되살아나지 않고 재고도 그대로다")
    void pgFailureKeepsOrderRejected() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(failed("cancel_admin_" + flow.orderId()));

        // when
        reject(flow, martAdminToken(flow), "mart-reject-c-" + flow.orderId())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT-009"));

        // then
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
        Refund refund = onlyRefund(flow);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.ADMIN);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.PG_FAILED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        assertThat(orderRejectAuditCount(flow)).isEqualTo(1);
    }

    @Test
    @DisplayName("완료된 거절을 같은 키로 다시 보내면 200으로 처음 결과를 돌려주고 환불·감사 로그·재고 복구·취소 요청이 늘지 않는다")
    void sameKeyAfterCompletion() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        String key = "mart-reject-d-" + flow.orderId();
        String token = martAdminToken(flow);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(succeeded("cancel_admin_" + flow.orderId()));
        reject(flow, token, key).andExpect(status().isOk());

        // when
        reject(flow, token, key)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.refundStatus").value("COMPLETED"));

        // then
        assertThat(refundsOf(flow)).hasSize(1);
        assertThat(orderRejectAuditCount(flow)).isEqualTo(1);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        verify(portOnePaymentClient, times(1)).getPayment(flow.portonePaymentId());
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("ADMIN 취소 POST 결과를 확인하지 못해도 재조회에서 기존 성공 취소를 찾아 추가 POST 없이 완료한다 — 과거 회원 취소의 실패 내역은 묶지 않는다")
    void recoverUnconfirmedPostByLookup() throws Exception {
        // given — 이 결제에는 PG가 실패로 끝낸 과거 회원 취소가 남아 있다
        Flow flow = paidPaymentWithStock();
        String oldCancellationId = "cancel_old_member_" + flow.orderId();
        String newCancellationId = "cancel_new_admin_" + flow.orderId();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Refund oldMemberRefund = failedMemberRefund(flow, oldCancellationId, now.minusMinutes(30));
        PortOneCancellation oldCancellation = new PortOneCancellation(oldCancellationId,
            PortOneCancellationStatus.FAILED, AMOUNT, now.minusMinutes(30), null, PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(new PortOnePayment(
            flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.PAID, AMOUNT, "KRW",
            flow.paidAt(), flow.orderId(), null, List.of(oldCancellation)));
        // POST는 PG에 닿았지만 응답을 받지 못했다
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(new PortOneCancelResult(PortOneCancelOutcome.UNCONFIRMED, null, null, "timeout"));

        // when
        reject(flow, martAdminToken(flow), "mart-reject-f-" + flow.orderId()).andExpect(status().isAccepted());

        // then — 결과를 모르니 ADMIN 환불은 취소 ID 없이 REQUESTED다
        Refund adminRefund = adminRefundOf(flow);
        assertThat(adminRefund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(adminRefund.getPortoneCancellationId()).isNull();

        // when — 재조회하면 실패한 과거 취소와 이 환불 이후에 요청된 성공 취소가 함께 보인다
        PortOneCancellation newCancellation = new PortOneCancellation(newCancellationId,
            PortOneCancellationStatus.SUCCEEDED, AMOUNT, adminRefund.getRequestedAt(), now,
            PortOneCancellationTrigger.API);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(new PortOnePayment(
            flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.CANCELED, AMOUNT, "KRW",
            flow.paidAt(), flow.orderId(), null, List.of(oldCancellation, newCancellation)));
        PaymentDelayTask task = PaymentDelayTask.adminRefundExecute(adminRefund.getId());
        makeDueNow(task);
        pollOnly(task);

        // then — 새 취소만 이 환불에 묶였고 과거 회원 환불은 그대로다
        Refund completed = adminRefundOf(flow);
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getPortoneCancellationId()).isEqualTo(newCancellationId);
        Refund oldAfter = refundRepository.findById(oldMemberRefund.getId()).orElseThrow();
        assertThat(oldAfter.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(oldAfter.getPortoneCancellationId()).isEqualTo(oldCancellationId);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("ADMIN 환불이 실패한 거절 주문을 운영자가 콘솔에서 전액 취소하면 웹훅이 주문을 닫고 재고를 한 번 복구한다 — 같은 취소의 재전송은 아무것도 늘리지 않는다")
    void consoleCancellationClosesFailedRejection() throws Exception {
        // given — PG 취소 실패로 결제만 PAID로 돌아간 거절 주문
        Flow flow = paidPaymentWithStock();
        String failedCancellationId = "cancel_admin_" + flow.orderId();
        String consoleCancellationId = "cancel_console_" + flow.orderId();
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        given(portOnePaymentClient.cancelPaymentByAdmin(
            eq(flow.portonePaymentId()), anyString(), anyString(), anyLong()))
            .willReturn(failed(failedCancellationId));
        reject(flow, martAdminToken(flow), "mart-reject-g-" + flow.orderId()).andExpect(status().isConflict());
        Refund failedAdmin = onlyRefund(flow);

        // 운영자가 콘솔에서 전액 취소했다. 조회 결과에는 실패한 API 취소와 성공한 콘솔 취소가 함께 있다
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(new PortOnePayment(
            flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.CANCELED, AMOUNT, "KRW",
            flow.paidAt(), flow.orderId(), null, List.of(
            new PortOneCancellation(failedCancellationId, PortOneCancellationStatus.FAILED, AMOUNT,
                now.minusMinutes(5), null, PortOneCancellationTrigger.API),
            new PortOneCancellation(consoleCancellationId, PortOneCancellationStatus.SUCCEEDED, AMOUNT,
                now.minusMinutes(2), now.minusMinutes(1), PortOneCancellationTrigger.CONSOLE))));

        // when
        WebhookEvent first = receiveAndProcess("wh_admin_console_1_" + flow.orderId(), flow, consoleCancellationId);

        // then
        assertThat(reload(first).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertConsoleClosed(flow, failedAdmin, consoleCancellationId);

        // when — 같은 취소를 다른 웹훅으로 다시 받는다
        WebhookEvent second = receiveAndProcess("wh_admin_console_2_" + flow.orderId(), flow, consoleCancellationId);

        // then — 이미 반영된 취소라 환불·재고·이력이 그대로다
        assertThat(reload(second).getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertConsoleClosed(flow, failedAdmin, consoleCancellationId);
        verify(portOnePaymentClient, times(1)).cancelPaymentByAdmin(anyString(), anyString(), anyString(), anyLong());
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("다른 마트의 관리자가 거절하면 403 AUTH-005이고 결제·주문·환불·감사 로그가 그대로이며 PortOne을 부르지 않는다")
    void rejectOtherMartOrder() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        Long otherMartId = orderOf(flow).getMartId() + 1_000_000L;

        // when
        reject(flow, token(saveMartAdmin(otherMartId)), "mart-reject-h-" + flow.orderId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH-005"));

        // then
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        assertThat(refundsOf(flow)).isEmpty();
        assertThat(orderRejectAuditCount(flow)).isZero();
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
        verifyNoInteractions(portOnePaymentClient);
    }

    // ---------- helpers ----------

    private ResultActions reject(Flow flow, String token, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/{orderId}/reject", flow.orderId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(REJECT_BODY));
    }

    // 감사 기록이 실제 관리자 행의 역할·마트를 토큰과 대조하므로 DB에 저장한 관리자로 토큰을 만든다
    private String martAdminToken(Flow flow) {
        return token(saveMartAdmin(orderOf(flow).getMartId()));
    }

    private Admin saveMartAdmin(Long martId) {
        Admin admin = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .passwordHash("hash")
            .role(AdminRole.MART_ADMIN)
            .martId(martId)
            .active(true)
            .build());
        adminIds.add(admin.getId());
        return admin;
    }

    private String token(Admin admin) {
        return tokenProvider.createTokens(new AuthPrincipal(SubjectType.ADMIN, admin.getId(), admin.getRole().name(),
            admin.getMartId())).accessToken();
    }

    // 회원 취소가 PG 실패로 끝난 과거 시도. 실제 전이 메서드로 결제를 PAID로 되돌린 상태까지 만든다
    private Refund failedMemberRefund(Flow flow, String cancellationId, LocalDateTime requestedAt) {
        return transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findById(flow.paymentId()).orElseThrow();
            payment.requestCancel();
            Refund refund = Refund.requestedByMember(payment, CANCEL_REASON, "member-key-" + flow.orderId(),
                requestedAt);
            refund.fail(cancellationId, RefundFailureType.PG_FAILED, "cancellation FAILED");
            payment.restorePaid();
            return refundRepository.save(refund);
        });
    }

    // 실제 서명으로 웹훅을 받고 Worker로 한 번 처리한다
    private WebhookEvent receiveAndProcess(String webhookId, Flow flow, String cancellationId) throws Exception {
        postWebhook(webhookId, CANCELLED, flow.portonePaymentId(), cancellationId).andExpect(status().isOk());
        WebhookEvent event = eventsOf(webhookId).get(0);
        pollOnly(PaymentDelayTask.webhookProcess(event.getId()));
        return event;
    }

    private void assertConsoleClosed(Flow flow, Refund failedAdmin, String consoleCancellationId) {
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        Order order = orderOf(flow);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(2);
        Refund admin = refunds.stream().filter(refund -> refund.getId().equals(failedAdmin.getId())).findFirst()
            .orElseThrow();
        assertThat(admin.getRequestedBy()).isEqualTo(RefundRequester.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(RefundStatus.FAILED);
        Refund external = refunds.stream().filter(refund -> !refund.getId().equals(failedAdmin.getId())).findFirst()
            .orElseThrow();
        assertThat(external.getRequestedBy()).isEqualTo(RefundRequester.EXTERNAL);
        assertThat(external.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(external.getPortoneCancellationId()).isEqualTo(consoleCancellationId);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId()))
            .filteredOn(history -> OrderStatus.REFUNDED.name().equals(history.getToStatus()))
            .hasSize(1);
    }

    private Refund onlyRefund(Flow flow) {
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).hasSize(1);
        return refunds.get(0);
    }

    private Refund adminRefundOf(Flow flow) {
        List<Refund> admins = refundsOf(flow).stream()
            .filter(refund -> refund.getRequestedBy() == RefundRequester.ADMIN)
            .toList();
        assertThat(admins).hasSize(1);
        return admins.get(0);
    }

    private long orderRejectAuditCount(Flow flow) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_log WHERE action = 'ORDER_REJECT' AND target_id = ?",
            Long.class, flow.orderId());
        return count == null ? 0 : count;
    }

    private PortOneCancelResult succeeded(String cancellationId) {
        return new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, cancellationId,
            LocalDateTime.now(clock).withNano(0), "cancellation SUCCEEDED");
    }

    private static PortOneCancelResult failed(String cancellationId) {
        return new PortOneCancelResult(PortOneCancelOutcome.FAILED, cancellationId, null, "cancellation FAILED");
    }

    // 실행기와 같은 규칙. 환불 행 하나가 PortOne 취소 작업 하나다
    private static String serverKey(Refund refund) {
        return "recipick-refund-" + refund.getId();
    }
}
