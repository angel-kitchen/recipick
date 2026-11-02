package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.dto.response.MembershipRefundRetryResponse;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.facade.MembershipRefundAdminFacade;
import com.recipick.domain.payment.facade.MembershipRefundExecutionProcessor.Claim;
import com.recipick.domain.payment.facade.MembershipRefundRecoveryFacade;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.MembershipRefundAcceptResult;
import com.recipick.domain.payment.service.MembershipRefundTicket;
import com.recipick.domain.payment.service.MembershipRenewalDecision;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneMembershipCancelResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

// 환불 경합 — 같은 키 동시 5건, 실행기·웹훅·복구 동시 반영, 옛 실행자(토큰 교체 뒤·임대 만료 뒤 재확보 전)의 덮어쓰기 금지, 재실행 동시 5건
// (접수 TX·운영 API), 즉시 해지와 갱신 접수의 회원 잠금 직렬화(두 순서를 래치로 각각 재현). POST 횟수(HTTP)와 실제 취소 작업 수를 구분해 본다.
// 실제 MySQL + PortOne 스텁
class MembershipRefundConcurrencyTest extends MembershipRefundFlowSupport {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired MembershipRefundRecoveryFacade refundRecovery;
    @Autowired MembershipRefundAdminFacade refundAdmin;
    @Autowired PaymentReconciliationFacade reconciliation;
    @Autowired WebhookEventRepository webhookEvents;
    @Autowired AdminRepository admins;

    private final List<Long> eventIds = new ArrayList<>();
    private Long superAdminId;

