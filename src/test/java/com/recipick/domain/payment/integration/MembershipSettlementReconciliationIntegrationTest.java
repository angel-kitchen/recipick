package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.domain.payment.service.MembershipPgVerdict;
import com.recipick.domain.payment.service.MembershipReconciliationResult;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

// 확정·미발송 시도에 새 PG 관측이 왔을 때의 판정. 갱신 회차로 재현한다(첫 결제와 같은 TX2 판정 경로).
// 웹훅 경로는 검증된 웹훅과 같은 재조회 원인(VERIFIED_WEBHOOK)으로 실행기를 직접 부른다 — PG GET은 스텁 서버의 실제 HTTP
class MembershipSettlementReconciliationIntegrationTest extends MembershipRenewalFlowSupport {

    @Autowired MemberService memberService;
    @Autowired MembershipBillingService billing;
    @Autowired MembershipPaymentQueryService queryService;

    // ---------- 발송된 FAILED 뒤 늦은 수납 ----------

    @Test
    @DisplayName("발송된 FAILED 시도에 늦은 PAID가 오면 상태를 PAID로 정정하고 실패 이력은 보존, 실제 수납액 SYSTEM 환불 1건, 연장 없음")
    void latePaidOnDispatchedFailedIsCorrectedAndRefunded() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = failedRenewalAttempt();
        MembershipPaymentAttempt failed = attempt(attemptId);
        PORTONE.getPaymentPaid(failed.getPortonePaymentId(), FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusHours(2));

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);
        MembershipChargeOutcome replay = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(replay).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        MembershipPaymentAttempt corrected = attempt(attemptId);
        assertThat(corrected.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(corrected.getFailureReason()).isEqualTo(failed.getFailureReason());
        assertThat(corrected.getCompletedAt()).isEqualTo(failed.getCompletedAt());
        assertThat(corrected.getPgPaidAt()).isEqualTo(T.plusMinutes(1));
        assertThat(corrected.getRefundRequiredAt()).isEqualTo(T.plusHours(2));
        assertThat(corrected.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.LATE_PAID_AFTER_FAILED);
        assertThat(corrected.isSettlementBlocked()).isFalse();
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
        assertThat(existsUnresolved()).isTrue();
    }

    @Test
    @DisplayName("정정된 수납의 SYSTEM 환불이 FAILED·COMPLETED여도 같은 수납 재전송은 새 환불을 만들지 않는다")
    void refundRowInAnyStatusIsNotDuplicated() {
        // given
        activeMembership();
        Long attemptId = failedRenewalAttempt();
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.plusMinutes(1));
        webhook(attemptId);

        // when
        jdbc.update("UPDATE membership_refund SET status = 'FAILED' WHERE membership_payment_attempt_id = ?",
            attemptId);
        MembershipChargeOutcome afterFailed = webhook(attemptId);
        jdbc.update("UPDATE membership_refund SET status = 'COMPLETED' WHERE membership_payment_attempt_id = ?",
            attemptId);
        MembershipChargeOutcome afterCompleted = webhook(attemptId);

        // then
        assertThat(afterFailed).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(afterCompleted).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(refundRowCount(attemptId)).isEqualTo(1);
        assertThat(attempt(attemptId).isSettlementBlocked()).isFalse();
    }

    @Test
    @DisplayName("환불 필요 수납의 SYSTEM 환불 행이 수납액과 다르면 자동으로 고치지 않고 강한 차단만 남긴다")
    void mismatchedRefundRowsBlock() {
        // given
        activeMembership();
        Long attemptId = failedRenewalAttempt();
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.plusMinutes(1));
        webhook(attemptId);
        jdbc.update("UPDATE membership_refund SET amount = 3800 WHERE membership_payment_attempt_id = ?", attemptId);

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT);
        assertThat(refundRowCount(attemptId)).isEqualTo(1);
    }

    @Test
    @DisplayName("발송된 FAILED의 늦은 수납이 회차 기간 전이거나 미래 시각이면 정정하지 않고 차단한다 — FAILED 이력 유지·환불 0")
    void latePaidWithInvalidTimeBlocksWithoutCorrection() {
        // given
        activeMembership();
        Long beforePeriodId = failedRenewalAttempt();
        PORTONE.getPaymentPaid(attempt(beforePeriodId).getPortonePaymentId(), FEE, "KRW", T.minusMinutes(1));

        // when
        MembershipChargeOutcome beforePeriod = webhook(beforePeriodId);

        // then
        assertThat(beforePeriod).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        MembershipPaymentAttempt blocked = attempt(beforePeriodId);
        assertThat(blocked.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.FAILED);
        assertThat(blocked.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_AT_INVALID);
        assertThat(blocked.getRefundRequiredAt()).isNull();
        assertThat(refundRowCount(beforePeriodId)).isZero();
    }

    @Test
    @DisplayName("발송된 FAILED의 늦은 수납이 서버 시각보다 미래면 정정하지 않고 차단한다")
    void latePaidInFutureBlocksWithoutCorrection() {
        // given
        activeMembership();
        Long attemptId = failedRenewalAttempt();
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.plusHours(5));

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.FAILED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_AT_INVALID);
        assertThat(refundRowCount(attemptId)).isZero();
    }

    @Test
    @DisplayName("환불 대상 수납만 남은 갱신 회차는 닫히고 자동갱신이 꺼져, 환불이 완료된 뒤에도 같은 회차에 새 청구가 생기지 않는다")
    void refundOnlyRoundClosesAndNeverRecharges() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = failedRenewalAttempt();
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.plusMinutes(1));
        MembershipChargeOutcome corrected = webhook(attemptId);
        MembershipPayment closed = onlyRenewalPayment(membershipId);
        PORTONE.defaultPaid(FEE, T.plusHours(24));

        // when
        clock.fixAt(T.plusHours(24));
        renewalFacade.runOnce();
        renewalFacade.settleOnce();
        jdbc.update("UPDATE membership_refund SET status = 'COMPLETED' WHERE membership_payment_attempt_id = ?",
            attemptId);
        clock.fixAt(T.plusHours(48));
        renewalFacade.runOnce();

        // then
        assertThat(corrected).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(closed.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(closed.getFailureReason()).isEqualTo("UNAPPLIED_PAID_ONLY");
        assertThat(membership(membershipId).isAutoRenew()).isFalse();
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
        assertThat(attemptsOf(closed.getId())).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
        assertThat(renewalPaymentIds(membershipId)).hasSize(1);
    }

    @Test
    @DisplayName("수납 정정 중 SYSTEM 환불 저장이 실패하면 정정 전체가 롤백되고, 장애 제거 뒤 같은 재조회로 한 번만 정정된다")
    void refundInsertFailureRollsBackCorrection() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = failedRenewalAttempt();
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.plusMinutes(1));
        jdbc.execute("ALTER TABLE membership_refund ADD CONSTRAINT ck_test_refund_insert_failure CHECK ("
            + "membership_payment_attempt_id <> " + attemptId + ")");
        Throwable thrown;
        try {
            // when
            thrown = org.assertj.core.api.Assertions.catchThrowable(() -> webhook(attemptId));
        } finally {
            jdbc.execute("ALTER TABLE membership_refund DROP CHECK ck_test_refund_insert_failure");
        }
        MembershipPaymentAttempt afterFailure = attempt(attemptId);
        MembershipChargeOutcome recovered = webhook(attemptId);

        // then
        assertThat(thrown).isNotNull();
        assertThat(afterFailure.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.FAILED);
        assertThat(afterFailure.getRefundRequiredAt()).isNull();
        assertThat(afterFailure.getOpsReviewRequiredAt()).isNull();
        assertThat(recovered).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(refundRowCount(attemptId)).isEqualTo(1);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    // ---------- 미발송 시도의 PG 거래 관측 ----------

    @Test
    @DisplayName("PG에 보낸 적 없는 READY의 결제 ID로 수납이 보이면 강한 차단 — 상태 READY 유지·환불 0·연장 0, C-17 미정산")
    void paidOnUndispatchedReadyBlocks() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = readyRenewalAttempt(membershipId);
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.minusMinutes(1));

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        MembershipPaymentAttempt blocked = attempt(attemptId);
        assertThat(blocked.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(blocked.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.UNDISPATCHED_PAID);
        assertThat(blocked.getRefundRequiredAt()).isNull();
        assertThat(refundCountOfMember()).isZero();
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
        assertThat(existsUnresolved()).isTrue();
    }

    @ParameterizedTest(name = "PG 상태 {0}")
    @ValueSource(strings = {"FAILED", "PENDING", "READY"})
    @DisplayName("PG에 보낸 적 없는 READY의 결제 ID로 수납 아닌 PG 거래가 보여도 강한 차단, 수납 금액을 추정하지 않는다")
    void nonPaidPgActivityOnUndispatchedReadyBlocks(String pgStatus) {
        // given
        Long membershipId = activeMembership();
        Long attemptId = readyRenewalAttempt(membershipId);
        PORTONE.getPaymentInStatus(attempt(attemptId).getPortonePaymentId(), pgStatus, FEE);

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        MembershipPaymentAttempt blocked = attempt(attemptId);
        assertThat(blocked.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(blocked.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.UNDISPATCHED_PG_ACTIVITY);
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("READY의 결제 ID가 PG에 없으면(404) 차단하지 않고, 조회 장애는 거래 부재로 확정하지 않아 재시도 결과를 돌려준다")
    void notFoundIsNoPgRecordButServerErrorProvesNothing() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = readyRenewalAttempt(membershipId);
        String paymentId = attempt(attemptId).getPortonePaymentId();

        // when
        PORTONE.getPaymentNotFound(paymentId);
        MembershipChargeOutcome notFound = webhook(attemptId);
        PORTONE.getPaymentServerError(paymentId);
        MembershipChargeOutcome serverError = webhook(attemptId);

        // then
        assertThat(notFound).isEqualTo(MembershipChargeOutcome.NOT_DISPATCHED);
        assertThat(serverError).isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);
        MembershipPaymentAttempt ready = attempt(attemptId);
        assertThat(ready.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(ready.getOpsReviewRequiredAt()).isNull();
    }

    @Test
    @DisplayName("미발송 CANCELED의 결제 ID로 PG FAILED가 보이면 멱등 완료가 아니라 강한 차단, 404일 때만 끝난 사실의 재확인이다")
    void canceledWithPgFailedBlocksButNotFoundIsIdempotent() {
        // given
        Long membershipId = activeMembership();
        Long blockedId = readyRenewalAttempt(membershipId);
        cancelUndispatched(blockedId);
        PORTONE.getPaymentFailed(attempt(blockedId).getPortonePaymentId(), FEE);

        // when
        MembershipChargeOutcome withFailed = webhook(blockedId);

        // then
        assertThat(withFailed).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(attempt(blockedId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(attempt(blockedId).getOpsReviewReason())
            .isEqualTo(MembershipOpsReviewReason.UNDISPATCHED_PG_ACTIVITY);
        assertThat(existsUnresolved()).isTrue();
    }

    @Test
    @DisplayName("미발송 CANCELED의 결제 ID가 PG에 없으면(404) 변경 없이 끝난 사실의 재확인")
    void canceledWithNotFoundIsIdempotent() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = readyRenewalAttempt(membershipId);
        cancelUndispatched(attemptId);
        PORTONE.getPaymentNotFound(attempt(attemptId).getPortonePaymentId());

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(attempt(attemptId).getOpsReviewRequiredAt()).isNull();
    }

    // ---------- 차단 회차의 선점·만료 ----------

    @Test
    @DisplayName("강한 차단 READY는 실행해도 선점·POST 0이고, 임대가 끝나도 자동 CANCELED·새 시도 생성이 없다")
    void blockedReadyIsNeitherClaimedNorExpired() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = readyRenewalAttempt(membershipId);
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.minusMinutes(1));
        webhook(attemptId);
        PORTONE.defaultPaid(FEE, T);

        // when
        MembershipChargeOutcome executed = executor.execute(member.getId(), attemptId);
        clock.fixAt(T.plusHours(1));
        recovery.recoverOnce();

        // then
        assertThat(executed).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(PORTONE.totalPostCount()).isZero();
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(attemptsOf(onlyRenewalPayment(membershipId).getId())).hasSize(1);
    }

    @Test
    @DisplayName("같은 회차의 시도 A가 강한 차단이면 시도 B READY도 선점하지 못한다 — POST 0, 결과는 차단")
    void blockedSiblingStopsOtherReady() {
        // given
        Long membershipId = activeMembership();
        Long blockedId = readyRenewalAttempt(membershipId);
        PORTONE.getPaymentPaid(attempt(blockedId).getPortonePaymentId(), FEE, "KRW", T.minusMinutes(1));
        webhook(blockedId);
        Long siblingId = siblingReady(blockedId);
        PORTONE.defaultPaid(FEE, T);

        // when
        boolean claimed = dispatchProcessor.claim(siblingId).isClaimed();
        MembershipChargeOutcome executed = executor.execute(member.getId(), siblingId);

        // then
        assertThat(claimed).isFalse();
        assertThat(executed).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(PORTONE.totalPostCount()).isZero();
        assertThat(attempt(siblingId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
    }

    @Test
    @DisplayName("같은 회차에 차단 시도가 있으면 다른 시도의 신뢰 가능한 PAID도 반영하지 않는다(TX2 회차 전체 재확인)")
    void blockedSiblingStopsResultApply() {
        // given
        Long membershipId = activeMembership();
        Long blockedId = readyRenewalAttempt(membershipId);
        Long siblingId = siblingReady(blockedId);
        String siblingPaymentId = claim(siblingId);
        PORTONE.getPaymentPaid(attempt(blockedId).getPortonePaymentId(), FEE, "KRW", T.minusMinutes(1));
        webhook(blockedId);
        PORTONE.getPaymentPaid(siblingPaymentId, FEE, "KRW", T.plusMinutes(1));

        // when
        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), siblingId,
            attempt(siblingId).getDispatchedAt(), paid(T.plusMinutes(1)), MembershipReconciliationTrigger.SCHEDULED);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(attempt(siblingId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    // ---------- 정상 반영 PAID의 재전송 ----------

    @Test
    @DisplayName("발송된 FAILED 시도의 재조회가 조회 장애면 끝난 사실로 보지 않고 재시도 결과, 상태는 그대로다")
    void lookupFailureOnFinalAttemptIsRetried() {
        // given
        activeMembership();
        Long attemptId = failedRenewalAttempt();
        PORTONE.getPaymentServerError(attempt(attemptId).getPortonePaymentId());

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.FAILED);
        assertThat(attempt(attemptId).getOpsReviewRequiredAt()).isNull();
        assertThat(refundRowCount(attemptId)).isZero();
    }

    @Test
    @DisplayName("정상 반영 PAID의 같은 근거 재전송은 변경 없음, 다른 결제 시각은 근거 충돌 차단, PG FAILED도 PAID를 내리지 않고 차단")
    void appliedPaidReplay() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);
        renewalFacade.runOnce();
        MembershipPayment paid = onlyRenewalPayment(membershipId);
        Long attemptId = attemptsOf(paid.getId()).get(0).getId();
        String paymentId = attempt(attemptId).getPortonePaymentId();
        LocalDateTime extendedEnd = membership(membershipId).getEndAt();

        // when
        MembershipChargeOutcome same = webhook(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(5));
        MembershipChargeOutcome different = webhook(attemptId);

        // then
        assertThat(same).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(different).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        MembershipPaymentAttempt blocked = attempt(attemptId);
        assertThat(blocked.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(blocked.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(extendedEnd);
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("PAID 시도에 PG FAILED가 오면 PAID를 내리지 않고 근거 충돌로 차단한다")
    void failedVerdictOnPaidAttemptBlocksWithoutDowngrade() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);
        renewalFacade.runOnce();
        Long attemptId = attemptsOf(onlyRenewalPayment(membershipId).getId()).get(0).getId();
        PORTONE.getPaymentFailed(attempt(attemptId).getPortonePaymentId(), FEE);

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT);
    }

    @Test
    @DisplayName("시도는 PAID인데 회차가 미반영이면 같은 근거 재전송도 멱등이 아니라 정합성 차단 — 다시 반영·새 환불 없음")
    void paidAttemptWithUnappliedRoundBlocks() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);
        renewalFacade.runOnce();
        MembershipPayment paid = onlyRenewalPayment(membershipId);
        Long attemptId = attemptsOf(paid.getId()).get(0).getId();
        LocalDateTime extendedEnd = membership(membershipId).getEndAt();
        jdbc.update("UPDATE membership_payment SET membership_applied_at = NULL WHERE id = ?", paid.getId());

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(attempt(attemptId).getOpsReviewReason())
            .isEqualTo(MembershipOpsReviewReason.APPLIED_STATE_INCONSISTENT);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(extendedEnd);
        assertThat(refundCountOfMember()).isZero();
        assertThat(payments.findById(paid.getId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.PAID);
    }

    // ---------- 운영자 재조회 ----------

    @Test
    @DisplayName("차단 시도의 운영자 재조회는 GET 1회로 관측값을 돌려주고 반영하지 않는다")
    void adminRecheckOnBlockedReturnsObservationWithoutApply() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = readyRenewalAttempt(membershipId);
        String paymentId = attempt(attemptId).getPortonePaymentId();
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.minusMinutes(1));
        webhook(attemptId);
        int getsBefore = PORTONE.getCount(paymentId);

        // when
        MembershipReconciliationResult result = executor.recheckForAdmin(member.getId(), attemptId);

        // then
        assertThat(result.outcome()).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(result.verdict().kind()).isEqualTo(MembershipPgVerdict.Kind.PAID);
        assertThat(result.verdict().pgPaidAt()).isEqualTo(T.minusMinutes(1));
        assertThat(result.verdict().paidAmount()).isEqualTo(FEE);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore + 1);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("복구 Sweep은 차단 회차를 다시 조회하지 않는다")
    void sweepSkipsBlockedRound() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = readyRenewalAttempt(membershipId);
        Long siblingId = siblingReady(attemptId);
        String siblingPaymentId = claim(siblingId);
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.minusMinutes(1));
        webhook(attemptId);
        clock.fixAt(T.plusHours(1));

        // when
        recovery.recoverOnce();

        // then
        assertThat(PORTONE.getCount(siblingPaymentId)).isZero();
        assertThat(attempt(siblingId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
    }

    // ---------- 보조 ----------

    private MembershipChargeOutcome webhook(Long attemptId) {
        return executor.reconcile(member.getId(), attemptId, MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
    }

    // 접수만 한 갱신 READY(선점·POST 없음)
    private Long readyRenewalAttempt(Long membershipId) {
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        return attemptId;
    }

    // 발송 뒤 재조회로 확정된 실패 시도(회차는 재시도 대기)
    private Long failedRenewalAttempt() {
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        PORTONE.clearDefault();
        Long paymentId = renewalPaymentIds(attemptMembershipId()).get(0);
        MembershipPaymentAttempt failed = attemptsOf(paymentId).get(0);
        assertThat(failed.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.FAILED);
        assertThat(failed.isDispatched()).isTrue();
        // 늦은 수납의 결제 시각(T + 1분)이 판정 시각보다 미래가 되지 않게 시계를 그 뒤로 둔다
        clock.fixAt(T.plusMinutes(10));
        return failed.getId();
    }

    private Long attemptMembershipId() {
        return jdbc.queryForObject("SELECT id FROM membership WHERE member_id = ? ORDER BY id DESC LIMIT 1", Long.class,
            member.getId());
    }

    // 테스트 픽스처: 임대가 끝난 READY를 미발송 CANCELED로 접은 것과 같은 행(dispatchedAt 없음)
    private void cancelUndispatched(Long attemptId) {
        jdbc.update("UPDATE membership_payment_attempt SET status = 'CANCELED', failure_reason = 'NOT_DISPATCHED', "
            + "completed_at = ? WHERE id = ? AND dispatched_at IS NULL", LocalDateTime.now(clock), attemptId);
    }

    // 테스트 픽스처: 같은 회차의 두 번째 READY(차단 뒤에는 운영 경로로 만들 수 없어 직접 저장한다)
    private Long siblingReady(Long attemptId) {
        MembershipPaymentAttempt first = attempt(attemptId);
        MembershipPayment payment = payments.findById(first.getMembershipPayment().getId()).orElseThrow();
        return attempts.saveAndFlush(MembershipPaymentAttempt.ready(payment, first.getAttemptNo() + 1, cardA,
            LocalDateTime.now(clock), Duration.ofMinutes(10))).getId();
    }

    private MembershipPgVerdict paid(LocalDateTime pgPaidAt) {
        return new MembershipPgVerdict(MembershipPgVerdict.Kind.PAID, pgPaidAt, FEE, null, true);
    }

    private long refundRowCount(Long attemptId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM membership_refund WHERE membership_payment_attempt_id = ?",
            Long.class, attemptId);
    }

    private boolean existsUnresolved() {
        return Boolean.TRUE.equals(transactionTemplate.execute(s -> {
            memberService.lockActiveMember(member.getId());
            List<Long> ids = billing.findMembershipIds(member.getId());
            return queryService.existsUnresolved(ids);
        }));
    }
}
