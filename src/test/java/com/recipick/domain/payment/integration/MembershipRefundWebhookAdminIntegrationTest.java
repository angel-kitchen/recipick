
package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 취소 웹훅의 환불 정합화·늦은 Paid 웹훅(환불 결과별 처리 기록)·운영 상세의 환불 이력·운영 재조회·SYSTEM 재실행 API·권한·감사 원자성.
// 실제 MySQL + PortOne 스텁
class MembershipRefundWebhookAdminIntegrationTest extends MembershipRefundFlowSupport {

    @Autowired PaymentReconciliationFacade reconciliation;
    @Autowired WebhookEventRepository webhookEvents;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired AdminRepository admins;

    private final List<Long> eventIds = new ArrayList<>();
    private Long superAdminId;

    @BeforeEach
    void setUpAdmin() {
        superAdminId = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).name("결제 운영")
            .passwordHash("hash").role(AdminRole.SUPER_ADMIN).active(true).build()).getId();
    }

    @AfterEach
    void cleanUpWebhookAndAdmin() {
        eventIds.forEach(webhookEvents::deleteById);
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", superAdminId);
        admins.deleteById(superAdminId);
    }

    // ---------- 취소 웹훅 ----------

    @Test
    @DisplayName("취소 완료 웹훅은 GET 1회로 REQUESTED 환불을 완료·종료하고 처리 완료로 기록한다. 같은 웹훅이 또 와도 변경 없이 처리 완료")
    void cancelWebhookCompletesRequestedRefundOnce() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        int getsBefore = PORTONE.getCount(paymentId);
        clock.fixAt(DAY_10.plusMinutes(1));

        // when
        WebhookProcessOutcome first = reconciliation.processWebhook(received("Transaction.PartialCancelled", paymentId)
            .getId());
        WebhookProcessOutcome again = reconciliation.processWebhook(received("Transaction.PartialCancelled", paymentId)
            .getId());

        // then
        assertThat(requested.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(first).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(again).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore + 2);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(refund(requested.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plusMinutes(1));
        assertThat(attempt(appliedAttemptId(membershipId)).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
    }

    @Test
    @DisplayName("취소 처리 중 웹훅(CancelPending)에 PG도 처리 중이면 재시도 대기, PG 조회가 실패하면 재시도 대기다")
    void cancelPendingWebhookRetries() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());

        // when
        WebhookProcessOutcome pending = reconciliation.processWebhook(received("Transaction.CancelPending", paymentId)
            .getId());
        PORTONE.getPaymentServerError(paymentId);
        WebhookProcessOutcome lookupFailed = reconciliation.processWebhook(received("Transaction.Cancelled", paymentId)
            .getId());

        // then
        assertThat(pending).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        assertThat(lookupFailed).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        assertThat(refund(requested.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    // CodeRabbit #388 회귀 검증: 확정 환불의 취소 ID 누락·빈 문자열 관측
    @Test
    @DisplayName("확정 환불 재관측에서 취소 ID가 누락돼도 예외 없이 웹훅 재시도 대기로 보존한다")
    void finalizedRefundWithMissingCancellationIdRetries() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);

        clock.fixAt(DAY_10);
        MembershipRefundResult completed = requestRefund(membershipId, newKey());

        assertThat(completed.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);

        MembershipRefund saved = refund(completed.refundId());
        String reason = saved.getPgRequestReason();
        long amount = saved.getAmount();
        int postsBefore = PORTONE.cancelCount(paymentId);

        // when: 취소 ID가 null인 경우와 빈 문자열인 경우를 각각 재현
        for (String rawId : new String[] {"null", "\"\""}) {
            String cancellationsJson = "[{\"id\":" + rawId
                + ",\"status\":\"SUCCEEDED\",\"totalAmount\":" + amount
                + ",\"reason\":\"" + reason + "\",\"trigger\":\"API\"}]";

            PORTONE.getPaymentPaidWithCancellations(
                paymentId, STORE_ID, FEE, RENEWAL_PAID_AT, amount, cancellationsJson);

            WebhookEvent event = received("Transaction.PartialCancelled", paymentId);
            WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());

            // then: 불신뢰 관측은 재시도 대상으로 보존
            assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);

            // 이미 완료된 환불 및 이용권 종료 기록은 불변
            assertThat(refund(completed.refundId()).getStatus())
                .isEqualTo(MembershipRefundStatus.COMPLETED);
            assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);

            // 웹훅 재조회 과정에서 PG 취소 POST가 다시 발생하지 않음
            assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(postsBefore);
        }
    }

    @Test
    @DisplayName("환불 뒤 늦게 도착한 Paid 웹훅은 PG의 부분 취소 상태를 환불 정합화로 설명하고 수납 불신뢰(운영 확인)로 보내지 않는다")
    void latePaidWebhookAfterRefundIsExplainedByRefund() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        Long attemptId = appliedAttemptId(membershipId);
        clock.fixAt(DAY_10);
        MembershipRefundResult completed = requestRefund(membershipId, newKey());
        int getsBefore = PORTONE.getCount(paymentId);

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(received("Transaction.Paid", paymentId).getId());
        MembershipChargeOutcome recheck = executor.reconcile(member.getId(), attemptId,
            MembershipReconciliationTrigger.VERIFIED_WEBHOOK);

        // then
        assertThat(completed.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(recheck).isEqualTo(MembershipChargeOutcome.REFUND_RECONCILED);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore + 2);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt(attemptId).getOpsReviewReason()).isNull();
        assertThat(refund(completed.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("GET 전 투영이 UNKNOWN이던 시도라도 잠금 아래에서 PAID이고 PG가 부분 취소면 환불 정합화로 라우팅한다(운영 확인 아님)")
    void unknownProjectionThenPaidWithPartialCancelRoutesToRefund() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        Long attemptId = appliedAttemptId(membershipId);
        clock.fixAt(DAY_10);
        requestRefund(membershipId, newKey());
        // 같은 결제의 재조회가 "결제 상태 미확정" 투영으로 시작했더라도 TX2는 잠금 아래의 최신 시도(PAID)로 판정한다
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId,
            MembershipReconciliationTrigger.ADMIN_RECHECK);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.REFUND_RECONCILED);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt(attemptId).getOpsReviewReason()).isNull();
    }

    @Test
    @DisplayName("환불 요청이 없는 시도의 취소 웹훅은 외부 취소로 운영 확인에 남고 PG 조회를 하지 않는다")
    void cancelWebhookWithoutRefundIsManualReview() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        int getsBefore = PORTONE.getCount(paymentId);

        // when
        WebhookEvent event = received("Transaction.Cancelled", paymentId);
        WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        assertThat(webhookEvents.findById(event.getId()).orElseThrow().getFailureReason())
            .isEqualTo("EXTERNAL_CANCELLATION_UNEXPLAINED");
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore);
    }

    // ---------- Paid 웹훅의 환불 결과별 처리 ----------

    @Test
    @DisplayName("Paid 웹훅인데 PG가 부분 취소 상태이고 환불 행이 하나도 없으면 외부 취소로 운영 확인에 남기고 처리 완료로 기록하지 않는다")
    void paidWebhookWithExternalCancelAndNoRefundIsManualReview() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        Long attemptId = appliedAttemptId(membershipId);
        PORTONE.addSimulatedCancellation(paymentId, "SUCCEEDED", 1_000L, "console partial", DAY_10);
        clock.fixAt(DAY_10);

        // when
        WebhookEvent event = received("Transaction.Paid", paymentId);
        WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());
        MembershipChargeOutcome recheck = executor.reconcile(member.getId(), attemptId,
            MembershipReconciliationTrigger.VERIFIED_WEBHOOK);

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        assertThat(webhookEvents.findById(event.getId()).orElseThrow().getFailureReason())
            .isEqualTo("REFUND_RECONCILIATION_REVIEW_REQUIRED");
        assertThat(recheck).isEqualTo(MembershipChargeOutcome.REFUND_RECONCILIATION_REVIEW_REQUIRED);
        assertThat(refundsOfMember()).isEmpty();
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt(attemptId).getOpsReviewReason()).isNull();
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("Paid 웹훅에서 환불이 미확정(취소 POST 결과 불명 + 설명되지 않는 외부 취소)이면 재시도 대기로 기록하고 POST를 보내지 않는다")
    void paidWebhookWithPendingRefundRetries() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.cancelError(paymentId, 500, "INTERNAL_SERVER_ERROR");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        PORTONE.clearCancelStub(paymentId);
        PORTONE.addSimulatedCancellation(paymentId, "SUCCEEDED", 1_000L, "console partial", DAY_10);
        clock.fixAt(DAY_10.plusSeconds(30));

        // when
        WebhookEvent event = received("Transaction.Paid", paymentId);
        WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());

        // then
        assertThat(requested.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        MembershipRefund still = refund(requested.refundId());
        assertThat(still.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(still.getOpsReviewReason()).isNull();
        assertThat(attempt(appliedAttemptId(membershipId)).getOpsReviewReason()).isNull();
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Paid 웹훅에서 환불 근거와 PG 취소가 모순(같은 reason·다른 금액)이면 환불·시도를 차단하고 운영 확인으로 기록한다")
    void paidWebhookWithRefundEvidenceConflictIsManualReview() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        Long attemptId = appliedAttemptId(membershipId);
        PORTONE.cancelError(paymentId, 500, "INTERNAL_SERVER_ERROR");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        PORTONE.clearCancelStub(paymentId);
        PORTONE.addSimulatedCancellation(paymentId, "SUCCEEDED", 1_000L, refund(requested.refundId()).getPgRequestReason(),
            DAY_10, "API");
        clock.fixAt(DAY_10.plusSeconds(30));

        // when
        WebhookEvent event = received("Transaction.Paid", paymentId);
        WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        assertThat(webhookEvents.findById(event.getId()).orElseThrow().getFailureReason())
            .isEqualTo("REFUND_RECONCILIATION_REVIEW_REQUIRED");
        MembershipRefund blocked = refund(requested.refundId());
        assertThat(blocked.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(blocked.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.EVIDENCE_CONFLICT);
        assertThat(attemptOpsReason(attemptId)).isEqualTo(MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT.name());
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("FAILED 환불 이력이 있어도 새 REQUESTED 환불이 미확정이면 취소 웹훅은 처리 완료가 아니라 재시도 대기다")
    void cancelWebhookWithFailedHistoryAndNewRequestedRefundRetries() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, 3_800L, "KRW", RENEWAL_PAID_AT);
        clock.fixAt(T.plusMinutes(2));
        executor.reconcile(member.getId(), attemptId, MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
        Long failedId = refundsOfMember().get(0).getId();
        PORTONE.reset();
        PORTONE.simulateRefunds(paymentId, STORE_ID, 3_800L, RENEWAL_PAID_AT);
        PORTONE.simulatePostStatus(paymentId, "FAILED");
        clock.fixAt(T.plusMinutes(3));
        refundExecution.execute(failedId);
        assertThat(refund(failedId).getStatus()).isEqualTo(MembershipRefundStatus.FAILED);
        clock.fixAt(T.plusMinutes(4));
        Long retryId = refundRequests.acceptSystemRetry(member.getId(), failedId).refundId();

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(received("Transaction.CancelPending", paymentId)
            .getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        assertThat(refund(retryId).getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund(failedId).getStatus()).isEqualTo(MembershipRefundStatus.FAILED);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    // ---------- 운영 API ----------

    @Test
    @DisplayName("운영 상세는 기존 systemRefunds를 유지하면서 MEMBER·SYSTEM 전체 환불 이력(refunds)을 함께 준다")
    void attemptDetailShowsAllRefunds() throws Exception {
        // given
        Long membershipId = renewedMembership();
        Long attemptId = appliedAttemptId(membershipId);
        clock.fixAt(DAY_10);
        MembershipRefundResult completed = requestRefund(membershipId, newKey());

        // when
        MockHttpServletResponse detail = mockMvc.perform(get("/api/admin/membership-payment-attempts/" + attemptId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
            .andReturn().getResponse();

        // then
        assertThat(detail.getStatus()).isEqualTo(200);
        String body = detail.getContentAsString();
        assertThat(body).contains("\"systemRefunds\":[]");
        assertThat(body).contains("\"refunds\":[{\"refundId\":" + completed.refundId())
            .contains("\"requestedBy\":\"MEMBER\"").contains("\"periodDays\":31").contains("\"usedDays\":10")
            .contains("\"portoneCancellationId\":\"cxl_1\"").contains("\"reviewRequired\":false");
        assertThat(body).doesNotContain(member.getEmail()).doesNotContain(cardA.getPortoneBillingKey());
    }

    @Test
    @DisplayName("운영 재조회 API는 감사 1건을 커밋한 뒤 GET 1회로 관측값과 최신 상태를 돌려주고, 검증된 성공은 토큰 없이 완료로 반영한다")
    void adminRecheckRecordsAuditAndReturnsObservation() throws Exception {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        int getsBefore = PORTONE.getCount(paymentId);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/membership-refunds/"
                + requested.refundId() + "/recheck").header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
            .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        String body = response.getContentAsString();
        assertThat(body).contains("\"outcome\":\"COMPLETED\"").contains("\"status\":\"COMPLETED\"")
            .contains("\"pgLookedUp\":true").contains("\"pgCancellationId\":\"cxl_1\"")
            .contains("\"pgCancellationStatus\":\"SUCCEEDED\"").contains("\"pgCancellationAmount\":1862")
            .contains("\"reviewRequired\":false");
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore + 1);
        assertThat(auditActions()).containsExactly("MEMBERSHIP_REFUND_RECHECK");
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
    }

    @Test
    @DisplayName("재조회 감사 저장이 실패하면 PG 조회 없이 끝난다")
    void recheckAuditFailureStopsBeforePg() throws Exception {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentServerError(paymentId);
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        int getsBefore = PORTONE.getCount(paymentId);
        jdbc.execute("ALTER TABLE admin_audit_log ADD CONSTRAINT ck_refund_audit_test CHECK (action <> "
            + "'MEMBERSHIP_REFUND_RECHECK' AND action <> 'MEMBERSHIP_REFUND_RETRY')");
        try {
            // when
            MockHttpServletResponse recheck = mockMvc.perform(post("/api/admin/membership-refunds/"
                    + requested.refundId() + "/recheck")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
                .andReturn().getResponse();

            // then
            assertThat(recheck.getStatus()).isEqualTo(500);
        } finally {
            jdbc.execute("ALTER TABLE admin_audit_log DROP CHECK ck_refund_audit_test");
        }
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore);
        assertThat(refundsOfMember()).hasSize(1);
        assertThat(auditActions()).isEmpty();
    }

    @Test
    @DisplayName("재실행 감사 저장이 실패하면 후속 환불 행까지 롤백되고 PG POST가 없으며, 감사가 가능해진 뒤의 재실행은 정상 접수된다")
    void retryAuditFailureRollsBackFollowUpRefund() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, 3_800L, "KRW", RENEWAL_PAID_AT);
        clock.fixAt(T.plusMinutes(2));
        executor.reconcile(member.getId(), attemptId, MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
        Long failedId = refundsOfMember().get(0).getId();
        PORTONE.reset();
        PORTONE.simulateRefunds(paymentId, STORE_ID, 3_800L, RENEWAL_PAID_AT);
        PORTONE.simulatePostStatus(paymentId, "FAILED");
        clock.fixAt(T.plusMinutes(3));
        refundExecution.execute(failedId);
        assertThat(refund(failedId).getStatus()).isEqualTo(MembershipRefundStatus.FAILED);
        PORTONE.simulatePostStatus(paymentId, "SUCCEEDED");
        clock.fixAt(T.plusMinutes(4));
        jdbc.execute("ALTER TABLE admin_audit_log ADD CONSTRAINT ck_refund_audit_test CHECK (action <> "
            + "'MEMBERSHIP_REFUND_RETRY')");
        MockHttpServletResponse failed;
        try {
            // when
            failed = mockMvc.perform(post("/api/admin/membership-refunds/" + failedId + "/retry")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
                .andReturn().getResponse();
        } finally {
            jdbc.execute("ALTER TABLE admin_audit_log DROP CHECK ck_refund_audit_test");
        }
        MockHttpServletResponse recovered = mockMvc.perform(post("/api/admin/membership-refunds/" + failedId + "/retry")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
            .andReturn().getResponse();

        // then
        assertThat(failed.getStatus()).isEqualTo(500);
        assertThat(recovered.getStatus()).isEqualTo(200);
        List<MembershipRefund> rows = refundsOfMember();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getRetryOfRefundId()).isEqualTo(failedId);
        assertThat(rows.get(1).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(recovered.getContentAsString()).contains("\"existing\":false");
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(2);
        assertThat(auditActions()).containsExactly("MEMBERSHIP_REFUND_RETRY");
    }

    @Test
    @DisplayName("회원 토큰·MART_ADMIN 토큰은 환불 운영 API를 부를 수 없고(403) PG 조회·감사가 없다")
    void memberAndMartAdminCannotCallRefundAdminApi() throws Exception {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);
        MembershipRefundResult completed = requestRefund(membershipId, newKey());
        int getsBefore = PORTONE.totalGetCount();
        String memberToken = testAdminTokens(new AuthPrincipal(SubjectType.MEMBER, member.getId(), null, null))
            .accessToken();
        Long martAdminId = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).name("마트 운영")
            .passwordHash("hash").role(AdminRole.MART_ADMIN).martId(1L).active(true).build()).getId();
        String martAdminToken = testAdminTokens(new AuthPrincipal(SubjectType.ADMIN, martAdminId, "MART_ADMIN", 1L))
            .accessToken();

        try {
            // when
            MockHttpServletResponse byMember = mockMvc.perform(post("/api/admin/membership-refunds/"
                    + completed.refundId() + "/recheck").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andReturn().getResponse();
            MockHttpServletResponse byMartAdmin = mockMvc.perform(post("/api/admin/membership-refunds/"
                    + completed.refundId() + "/retry").header(HttpHeaders.AUTHORIZATION, "Bearer " + martAdminToken))
                .andReturn().getResponse();

            // then
            assertThat(byMember.getStatus()).isEqualTo(403);
            assertThat(byMartAdmin.getStatus()).isEqualTo(403);
            assertThat(PORTONE.totalGetCount()).isEqualTo(getsBefore);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ?", Long.class,
                martAdminId)).isZero();
        } finally {
            jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", martAdminId);
            admins.deleteById(martAdminId);
        }
    }

    @Test
    @DisplayName("SYSTEM FAILED 환불의 재실행 API는 후속 행 하나를 만들어 실행하고 신규 실행 감사 1건만 남기며, 다시 눌러도 같은 후속 행을 감사 없이 돌려준다")
    void retryIsIdempotentPerFailedRefund() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, 3_800L, "KRW", RENEWAL_PAID_AT);
        clock.fixAt(T.plusMinutes(2));
        executor.reconcile(member.getId(), attemptId, MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
        Long failedId = refundsOfMember().get(0).getId();
        PORTONE.reset();
        PORTONE.simulateRefunds(paymentId, STORE_ID, 3_800L, RENEWAL_PAID_AT);
        PORTONE.simulatePostStatus(paymentId, "FAILED");
        clock.fixAt(T.plusMinutes(3));
        refundExecution.execute(failedId);
        assertThat(refund(failedId).getStatus()).isEqualTo(MembershipRefundStatus.FAILED);
        PORTONE.simulatePostStatus(paymentId, "SUCCEEDED");
        clock.fixAt(T.plusMinutes(4));

        // when
        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = mockMvc.perform(post("/api/admin/membership-refunds/" + failedId
                    + "/retry").header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
                .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            bodies.add(response.getContentAsString());
        }

        // then
        List<MembershipRefund> rows = refundsOfMember();
        assertThat(rows).hasSize(2);
        MembershipRefund followUp = rows.get(1);
        assertThat(followUp.getRetryOfRefundId()).isEqualTo(failedId);
        assertThat(followUp.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(bodies.get(0)).contains("\"refundId\":" + followUp.getId()).contains("\"existing\":false")
            .contains("\"retryOfRefundId\":" + failedId).contains("\"executionResult\":\"COMPLETED\"");
        assertThat(bodies.get(4)).contains("\"refundId\":" + followUp.getId()).contains("\"existing\":true");
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(2);
        assertThat(auditActions()).containsExactly("MEMBERSHIP_REFUND_RETRY");
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("재실행은 FAILED SYSTEM 환불에서만 — COMPLETED MEMBER 환불에 누르면 BILLING-021이고 새 실행이 없으니 감사도 없다")
    void retryOnNonFailedIsRejected() throws Exception {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);
        MembershipRefundResult completed = requestRefund(membershipId, newKey());

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/membership-refunds/"
                + completed.refundId() + "/retry").header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
            .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("BILLING-021");
        assertThat(refundsOfMember()).hasSize(1);
        assertThat(auditActions()).isEmpty();
    }

    @Test
    @DisplayName("강한 차단 환불의 재조회는 관측만 돌려주고 반영·차단 해제를 하지 않는다")
    void recheckOnBlockedRefundObservesOnly() throws Exception {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentPaidWithCancellations(paymentId, "other-store", FEE, RENEWAL_PAID_AT, 0L, null);
        clock.fixAt(DAY_10);
        MembershipRefundResult blocked = requestRefund(membershipId, newKey());
        PORTONE.clearGetStub(paymentId);
        PORTONE.addSimulatedCancellation(paymentId, "SUCCEEDED", 1_862L, refund(blocked.refundId()).getPgRequestReason(),
            DAY_10, "API");

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/membership-refunds/"
                + blocked.refundId() + "/recheck").header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
            .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"outcome\":\"REVIEW_REQUIRED\"")
            .contains("\"status\":\"REQUESTED\"").contains("\"pgLookedUp\":true")
            .contains("\"opsReviewReason\":\"PG_IDENTITY_MISMATCH\"");
        assertThat(refund(blocked.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(attemptOpsReason(appliedAttemptId(membershipId)))
            .isEqualTo(MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT.name());
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    // ---------- 보조 ----------

    private WebhookEvent received(String type, String portonePaymentId) {
        WebhookEvent saved = webhookEvents.saveAndFlush(WebhookEvent.received("wh_" + UUID.randomUUID(), type,
            portonePaymentId, "{}"));
        eventIds.add(saved.getId());
        return saved;
    }

    private String superAdminToken() {
        return testAdminTokens(new AuthPrincipal(SubjectType.ADMIN, superAdminId, "SUPER_ADMIN", null))
            .accessToken();
    }

    private List<String> auditActions() {
        return jdbc.queryForList("SELECT action FROM admin_audit_log WHERE admin_id = ?", String.class, superAdminId);
    }
}