    @BeforeEach
    void setUpAdmin() {
        superAdminId = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).name("결제 운영")
            .passwordHash("hash").role(AdminRole.SUPER_ADMIN).active(true).build()).getId();
    }

    @AfterEach
    void cleanUpEventsAndAdmin() {
        eventIds.forEach(webhookEvents::deleteById);
        if (workersTerminated) {
            jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", superAdminId);
            admins.deleteById(superAdminId);
        }
    }

    @Test
    @DisplayName("같은 키로 동시에 5건을 요청해도 환불 행 1개·취소 작업 1개이고 모두 같은 환불 ID를 받는다")
    void fiveSameKeyRequestsCreateOneRefund() throws Exception {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        clock.fixAt(DAY_10);
        String key = newKey();

        // when
        List<Object> results = runConcurrently(5, () -> requestRefund(membershipId, key));

        // then
        List<Long> refundIds = results.stream().map(r -> ((MembershipRefundResult) r).refundId()).distinct().toList();
        assertThat(refundIds).hasSize(1);
        assertThat(refundsOfMember()).hasSize(1);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.simulatedCancellations(paymentId)).hasSize(1);
        assertThat(refund(refundIds.get(0)).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
    }

    @Test
    @DisplayName("실행기·취소 웹훅·복구 스캔이 같은 성공 취소를 동시에 반영해도 완료·종료는 1회이고 추가 POST가 없다")
    void executorWebhookRecoveryApplyOnce() throws Exception {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        clock.fixAt(DAY_10.plusMinutes(6));
        Long eventId = received("Transaction.PartialCancelled", paymentId);

        // when
        List<Object> results = runConcurrently(List.of(
            () -> refundExecution.execute(requested.refundId()),
            () -> reconciliation.processWebhook(eventId),
            () -> refundRecovery.recoverOnce()));

        // then
        MembershipRefund completed = refund(requested.refundId());
        assertThat(completed.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isEqualTo(DAY_10.plusMinutes(6));
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plusMinutes(6));
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(results.get(1)).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_refund WHERE id = ? AND status = 'COMPLETED'",
            Long.class, requested.refundId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("임대가 끝난 뒤 돌아온 옛 실행자는 새 실행권의 예약·운영 표시·발송 준비·응답 기록을 덮어쓸 수 없다")
    void staleExecutorCannotOverwriteNewClaim() {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);
        MembershipRefundAcceptResult accepted = refundRequests.acceptMember(member.getId(), membershipId,
            "mrf_member:" + member.getId() + ":stale-" + UUID.randomUUID(), "hash-stale");
        Long refundId = accepted.refundId();
        Claim stale = refundExecutions.claim(refundId);
        MembershipRefundTicket staleTicket = stale.ticket().orElseThrow();
        clock.fixAt(DAY_10.plusMinutes(6));
        Claim fresh = refundExecutions.claim(refundId);
        MembershipRefundTicket freshTicket = fresh.ticket().orElseThrow();
        refundExecutions.prepareFirstDispatch(freshTicket);

        // when
        boolean scheduled = refundExecutions.scheduleNextCheck(staleTicket, DAY_10.plusHours(1));
        boolean halted = refundExecutions.haltAuto(staleTicket, MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED);
        boolean prepared = refundExecutions.prepareFirstDispatch(staleTicket).isPresent();
        boolean recorded = refundExecutions.recordCancellationReceipt(staleTicket, new PortOneMembershipCancelResult(
            PortOneCancelOutcome.SUCCEEDED, 200, null, "cxl_stale", PortOneCancellationStatus.SUCCEEDED, 1_862L,
            freshTicket.pgRequestReason(), null, null, "stub"));

        // then
        assertThat(freshTicket.claimToken()).isEqualTo(staleTicket.claimToken() + 1);
        assertThat(scheduled).isFalse();
        assertThat(halted).isFalse();
        assertThat(prepared).isFalse();
        assertThat(recorded).isFalse();
        MembershipRefund current = refund(refundId);
        assertThat(current.getClaimToken()).isEqualTo(freshTicket.claimToken());
        assertThat(current.getNextCheckAt()).isNull();
        assertThat(current.getOpsReviewReason()).isNull();
        assertThat(current.getPortoneCancellationId()).isNull();
        assertThat(current.getFirstDispatchedAt()).isEqualTo(DAY_10.plusMinutes(6));
        assertThat(current.getLeaseExpiresAt()).isEqualTo(DAY_10.plusMinutes(11));
    }

    @Test
    @DisplayName("임대가 끝났지만 다음 실행자가 아직 토큰을 올리지 않은 사이에도 옛 실행자는 예약·운영 표시·발송 준비·응답 기록·임대 해제를 할 수 없고, 다음 실행자는 바로 잡는다")
    void expiredLeaseExecutorCannotChangeMetadataBeforeReclaim() {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);
        MembershipRefundAcceptResult accepted = refundRequests.acceptMember(member.getId(), membershipId,
            "mrf_member:" + member.getId() + ":expired-" + UUID.randomUUID(), "hash-expired");
        Long refundId = accepted.refundId();
        MembershipRefundTicket expired = refundExecutions.claim(refundId).ticket().orElseThrow();
        // 임대 5분이 막 끝난 시각. 아무도 다시 잡지 않아 토큰은 그대로다
        clock.fixAt(DAY_10.plusMinutes(5));

        // when
        boolean scheduled = refundExecutions.scheduleNextCheck(expired, DAY_10.plusHours(1));
        boolean halted = refundExecutions.haltAuto(expired, MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED);
        boolean prepared = refundExecutions.prepareFirstDispatch(expired).isPresent();
        boolean recorded = refundExecutions.recordCancellationReceipt(expired, new PortOneMembershipCancelResult(
            PortOneCancelOutcome.SUCCEEDED, 200, null, "cxl_expired", PortOneCancellationStatus.SUCCEEDED, 1_862L,
            expired.pgRequestReason(), null, null, "stub"));
        refundExecutions.releaseLease(expired);
        MembershipRefund untouched = refund(refundId);
        Claim next = refundExecutions.claim(refundId);

        // then
        assertThat(scheduled).isFalse();
        assertThat(halted).isFalse();
        assertThat(prepared).isFalse();
        assertThat(recorded).isFalse();
        assertThat(untouched.getClaimToken()).isEqualTo(expired.claimToken());
        assertThat(untouched.getLeaseExpiresAt()).isEqualTo(DAY_10.plusMinutes(5));
        assertThat(untouched.getNextCheckAt()).isNull();
        assertThat(untouched.getOpsReviewReason()).isNull();
        assertThat(untouched.getPortoneCancellationId()).isNull();
        assertThat(untouched.getFirstDispatchedAt()).isNull();
        assertThat(next.ticket()).isPresent();
        assertThat(next.ticket().get().claimToken()).isEqualTo(expired.claimToken() + 1);
        assertThat(refund(refundId).getLeaseExpiresAt()).isEqualTo(DAY_10.plusMinutes(10));
    }

    @Test
    @DisplayName("같은 FAILED SYSTEM 환불에 재실행을 동시에 5건 접수해도 후속 행은 1개다")
    void fiveRetryClicksCreateOneFollowUp() throws Exception {
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

        // when
        List<Object> results = runConcurrently(5, () -> refundRequests.acceptSystemRetry(member.getId(), failedId));

        // then
        List<Long> followUpIds = results.stream().map(r -> ((MembershipRefundAcceptResult) r).refundId()).distinct()
            .toList();
        assertThat(followUpIds).hasSize(1);
        assertThat(results.stream().filter(r -> !((MembershipRefundAcceptResult) r).existing()).count()).isEqualTo(1);
        assertThat(refundsOfMember()).hasSize(2);
        assertThat(refund(followUpIds.get(0)).getRetryOfRefundId()).isEqualTo(failedId);
    }

    @Test
    @DisplayName("운영 재실행 API를 동시에 5건 눌러도 후속 환불 1건·재실행 감사 1건이고 나머지 4건은 기존 행을 감사 없이 돌려준다")
    void fiveAdminRetriesCreateOneFollowUpAndOneAudit() throws Exception {
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
        List<Object> results = runConcurrently(5, () -> asSuperAdmin(() -> refundAdmin.retry(failedId)));

        // then
        List<MembershipRefundRetryResponse> responses = results.stream().map(r -> (MembershipRefundRetryResponse) r)
            .toList();
        List<MembershipRefund> rows = refundsOfMember();
        assertThat(rows).hasSize(2);
        MembershipRefund followUp = rows.get(1);
        assertThat(followUp.getRetryOfRefundId()).isEqualTo(failedId);
        assertThat(followUp.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(responses).allSatisfy(r -> assertThat(r.refundId()).isEqualTo(followUp.getId()));
        assertThat(responses.stream().filter(r -> !r.existing()).count()).isEqualTo(1);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT action FROM admin_audit_log WHERE admin_id = ?", String.class, superAdminId))
            .containsExactly("MEMBERSHIP_REFUND_RETRY");
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("갱신 접수가 가능한 시각(이전 기간 종료 1분 뒤)에 즉시 해지가 먼저 회원 잠금을 잡으면 0원 환불로 완료·종료되고, 뒤따른 갱신 접수는 새 회차 없이 건너뛴다")
    void terminationBeforeRenewalAcceptEndsWithZeroRefund() throws Exception {
        // given
        Long membershipId = activeMembership();
        String paymentId = simulateFirstPayment(membershipId);
        clock.fixAt(T.plusMinutes(1));

        // when
        List<Object> results = runInMemberLockOrder(
            () -> requestRefundOrError(membershipId),
            () -> renewalProcessor.accept(member.getId(), membershipId));

        // then
        MembershipRefundResult refund = (MembershipRefundResult) results.get(0);
        MembershipRenewalDecision renewal = (MembershipRenewalDecision) results.get(1);
        assertThat(refund.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(refund.refundAmount()).isZero();
        assertThat(refund.membershipTerminated()).isTrue();
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(T.plusMinutes(1));
        assertThat(renewal.kind()).isEqualTo(MembershipRenewalDecision.Kind.SKIPPED);
        assertThat(renewalPaymentIds(membershipId)).isEmpty();
        assertThat(refundsOfMember()).hasSize(1);
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("같은 시각에 갱신 접수가 먼저 회원 잠금을 잡으면 미정산 회차가 생겨 뒤따른 즉시 해지는 MEMBERSHIP-010으로 거절되고 MEMBER 환불 행이 생기지 않는다")
    void renewalAcceptBeforeTerminationRejectsRefund() throws Exception {
        // given
        Long membershipId = activeMembership();
        String paymentId = simulateFirstPayment(membershipId);
        clock.fixAt(T.plusMinutes(1));

        // when
        List<Object> results = runInMemberLockOrder(
            () -> renewalProcessor.accept(member.getId(), membershipId),
            () -> requestRefundOrError(membershipId));

        // then
        MembershipRenewalDecision renewal = (MembershipRenewalDecision) results.get(0);
        assertThat(renewal.kind()).isEqualTo(MembershipRenewalDecision.Kind.CHARGE_READY);
        assertThat(results.get(1)).isEqualTo(MemberErrorCode.CANCEL_PAYMENT_IN_PROGRESS);
        assertThat(renewalPaymentIds(membershipId)).hasSize(1);
        assertThat(refundsOfMember()).isEmpty();
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    // ---------- 보조 ----------

    private Object requestRefundOrError(Long membershipId) {
        try {
            return requestRefund(membershipId, newKey());
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    // 작업 스레드에 SUPER_ADMIN 인증을 두고 운영 Facade를 부른다(감사 기록이 요청자를 읽는다)
    private <T> T asSuperAdmin(Callable<T> task) throws Exception {
        AuthPrincipal principal = new AuthPrincipal(SubjectType.ADMIN, superAdminId, "SUPER_ADMIN", null);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.authorities()));
        try {
            return task.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // 회원 잠금을 쥔 채 first를 먼저 잠금 대기에 세우고, 잠금을 풀어 first가 회원 잠금을 얻은 직후 second를 시작한다 — second의 첫 DB 작업도
    // 회원 잠금이므로 first의 커밋을 기다린다. 동시 실행을 반복하지 않고 두 순서를 각각 재현한다
    private List<Object> runInMemberLockOrder(Callable<?> first, Callable<?> second) throws Exception {
        workersTerminated = false;
        HeldLock held = holdMemberLock();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> firstResult = pool.submit(() -> (Object) first.call());
            Thread.sleep(500);
            held.releaseAndWait();
            Future<Object> secondResult = pool.submit(() -> (Object) second.call());
            return List.of(firstResult.get(WAIT.toSeconds(), TimeUnit.SECONDS),
                secondResult.get(WAIT.toSeconds(), TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            workersTerminated = pool.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    private Long received(String type, String portonePaymentId) {
        Long id = webhookEvents.saveAndFlush(com.recipick.domain.payment.entity.WebhookEvent.received(
            "wh_" + UUID.randomUUID(), type, portonePaymentId, "{}")).getId();
        eventIds.add(id);
        return id;
    }

    private List<Object> runConcurrently(int count, Callable<?> task) throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }

    // 출발 신호 뒤 동시에 시작한다. 작업 스레드가 모두 끝나야 정리(@AfterEach)가 안전하다
    private List<Object> runConcurrently(List<Callable<?>> tasks) throws Exception {
        workersTerminated = false;
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await(WAIT.toSeconds(), TimeUnit.SECONDS);
                return (Object) task.call();
            }));
        }
        start.countDown();
        List<Object> results = new ArrayList<>();
        try {
            for (Future<Object> future : futures) {
                results.add(future.get(WAIT.toSeconds(), TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
            workersTerminated = pool.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
        return results;
    }
}
