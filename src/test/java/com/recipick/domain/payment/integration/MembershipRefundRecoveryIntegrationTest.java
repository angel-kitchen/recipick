package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.MembershipRefundRecoveryFacade;
import com.recipick.domain.payment.facade.MembershipWebhookReconciliationFacade;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import com.recipick.domain.payment.scheduler.PaymentDelayQueueWorker;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

// 환불 복구 — DB 스캔(큐 유실 회복)·SYSTEM 환불 큐 예약·Worker 라우팅·보류 취소 웹훅 재개·탈퇴 회원의 기존 환불 정산. 실제 MySQL·Redis + PortOne 스텁
class MembershipRefundRecoveryIntegrationTest extends MembershipRefundFlowSupport {

    @Autowired MembershipRefundRecoveryFacade refundRecovery;
    @Autowired PaymentDelayQueue paymentDelayQueue;
    @Autowired PaymentDelayQueueWorker worker;
    @Autowired StringRedisTemplate redis;
    @Autowired PaymentReconciliationFacade reconciliation;
    @Autowired WebhookEventRepository webhookEvents;

    private final List<Long> eventIds = new ArrayList<>();

    @AfterEach
    void cleanUpQueueAndEvents() {
        refundsOfMember().forEach(r -> redis.opsForZSet().remove(PaymentDelayQueue.QUEUE_KEY,
            PaymentDelayTask.membershipRefundExecute(r.getId()).member()));
        eventIds.forEach(id -> {
            redis.opsForZSet().remove(PaymentDelayQueue.QUEUE_KEY, PaymentDelayTask.webhookProcess(id).member());
            webhookEvents.deleteById(id);
        });
    }

    @Test
    @DisplayName("큐 예약이 유실돼도 다음 확인 시각이 지난 REQUESTED 환불은 DB 스캔이 집어 완료한다. 임대 중·운영 표시·확정 행은 집지 않는다")
    void lostQueueEntryIsRecoveredByScan() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentServerError(paymentId);
        clock.fixAt(DAY_10);
        MembershipRefundResult pending = requestRefund(membershipId, newKey());
        redis.opsForZSet().remove(PaymentDelayQueue.QUEUE_KEY,
            PaymentDelayTask.membershipRefundExecute(pending.refundId()).member());
        PORTONE.clearGetStub(paymentId);

        // when
        clock.fixAt(DAY_10.plusSeconds(30));
        int beforeDue = refundRecovery.recoverOnce();
        clock.fixAt(DAY_10.plusMinutes(1));
        int due = refundRecovery.recoverOnce();
        int afterCompleted = refundRecovery.recoverOnce();

