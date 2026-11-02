package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.MembershipRefundAcceptResult;
import com.recipick.domain.payment.service.MembershipRefundAcceptResult.RejectReason;
import com.recipick.domain.payment.service.MembershipRefundCheckResult;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.domain.payment.service.MembershipRefundTicket;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.dto.PortOnePayment;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 환불 실행기 — SYSTEM 환불·결과 불명·재조회 예약 시각 전 실행 금지·재POST 창·POST 직전 회차 차단 재확인·결과 확인 창·취소 매칭·증거 충돌·
// 관리자 재조회·SYSTEM 재실행. 실제 MySQL + PortOne 스텁
class MembershipRefundExecutionIntegrationTest extends MembershipRefundFlowSupport {

    private static final Duration LEASE_PASSED = Duration.ofMinutes(6);

    // ---------- SYSTEM 환불 ----------

    @Test
    @DisplayName("금액 불일치 수납의 SYSTEM 환불은 실제 수납액으로 ADMIN 취소를 보내 완료하고, 생성 때 PG 키가 있고 발송 시각은 실행 때 생기며 이용권은 바꾸지 않는다")
    void systemRefundUsesActualPaidAmount() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, 3_800L, "KRW", RENEWAL_PAID_AT);
        clock.fixAt(T.plusMinutes(2));
        MembershipChargeOutcome charge = executor.reconcile(member.getId(), attemptId,
            MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
        assertThat(charge).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        MembershipRefund requested = refundsOfMember().get(0);
        assertThat(requested.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(requested.getAmount()).isEqualTo(3_800L);
        assertThat(requested.getPaidAmountSnapshot()).isEqualTo(3_800L);
        assertThat(requested.getPgIdempotencyKey()).startsWith("mrf_v1_");
        assertThat(requested.getFirstDispatchedAt()).isNull();
        assertThat(requested.getPgStoreId()).isNull();
        PORTONE.reset();
        PORTONE.simulateRefunds(paymentId, STORE_ID, 3_800L, RENEWAL_PAID_AT);
        clock.fixAt(T.plusMinutes(3));

        // when
        MembershipRefundExecutionResult result = refundExecution.execute(requested.getId());

        // then
        assertThat(result).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        MembershipRefund completed = refund(requested.getId());
        assertThat(completed.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(completed.getPgStoreId()).isEqualTo(STORE_ID);
        assertThat(completed.getFirstDispatchedAt()).isEqualTo(T.plusMinutes(3));
        assertThat(completed.getPortoneCancellationId()).isEqualTo("cxl_1");
        assertThat(PORTONE.cancelBodies(paymentId).get(0))
            .contains("\"amount\":3800").contains("\"currentCancellableAmount\":3800").contains("\"requester\":\"ADMIN\"");
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
        assertThat(refundExecution.execute(requested.getId())).isEqualTo(MembershipRefundExecutionResult.SKIPPED);
    }

    // ---------- 원자성·복구 ----------

    @Test
    @DisplayName("PG 취소 성공 뒤 종료 반영이 DB 오류로 실패하면 완료·종료가 함께 롤백되고, 임대가 끝난 뒤 같은 취소 ID 재조회로 POST 없이 1회 완료된다")
    void terminationFailureRollsBackCompletionAndRecoversByCancellationId() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        clock.fixAt(DAY_10);
        jdbc.execute("ALTER TABLE membership ADD CONSTRAINT chk_refund_test CHECK (terminated_at IS NULL)");
        try {
            // 종료 UPDATE가 CHECK로 실패 → TX2 롤백. 요청은 예외로 끝나고 접수(REQUESTED)와 취소 ID 기록은 이미 커밋돼 있다
            assertThatThrownBy(() -> requestRefund(membershipId, newKey())).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("ALTER TABLE membership DROP CHECK chk_refund_test");
        }
        MembershipRefund afterFailure = refundsOfMember().get(0);
        Long refundId = afterFailure.getId();
        boolean terminatedAfterFailure = membership(membershipId).getTerminatedAt() != null;

        // when
        clock.fixAt(DAY_10.plus(LEASE_PASSED));
        MembershipRefundExecutionResult recovered = refundExecution.execute(refundId);

        // then
        assertThat(afterFailure.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(afterFailure.getPortoneCancellationId()).isEqualTo("cxl_1");
        assertThat(terminatedAfterFailure).isFalse();
        assertThat(recovered).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        assertThat(refund(refundId).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plus(LEASE_PASSED));
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    // ---------- 결과 불명 ----------

    @Test
    @DisplayName("취소 POST timeout은 REQUESTED·같은 PG 키를 유지하고 다음 확인을 예약하며, 뒤의 재조회가 PG에 생긴 취소를 reason으로 찾아 POST 없이 완료한다")
    void postTimeoutKeepsRequestedAndKey() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.delayCancels(3_000);
        clock.fixAt(DAY_10);

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        MembershipRefund pending = refund(result.refundId());
        PORTONE.delayCancels(0);
        clock.fixAt(DAY_10.plus(LEASE_PASSED));
        MembershipRefundExecutionResult later = refundExecution.execute(result.refundId());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(pending.getPortoneCancellationId()).isNull();
        assertThat(pending.getFirstDispatchedAt()).isEqualTo(DAY_10);
        assertThat(pending.getNextCheckAt()).isEqualTo(DAY_10.plusMinutes(1));
        assertThat(pending.getLeaseExpiresAt()).isNull();
        assertThat(later).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        MembershipRefund completed = refund(result.refundId());
        assertThat(completed.getPgIdempotencyKey()).isEqualTo(pending.getPgIdempotencyKey());
        assertThat(completed.getPortoneCancellationId()).isEqualTo("cxl_1");
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plus(LEASE_PASSED));
    }

    @Test
    @DisplayName("접수 직후 예약된 재조회 시각 전에 큐 Worker가 실행해도 실행권을 잡지 않아 GET·POST가 없고, 예약 시각이 되면 실행한다")
    void workerBeforeScheduledCheckDoesNotExecute() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        MembershipRefund scheduled = refund(result.refundId());
        int getsAfterAccept = PORTONE.getCount(paymentId);

        // when
        MembershipRefundExecutionResult early = refundExecution.execute(result.refundId());
        clock.fixAt(DAY_10.plusSeconds(59));
        MembershipRefundExecutionResult stillEarly = refundExecution.execute(result.refundId());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        clock.fixAt(DAY_10.plusMinutes(1));
        MembershipRefundExecutionResult due = refundExecution.execute(result.refundId());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(scheduled.getNextCheckAt()).isEqualTo(DAY_10.plusMinutes(1));
        assertThat(scheduled.getLeaseExpiresAt()).isNull();
        assertThat(early).isEqualTo(MembershipRefundExecutionResult.SKIPPED);
        assertThat(stillEarly).isEqualTo(MembershipRefundExecutionResult.SKIPPED);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsAfterAccept + 1);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(due).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        MembershipRefund completed = refund(result.refundId());
        assertThat(completed.getClaimToken()).isEqualTo(scheduled.getClaimToken() + 1);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plusMinutes(1));
    }

    @Test
    @DisplayName("실행권을 잡은 뒤 같은 회차에 강한 차단이 생기면 POST 준비가 거절되어 취소를 보내지 않고, 임대만 돌려준 채 운영 확인에 남는다")
    void roundBlockedAfterClaimRefusesDispatch() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        Long attemptId = appliedAttemptId(membershipId);
        clock.fixAt(DAY_10);
        MembershipRefundAcceptResult accepted = refundRequests.acceptMember(member.getId(), membershipId,
            "mrf_member:" + member.getId() + ":blocked-round", "hash-blocked-round");
        MembershipRefundTicket ticket = refundExecutions.claim(accepted.refundId()).ticket().orElseThrow();
        // 실행권 확보 뒤, 같은 회차의 다른 경로가 시도에 강한 차단을 남겼다
        jdbc.update("UPDATE membership_payment_attempt SET ops_review_required_at = ?, ops_review_reason = ? WHERE id = ?",
            DAY_10, MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT.name(), attemptId);

        // when
        boolean prepared = refundExecutions.prepareFirstDispatch(ticket).isPresent();
        MembershipRefundExecutionResult next = refundExecution.execute(accepted.refundId());

        // then
        assertThat(prepared).isFalse();
        assertThat(next).isEqualTo(MembershipRefundExecutionResult.SKIPPED);
        MembershipRefund untouched = refund(accepted.refundId());
        assertThat(untouched.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(untouched.getFirstDispatchedAt()).isNull();
        assertThat(untouched.getPortoneCancellationId()).isNull();
        assertThat(untouched.getClaimToken()).isEqualTo(ticket.claimToken());
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("같은 키 재POST는 최초 발송 + 2시간 55분 미만에서만 보내고, 그 뒤에는 예약된 확인마다 GET만 계속하다 24시간이 지나면 운영 확인(UNKNOWN_WINDOW_EXPIRED)으로 멈춘다")
    void repostWindowClosesAndUnknownWindowExpires() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.cancelError(paymentId, 500, "INTERNAL_SERVER_ERROR");
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        Long refundId = result.refundId();

        // when
        clock.fixAt(DAY_10.plusHours(2).plusMinutes(54));
        MembershipRefundExecutionResult insideWindow = refundExecution.execute(refundId);
        int postsInside = PORTONE.cancelCount(paymentId);
        LocalDateTime nextAfterInside = refund(refundId).getNextCheckAt();
        clock.fixAt(DAY_10.plusHours(2).plusMinutes(55));
        MembershipRefundExecutionResult beforeNextCheck = refundExecution.execute(refundId);
        int getsBefore = PORTONE.getCount(paymentId);
        clock.fixAt(nextAfterInside);
        MembershipRefundExecutionResult afterWindow = refundExecution.execute(refundId);
        int postsAfterWindow = PORTONE.cancelCount(paymentId);
        clock.fixAt(DAY_10.plusHours(23).plusMinutes(50));
        MembershipRefundExecutionResult beforeExpiry = refundExecution.execute(refundId);
        LocalDateTime nextAtExpiry = refund(refundId).getNextCheckAt();
        clock.fixAt(DAY_10.plusHours(24));
        MembershipRefundExecutionResult atExpiry = refundExecution.execute(refundId);
        MembershipRefundExecutionResult afterHalt = refundExecution.execute(refundId);

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(insideWindow).isEqualTo(MembershipRefundExecutionResult.PENDING);
        assertThat(postsInside).isEqualTo(2);
        assertThat(PORTONE.cancelIdempotencyKeys(paymentId)).hasSize(2);
        assertThat(PORTONE.cancelIdempotencyKeys(paymentId).get(0)).isEqualTo(PORTONE.cancelIdempotencyKeys(paymentId).get(1));
        assertThat(nextAfterInside).isEqualTo(DAY_10.plusHours(3).plusMinutes(4));
        assertThat(beforeNextCheck).isEqualTo(MembershipRefundExecutionResult.SKIPPED);
        assertThat(afterWindow).isEqualTo(MembershipRefundExecutionResult.PENDING);
        assertThat(postsAfterWindow).isEqualTo(2);
        assertThat(PORTONE.getCount(paymentId)).isGreaterThan(getsBefore);
        assertThat(beforeExpiry).isEqualTo(MembershipRefundExecutionResult.PENDING);
        assertThat(nextAtExpiry).isEqualTo(DAY_10.plusHours(24));
        assertThat(atExpiry).isEqualTo(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        assertThat(afterHalt).isEqualTo(MembershipRefundExecutionResult.SKIPPED);
        MembershipRefund halted = refund(refundId);
        assertThat(halted.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(halted.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED);
        assertThat(halted.isSettlementBlocked()).isFalse();
        assertThat(halted.getNextCheckAt()).isNull();
        assertThat(halted.getLeaseExpiresAt()).isNull();
        assertThat(attemptOpsReason(appliedAttemptId(membershipId))).isNull();
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("GET 오류만 24시간 지속되면(발송 없음) 접수 시각 기준으로 예약된 확인에서 운영 확인이 되고 POST는 없다")
    void getFailureOnlyExpiresToReviewFromRequestedAt() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentServerError(paymentId);
        clock.fixAt(DAY_10);
        MembershipRefundResult getFailing = requestRefund(membershipId, newKey());
        clock.fixAt(DAY_10.plusHours(23).plusMinutes(50));
        MembershipRefundExecutionResult beforeExpiry = refundExecution.execute(getFailing.refundId());
        LocalDateTime scheduledAt = refund(getFailing.refundId()).getNextCheckAt();
        clock.fixAt(DAY_10.plusHours(24));

        // when
        MembershipRefundExecutionResult expired = refundExecution.execute(getFailing.refundId());

        // then
        assertThat(getFailing.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(beforeExpiry).isEqualTo(MembershipRefundExecutionResult.PENDING);
        assertThat(scheduledAt).isEqualTo(DAY_10.plusHours(24));
        assertThat(expired).isEqualTo(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        MembershipRefund halted = refund(getFailing.refundId());
        assertThat(halted.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED);
        assertThat(halted.getFirstDispatchedAt()).isNull();
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("취소가 REQUESTED로 24시간 머물면 FAILED가 아니라 REQUESTED 유지 + 운영 확인이다")
    void requestedCancellationExpiresToReviewNotFailed() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        clock.fixAt(DAY_10.plusHours(24));

        // when
        MembershipRefundExecutionResult expired = refundExecution.execute(requested.refundId());

        // then
        assertThat(requested.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund(requested.refundId()).getPortoneCancellationId()).isEqualTo("cxl_1");
        assertThat(expired).isEqualTo(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        MembershipRefund halted = refund(requested.refundId());
        assertThat(halted.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(halted.getFailureReason()).isNull();
        assertThat(halted.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("24시간 경계에 도착한 정상 SUCCEEDED는 시간 초과로 버리지 않고 완료한다")
    void succeededAtExpiryBoundaryCompletes() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult requested = requestRefund(membershipId, newKey());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        clock.fixAt(DAY_10.plusHours(24));

        // when
        MembershipRefundExecutionResult result = refundExecution.execute(requested.refundId());

        // then
        assertThat(result).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        assertThat(refund(requested.refundId()).getOpsReviewReason()).isNull();
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plusHours(24));
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("409 IDEMPOTENCY_OUTSTANDING_REQUEST는 FAILED가 아니라 같은 키 재확인이고, 다음 실행이 같은 키·같은 본문으로 다시 보내 완료한다")
    void idempotencyOutstandingKeepsSameKey() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.cancelError(paymentId, 409, "IDEMPOTENCY_OUTSTANDING_REQUEST");
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        PORTONE.clearCancelStub(paymentId);
        clock.fixAt(DAY_10.plus(LEASE_PASSED));

        // when
        MembershipRefundExecutionResult later = refundExecution.execute(result.refundId());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund(result.refundId()).getFailureReason()).isNull();
        assertThat(later).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(2);
        assertThat(PORTONE.cancelIdempotencyKeys(paymentId)).hasSize(2);
        assertThat(PORTONE.cancelIdempotencyKeys(paymentId).get(0)).isEqualTo(PORTONE.cancelIdempotencyKeys(paymentId).get(1));
        assertThat(PORTONE.cancelBodies(paymentId).get(0)).isEqualTo(PORTONE.cancelBodies(paymentId).get(1));
    }

    @Test
    @DisplayName("400은 요청 거절(PG_REQUEST_REJECTED), 401·403은 인증 오류(PG_AUTH_ERROR)로 자동 POST를 멈추고 REQUESTED를 유지한다")
    void rejectedResponsesHaltAutomaticPost() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.cancelError(paymentId, 403, "FORBIDDEN");
        clock.fixAt(DAY_10);

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        MembershipRefundExecutionResult again = refundExecution.execute(result.refundId());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(result.reviewRequired()).isTrue();
        MembershipRefund halted = refund(result.refundId());
        assertThat(halted.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.PG_AUTH_ERROR);
        assertThat(halted.isSettlementBlocked()).isFalse();
        assertThat(again).isEqualTo(MembershipRefundExecutionResult.SKIPPED);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    // ---------- 매칭 ----------

    @Test
    @DisplayName("같은 금액이지만 다른 reason의 외부 취소는 자동 귀속하지 않고, 설명되지 않는 성공 취소가 있어 POST도 보내지 않는다")
    void foreignCancellationWithSameAmountIsNotAdopted() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.addSimulatedCancellation(paymentId, "SUCCEEDED", 1_862L, "manual console refund", T.plusDays(5));
        clock.fixAt(DAY_10);

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(result.membershipTerminated()).isFalse();
        MembershipRefund pending = refund(result.refundId());
        assertThat(pending.getPortoneCancellationId()).isNull();
        assertThat(pending.getNextCheckAt()).isEqualTo(DAY_10.plusMinutes(1));
        assertThat(pending.getOpsReviewReason()).isNull();
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("응답으로 받은 취소 ID가 조회에 아직 보이지 않으면 새 취소 없이 재조회를 예약한다")
    void knownCancellationIdNotYetVisibleRetriesLookup() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        PORTONE.getPaymentPaidWithCancellations(paymentId, STORE_ID, FEE, RENEWAL_PAID_AT, 0L, null);
        clock.fixAt(DAY_10.plus(LEASE_PASSED));

        // when
        MembershipRefundExecutionResult later = refundExecution.execute(result.refundId());

        // then
        assertThat(refund(result.refundId()).getPortoneCancellationId()).isEqualTo("cxl_1");
        assertThat(later).isEqualTo(MembershipRefundExecutionResult.PENDING);
        assertThat(refund(result.refundId()).getNextCheckAt()).isEqualTo(DAY_10.plus(LEASE_PASSED).plusMinutes(4));
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 reason의 취소 ID가 여러 개면 자동으로 고르지 않고 증거 충돌로 차단한다(환불 EVIDENCE_CONFLICT·시도 REFUND_EVIDENCE_CONFLICT)")
    void ambiguousCandidatesRequireReview() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentServerError(paymentId);
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        String reason = refund(result.refundId()).getPgRequestReason();
        PORTONE.addSimulatedCancellation(paymentId, "SUCCEEDED", 1_862L, reason, DAY_10, "API");
        PORTONE.addSimulatedCancellation(paymentId, "REQUESTED", 1_862L, reason, DAY_10.plusSeconds(1), "API");
        PORTONE.clearGetStub(paymentId);
        clock.fixAt(DAY_10.plus(LEASE_PASSED));

        // when
        MembershipRefundExecutionResult later = refundExecution.execute(result.refundId());

        // then
        assertThat(later).isEqualTo(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        MembershipRefund blocked = refund(result.refundId());
        assertThat(blocked.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(blocked.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.EVIDENCE_CONFLICT);
        assertThat(blocked.isSettlementBlocked()).isTrue();
        assertThat(attemptOpsReason(appliedAttemptId(membershipId)))
            .isEqualTo(MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT.name());
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
        assertThat(refundExecution.execute(result.refundId())).isEqualTo(MembershipRefundExecutionResult.SKIPPED);
    }

    @Test
    @DisplayName("자기 취소 성공과 설명되지 않는 다른 성공 취소가 함께 보이면 자동 완료·추가 POST 없이 정합성 차단한다")
    void ownSuccessWithUnexplainedOtherSuccessBlocks() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        PORTONE.addSimulatedCancellation(paymentId, "SUCCEEDED", 1_000L, "console", DAY_10.plusMinutes(1));
        clock.fixAt(DAY_10.plus(LEASE_PASSED));

        // when
        MembershipRefundExecutionResult later = refundExecution.execute(result.refundId());

        // then
        assertThat(later).isEqualTo(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        assertThat(refund(result.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund(result.refundId()).getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.EVIDENCE_CONFLICT);
        assertThat(attemptOpsReason(appliedAttemptId(membershipId)))
            .isEqualTo(MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT.name());
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("POST 응답에 취소 ID가 있어도 금액·reason이 저장 근거와 다르면 귀속하지 않고 재조회로 넘긴다")
    void postReceiptWithDifferentAmountIsNotAdopted() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.cancelSucceeded(paymentId, "cxl_bad", 1_000L, "someone else", DAY_10);
        clock.fixAt(DAY_10);

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        MembershipRefund pending = refund(result.refundId());
        assertThat(pending.getPortoneCancellationId()).isNull();
        assertThat(pending.getOpsReviewReason()).isNull();
        assertThat(pending.getNextCheckAt()).isNotNull();
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("FAILED 취소와 REQUESTED 취소만 있으면 누적 성공 취소액 0원으로 보고 차단 없이 재조회한다")
    void failedAndRequestedCancellationsSumToZero() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "FAILED");
        clock.fixAt(DAY_10);
        MembershipRefundResult failed = requestRefund(membershipId, newKey());
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10.plusHours(1));

        // when
        MembershipRefundResult requested = requestRefund(membershipId, newKey());

        // then
        assertThat(failed.refundStatus()).isEqualTo(MembershipRefundStatus.FAILED);
        assertThat(requested.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund(requested.refundId()).getPortoneCancellationId()).isEqualTo("cxl_2");
        assertThat(refund(requested.refundId()).getOpsReviewReason()).isNull();
        assertThat(attemptOpsReason(appliedAttemptId(membershipId))).isNull();
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(2);
    }

    @Test
    @DisplayName("누적 취소액과 취소 배열이 일시적으로 다르면 강한 차단 없이 PENDING이고 POST를 보내지 않는다")
    void transientCancelledSumMismatchIsPending() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentPaidWithCancellations(paymentId, STORE_ID, FEE, RENEWAL_PAID_AT, 1_862L, null);
        clock.fixAt(DAY_10);

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund(result.refundId()).getOpsReviewReason()).isNull();
        assertThat(attemptOpsReason(appliedAttemptId(membershipId))).isNull();
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("SUCCEEDED 취소에 cancelledAt이 없어도 ID·금액이 맞으면 완료한다")
    void succeededWithoutCancelledAtCompletes() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        String reason = refund(result.refundId()).getPgRequestReason();
        PORTONE.getPaymentPaidWithCancellations(paymentId, STORE_ID, FEE, RENEWAL_PAID_AT, 1_862L,
            "[" + PortOneStubServer.cancellationJson("cxl_1", "SUCCEEDED", 1_862L, reason, "API", DAY_10, null) + "]");
        clock.fixAt(DAY_10.plus(LEASE_PASSED));

        // when
        MembershipRefundExecutionResult later = refundExecution.execute(result.refundId());

        // then
        assertThat(later).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        assertThat(refund(result.refundId()).getPgCancelledAt()).isNull();
        assertThat(refund(result.refundId()).getCompletedAt()).isEqualTo(DAY_10.plus(LEASE_PASSED));
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plus(LEASE_PASSED));
    }

    @Test
    @DisplayName("PG 응답의 상점이 다르면 반영·POST 없이 PG_IDENTITY_MISMATCH와 시도 차단으로 끝난다")
    void identityMismatchBlocksWithoutPost() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentPaidWithCancellations(paymentId, "other-store", FEE, RENEWAL_PAID_AT, 0L, null);
        clock.fixAt(DAY_10);

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(result.reviewRequired()).isTrue();
        MembershipRefund blocked = refund(result.refundId());
        assertThat(blocked.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.PG_IDENTITY_MISMATCH);
        assertThat(blocked.getPgStoreId()).isEqualTo(STORE_ID);
        assertThat(attemptOpsReason(appliedAttemptId(membershipId)))
            .isEqualTo(MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT.name());
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("PG 키가 없는 기존 행은 발송 이력을 증명할 수 없어 POST 없이 LEGACY_DISPATCH_UNPROVEN으로 멈춘다")
    void legacyRowWithoutKeyIsNotDispatched() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentServerError(paymentId);
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        jdbc.update("UPDATE membership_refund SET pg_idempotency_key = NULL, pg_store_id = NULL, pg_request_reason = NULL "
            + "WHERE id = ?", result.refundId());
        PORTONE.clearGetStub(paymentId);
        clock.fixAt(DAY_10.plus(LEASE_PASSED));

        // when
        MembershipRefundExecutionResult later = refundExecution.execute(result.refundId());

        // then
        assertThat(later).isEqualTo(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        MembershipRefund halted = refund(result.refundId());
        assertThat(halted.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(halted.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.LEGACY_DISPATCH_UNPROVEN);
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    // ---------- 관리자 재조회·SYSTEM 재실행 ----------

    @Test
    @DisplayName("관리자 재조회는 GET 1회로 관측값을 그대로 돌려주고 토큰 없이도 검증된 성공을 완료로 반영하며, GET 실패는 저장 상태와 분리된다")
    void recheckReturnsObservationAndAppliesVerifiedSuccess() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        int getsBefore = PORTONE.getCount(paymentId);

        // when
        MembershipRefundCheckResult check = refundExecution.recheck(result.refundId());
        PORTONE.getPaymentServerError(paymentId);
        MembershipRefundCheckResult failedLookup = refundExecution.recheck(result.refundId());

        // then
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore + 2);
        assertThat(check.outcome()).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        assertThat(check.observation().lookedUp()).isTrue();
        assertThat(check.observation().cancellationId()).isEqualTo("cxl_1");
        assertThat(check.observation().cancellationAmount()).isEqualTo(1_862L);
        assertThat(refund(result.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
        assertThat(failedLookup.outcome()).isEqualTo(MembershipRefundExecutionResult.PENDING);
        assertThat(failedLookup.observation().lookedUp()).isFalse();
        assertThat(failedLookup.observation().code()).isEqualTo("COMMON-008");
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("SYSTEM FAILED 환불의 재실행은 후속 행 하나만 만들고(반복 접수는 같은 행), 이전 FAILED 취소가 성공으로 바뀌어 보이면 새 POST 없이 차단한다")
    void systemRetryIsIdempotentAndContradictionBlocks() {
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
        assertThat(refundExecution.execute(failedId)).isEqualTo(MembershipRefundExecutionResult.FAILED);
        assertThatThrownBy(() -> refundRequests.acceptSystemRetry(member.getId(), failedId + 999_999L))
            .isInstanceOf(BusinessException.class);

        // when
        MembershipRefundAcceptResult retry = refundRequests.acceptSystemRetry(member.getId(), failedId);
        MembershipRefundAcceptResult again = refundRequests.acceptSystemRetry(member.getId(), failedId);
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        clock.fixAt(T.plusMinutes(4));
        MembershipRefundExecutionResult executed = refundExecution.execute(retry.refundId());

        // then
        assertThat(retry.requiresExecution()).isTrue();
        assertThat(again.existing()).isTrue();
        assertThat(again.refundId()).isEqualTo(retry.refundId());
        MembershipRefund followUp = refund(retry.refundId());
        assertThat(followUp.getRetryOfRefundId()).isEqualTo(failedId);
        assertThat(followUp.getAmount()).isEqualTo(3_800L);
        assertThat(followUp.getIdempotencyKey()).isEqualTo("mrf_sys_" + attemptId + "_2");
        assertThat(executed).isEqualTo(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        assertThat(refund(retry.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund(retry.refundId()).getPortoneCancellationId()).isNull();
        assertThat(refund(failedId).getStatus()).isEqualTo(MembershipRefundStatus.FAILED);
        assertThat(refund(failedId).getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.EVIDENCE_CONFLICT);
        assertThat(attemptOpsReason(attemptId)).isEqualTo(MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT.name());
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(refundRequests.acceptSystemRetry(member.getId(), failedId).existing()).isTrue();
    }

    @Test
    @DisplayName("SYSTEM 재실행은 FAILED SYSTEM 행에서만 허용된다 — COMPLETED 행·MEMBER 행은 RETRY_NOT_ALLOWED")
    void systemRetryRejectsNonFailedOrMemberRows() {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);
        MembershipRefundResult memberRefund = requestRefund(membershipId, newKey());

        // when
        MembershipRefundAcceptResult rejected = refundRequests.acceptSystemRetry(member.getId(), memberRefund.refundId());

        // then
        assertThat(memberRefund.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(rejected.isRejected()).isTrue();
        assertThat(rejected.rejectReason()).isEqualTo(RejectReason.RETRY_NOT_ALLOWED);
        assertThat(refundsOfMember()).hasSize(1);
    }

    @Test
    @DisplayName("다른 경로가 받은 취소 관측(reconcileObserved)으로도 GET·POST 없이 검증된 성공을 완료할 수 있다")
    void reconcileObservedAppliesWithoutLookup() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "REQUESTED");
        clock.fixAt(DAY_10);
        MembershipRefundResult result = requestRefund(membershipId, newKey());
        PORTONE.settleSimulated(paymentId, "cxl_1", "SUCCEEDED");
        PortOnePayment observed = portOnePaymentClient.getPayment(paymentId);
        int gets = PORTONE.getCount(paymentId);

        // when
        MembershipRefundExecutionResult outcome = refundExecution.reconcileObserved(appliedAttemptId(membershipId), observed);

        // then
        assertThat(outcome).isEqualTo(MembershipRefundExecutionResult.COMPLETED);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(gets);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(refund(result.refundId()).getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
    }
}
