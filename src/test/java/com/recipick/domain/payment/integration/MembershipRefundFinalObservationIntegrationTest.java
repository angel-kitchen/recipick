package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 이미 확정된(COMPLETED) 환불에 다시 들어온 PG 관측. 식별값·취소 ID를 믿을 수 없으면 확정 결과를 그대로 인정하지 않고,
// 저장된 환불 상태·이용권 종료 기록은 바꾸지 않는다. 실제 MySQL + PortOne 스텁
class MembershipRefundFinalObservationIntegrationTest extends MembershipRefundFlowSupport {

    @Autowired PaymentReconciliationFacade reconciliation;
    @Autowired WebhookEventRepository webhookEvents;

    private final List<Long> eventIds = new ArrayList<>();

    @AfterEach
    void cleanUpWebhooks() {
        eventIds.forEach(webhookEvents::deleteById);
    }

    @Test
    @DisplayName("확정 환불 재관측에서 같은 취소 ID가 두 번 오면 판정하지 않고 웹훅 재시도 대기로 보존한다")
    void finalizedRefundWithDuplicateCancellationIdRetries() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        clock.fixAt(DAY_10);
        MembershipRefundResult completed = requestRefund(membershipId, newKey());
        assertThat(completed.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        MembershipRefund saved = refund(completed.refundId());
        long amount = saved.getAmount();
        String id = saved.getPortoneCancellationId();
        String cancellations = "[" + PortOneStubServer.cancellationJson(id, "SUCCEEDED", amount,
            saved.getPgRequestReason(), "API", null, null) + ","
            + PortOneStubServer.cancellationJson(id, "FAILED", amount, saved.getPgRequestReason(), "API", null, null)
            + "]";
        PORTONE.getPaymentPaidWithCancellations(paymentId, STORE_ID, FEE, RENEWAL_PAID_AT, amount, cancellations);
        int postsBefore = PORTONE.cancelCount(paymentId);

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(
            received("Transaction.PartialCancelled", paymentId).getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        MembershipRefund after = refund(completed.refundId());
        assertThat(after.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(after.getOpsReviewReason()).isNull();
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(postsBefore);
    }

    @Test
    @DisplayName("확정 환불 재관측의 상점이 저장 근거와 다르면 완료로 기록하지 않고 환불·결제 시도를 강하게 차단한다")
    void finalizedRefundWithStoreMismatchBlocksSettlement() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        Long attemptId = appliedAttemptId(membershipId);
        clock.fixAt(DAY_10);
        MembershipRefundResult completed = requestRefund(membershipId, newKey());
        MembershipRefund saved = refund(completed.refundId());
        long amount = saved.getAmount();
        String cancellations = "[" + PortOneStubServer.cancellationJson(saved.getPortoneCancellationId(), "SUCCEEDED",
            amount, saved.getPgRequestReason(), "API", null, null) + "]";
        PORTONE.getPaymentPaidWithCancellations(paymentId, "other-store-id", FEE, RENEWAL_PAID_AT, amount,
            cancellations);
        int postsBefore = PORTONE.cancelCount(paymentId);

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(
            received("Transaction.PartialCancelled", paymentId).getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        MembershipRefund after = refund(completed.refundId());
        assertThat(after.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(after.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.PG_IDENTITY_MISMATCH);
        assertThat(attemptOpsReason(attemptId)).isEqualTo(MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT.name());
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(postsBefore);
    }

    @Test
    @DisplayName("확정 환불 재관측에 상점 ID가 비어 있으면 이번 관측만 미확정으로 보고 저장된 확정 상태는 그대로 둔다")
    void finalizedRefundWithIncompleteIdentityStaysPending() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        Long attemptId = appliedAttemptId(membershipId);
        clock.fixAt(DAY_10);
        MembershipRefundResult completed = requestRefund(membershipId, newKey());
        MembershipRefund saved = refund(completed.refundId());
        long amount = saved.getAmount();
        String cancellations = "[" + PortOneStubServer.cancellationJson(saved.getPortoneCancellationId(), "SUCCEEDED",
            amount, saved.getPgRequestReason(), "API", null, null) + "]";
        PORTONE.getPaymentPaidWithCancellations(paymentId, "", FEE, RENEWAL_PAID_AT, amount, cancellations);

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(
            received("Transaction.PartialCancelled", paymentId).getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        MembershipRefund after = refund(completed.refundId());
        assertThat(after.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(after.getOpsReviewReason()).isNull();
        assertThat(attemptOpsReason(attemptId)).isNull();
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
    }

    private WebhookEvent received(String type, String portonePaymentId) {
        WebhookEvent saved = webhookEvents.saveAndFlush(WebhookEvent.received("wh_" + UUID.randomUUID(), type,
            portonePaymentId, "{}"));
        eventIds.add(saved.getId());
        return saved;
    }
}