        // then
        assertThat(pending.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(beforeDue).isZero();
        assertThat(due).isEqualTo(1);
        assertThat(afterCompleted).isZero();
        assertThat(refund(pending.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plusMinutes(1));
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("접수는 큐에 환불 실행을 예약하고, Worker가 꺼내면 환불 실행기로 라우팅된다(동기 실행이 멈춘 경우의 두 번째 경로)")
    void acceptEnqueuesAndWorkerRoutesToRefundExecutor() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentServerError(paymentId);
        clock.fixAt(DAY_10);
        MembershipRefundResult pending = requestRefund(membershipId, newKey());
        Double score = redis.opsForZSet().score(PaymentDelayQueue.QUEUE_KEY,
            PaymentDelayTask.membershipRefundExecute(pending.refundId()).member());
        PORTONE.clearGetStub(paymentId);
        clock.fixAt(DAY_10.plusMinutes(6));

        // when — 다른 테스트가 남긴 작업이 큐에 함께 있을 수 있어 우리 작업이 빠질 때까지 poll한다(한 번에 20건)
        String member = PaymentDelayTask.membershipRefundExecute(pending.refundId()).member();
        for (int i = 0; i < 20 && redis.opsForZSet().score(PaymentDelayQueue.QUEUE_KEY, member) != null; i++) {
            worker.poll();
        }

        // then
        assertThat(score).isNotNull();
        assertThat(refund(pending.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(redis.opsForZSet().score(PaymentDelayQueue.QUEUE_KEY,
            PaymentDelayTask.membershipRefundExecute(pending.refundId()).member())).isNull();
    }

    @Test
    @DisplayName("결제 결과 반영이 SYSTEM 환불을 요청하면 커밋 뒤 큐에 실행을 예약하고, 큐가 유실돼도 스캔이 실행한다")
    void systemRefundIsScheduledAfterCommitAndRecoveredByScan() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, 3_800L, "KRW", RENEWAL_PAID_AT);
        clock.fixAt(T.plusMinutes(2));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId,
            MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
        MembershipRefund requested = refundsOfMember().get(0);
        Double score = redis.opsForZSet().score(PaymentDelayQueue.QUEUE_KEY,
            PaymentDelayTask.membershipRefundExecute(requested.getId()).member());
        redis.opsForZSet().remove(PaymentDelayQueue.QUEUE_KEY,
            PaymentDelayTask.membershipRefundExecute(requested.getId()).member());
        PORTONE.reset();
        PORTONE.simulateRefunds(paymentId, STORE_ID, 3_800L, RENEWAL_PAID_AT);
        clock.fixAt(T.plusMinutes(3));
        int recovered = refundRecovery.recoverOnce();

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(score).isNotNull();
        assertThat(recovered).isEqualTo(1);
        assertThat(refund(requested.getId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(PORTONE.cancelBodies(paymentId).get(0)).contains("\"amount\":3800").contains("\"requester\":\"ADMIN\"");
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("운영 표시(자동 중단)가 있는 REQUESTED 행과 강한 차단 행은 스캔이 실행하지 않는다")
    void haltedAndBlockedRowsAreSkippedByScan() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.cancelError(paymentId, 403, "FORBIDDEN");
        clock.fixAt(DAY_10);
        MembershipRefundResult halted = requestRefund(membershipId, newKey());
        PORTONE.clearCancelStub(paymentId);
        clock.fixAt(DAY_10.plusMinutes(10));

        // when
        int processed = refundRecovery.recoverOnce();

        // then
        assertThat(refund(halted.refundId()).getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.PG_AUTH_ERROR);
        assertThat(processed).isZero();
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("환불 실행 기능이 없던 때 '환불 정합화 대기'로 보존된 취소 웹훅만 재처리 대기로 되돌려 큐에 넣고, 다른 운영 확인 웹훅은 건드리지 않는다")
    void reopenPendingCancelWebhooks() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        WebhookEvent preserved = received("Transaction.PartialCancelled", paymentId);
        WebhookEvent other = received("Transaction.Cancelled", paymentId);
        jdbc.update("UPDATE webhook_event SET status = 'MANUAL_REVIEW', failure_reason = ?, finished_at = ? WHERE id = ?",
            MembershipWebhookReconciliationFacade.REASON_REFUND_RECONCILIATION_PENDING, DAY_10, preserved.getId());
        jdbc.update("UPDATE webhook_event SET status = 'MANUAL_REVIEW', failure_reason = 'OTHER', finished_at = ? WHERE id = ?",
            DAY_10, other.getId());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");

        // when
        int reopened = refundRecovery.reopenPendingCancelWebhooks();
        int again = refundRecovery.reopenPendingCancelWebhooks();
        WebhookEvent reopenedEvent = webhookEvents.findById(preserved.getId()).orElseThrow();
        Double score = redis.opsForZSet().score(PaymentDelayQueue.QUEUE_KEY,
            PaymentDelayTask.webhookProcess(preserved.getId()).member());
        WebhookProcessOutcome processed = reconciliation.processWebhook(preserved.getId());

        // then
        assertThat(reopened).isEqualTo(1);
        assertThat(again).isZero();
        assertThat(reopenedEvent.getStatus()).isEqualTo(WebhookEventStatus.RETRY_PENDING);
        assertThat(reopenedEvent.getFailureReason()).startsWith("REOPENED:");
        assertThat(score).isNotNull();
        assertThat(processed).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(webhookEvents.findById(preserved.getId()).orElseThrow().getStatus())
            .isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(webhookEvents.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(WebhookEventStatus.MANUAL_REVIEW);
        assertThat(refund(requested.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
    }

    @Test
    @DisplayName("탈퇴한 회원의 기존 환불(MEMBER REQUESTED)은 정산을 계속해 완료·종료까지 반영한다")
    void withdrawnMemberExistingRefundSettles() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        withdraw();
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        clock.fixAt(DAY_10.plusMinutes(6));

        // when
        int processed = refundRecovery.recoverOnce();

        // then
        assertThat(processed).isEqualTo(1);
        assertThat(refund(requested.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plusMinutes(6));
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    private WebhookEvent received(String type, String portonePaymentId) {
        WebhookEvent saved = webhookEvents.saveAndFlush(WebhookEvent.received("wh_" + UUID.randomUUID(), type,
            portonePaymentId, "{}"));
        eventIds.add(saved.getId());
        return saved;
    }
}
