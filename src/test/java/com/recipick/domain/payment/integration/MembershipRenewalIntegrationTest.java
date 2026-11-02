package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.facade.MembershipRenewalProcessor.CloseResult;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipChargeTicket;
import com.recipick.domain.payment.service.MembershipPgVerdict;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.MembershipRenewalDecision;
import com.recipick.domain.payment.service.MembershipRenewalDecision.Kind;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;

// 월 갱신·재시도·카드 대기·마감 정리·갱신 결과 반영을 실제 실행기와 PG 스텁까지 타며 본다. 시도 행 수와 결제 ID별 POST·GET 수로
// "같은 기간 회차 1개·새 ID POST 없음·PG 발송 3회"를 확인한다. 실제 다중 스레드 경합은 MembershipRenewalConcurrencyTest가 본다
class MembershipRenewalIntegrationTest extends MembershipRenewalFlowSupport {

    // ---------- 정상 갱신·기간 ----------

    @Test
    @DisplayName("1/31 기준 이용권은 2/28 → 3/31 → 4/30으로 연장되고 같은 시각의 재실행은 회차·POST·연장을 늘리지 않는다")
    void renewsOnOriginalAnchor() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);

        // when
        renewalFacade.runOnce();
        renewalFacade.runOnce();
        clock.fixAt(E);
        PORTONE.defaultPaid(FEE, E);
        renewalFacade.runOnce();

        // then
        Membership membership = membership(membershipId);
        assertThat(membership.getEndAt()).isEqualTo(LocalDateTime.of(2027, 4, 30, 10, 3));
        assertThat(membership.getGraceEndAt()).isEqualTo(LocalDateTime.of(2027, 5, 3, 10, 3));
        assertThat(membership.getBillingAnchorDate()).isEqualTo(FIRST_PAID_AT.toLocalDate());
        List<Long> paymentIds = renewalPaymentIds(membershipId);
        assertThat(paymentIds).hasSize(2);
        MembershipPayment first = payments.findById(paymentIds.get(0)).orElseThrow();
        assertThat(first.getStatus()).isEqualTo(MembershipPaymentStatus.PAID);
        assertThat(first.getPeriodStartDate()).isEqualTo(T.toLocalDate());
        assertThat(first.getPeriodEndDate()).isEqualTo(E.toLocalDate().minusDays(1));
        assertThat(first.getGraceDeadlineAt()).isEqualTo(G);
        assertThat(first.getMembershipAppliedAt()).isEqualTo(T);
        assertThat(PORTONE.totalPostCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("갱신 시도의 선점 티켓 마감은 회차의 결제 마감 G다")
    void claimTicketUsesGraceDeadline() {
        // given
        activeMembership();
        MembershipRenewalDecision accepted = renewalProcessor.accept(member.getId(), onlyActive());

        // when
        MembershipChargeTicket ticket = dispatchProcessor.claim(accepted.attemptId()).ticket().orElseThrow();

        // then
        assertThat(ticket.acceptUntil()).isEqualTo(G);
    }

    // ---------- 실패·재시도 ----------

    @Test
    @DisplayName("확정 실패 뒤 다음 청구는 실패 확정 + 24시간 — 1초 전에는 POST 0, 정각에 같은 회차의 두 번째 시도 1건")
    void retriesExactlyAtInterval() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        MembershipPayment waiting = onlyRenewalPayment(membershipId);

        // when
        clock.fixAt(T.plusHours(24).minusSeconds(1));
        renewalFacade.runOnce();
        int postsBefore = PORTONE.totalPostCount();
        clock.fixAt(T.plusHours(24));
        renewalFacade.runOnce();

        // then
        assertThat(waiting.getStatus()).isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(waiting.getNextRetryAt()).isEqualTo(T.plusHours(24));
        assertThat(postsBefore).isEqualTo(1);
        assertThat(PORTONE.totalPostCount()).isEqualTo(2);
        List<MembershipPaymentAttempt> all = attemptsOf(waiting.getId());
        assertThat(all).extracting(MembershipPaymentAttempt::getAttemptNo).containsExactly(1, 2);
        assertThat(renewalPaymentIds(membershipId)).hasSize(1);
    }

    @Test
    @DisplayName("세 번 확정 실패하면 회차 FAILED·자동갱신 꺼짐, 유예 종료 시각과 종료 시각은 그대로")
    void threeFailuresStopRenewalKeepGrace() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);

        // when
        renewalFacade.runOnce();
        clock.fixAt(T.plusHours(24));
        renewalFacade.runOnce();
        clock.fixAt(T.plusHours(48));
        renewalFacade.runOnce();

        // then
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("RENEWAL_ATTEMPTS_EXHAUSTED");
        assertThat(payment.getNextRetryAt()).isNull();
        Membership membership = membership(membershipId);
        assertThat(membership.isAutoRenew()).isFalse();
        assertThat(membership.getEndAt()).isEqualTo(T);
        assertThat(membership.getGraceEndAt()).isEqualTo(G);
        assertThat(PORTONE.totalPostCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("세 번째가 결과 불명이면 G가 지나도 회차를 실패로 닫지 않는다(자동갱신 유지, 재조회 대상)")
    void unknownAtDeadlineKeepsPaymentOpen() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        clock.fixAt(T.plusHours(24));
        renewalFacade.runOnce();
        PORTONE.clearDefault();
        clock.fixAt(T.plusHours(48));
        renewalFacade.runOnce();

        // when
        clock.fixAt(G);
        renewalFacade.settleOnce();

        // then
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.UNKNOWN);
        assertThat(attemptsOf(payment.getId())).last().extracting(MembershipPaymentAttempt::getStatus)
            .isEqualTo(MembershipPaymentAttemptStatus.UNKNOWN);
        assertThat(membership(membershipId).isAutoRenew()).isTrue();
        assertThat(PORTONE.totalPostCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 FAILED를 다시 받아도 처음 정한 재시도 시각과 시도 수가 바뀌지 않는다")
    void sameFailedReplayDoesNotMoveRetry() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        Long attemptId = attemptsOf(payment.getId()).get(0).getId();

        // when
        clock.fixAt(T.plusHours(3));
        MembershipChargeOutcome reconciled = executor.reconcile(member.getId(), attemptId);
        MembershipChargeOutcome applied = applyResult(member.getId(), attemptId,
            new MembershipPgVerdict(MembershipPgVerdict.Kind.FAILED, null, null, "PG_FAILED", true));

        // then
        assertThat(reconciled).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(applied).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        MembershipPayment after = payments.findById(payment.getId()).orElseThrow();
        assertThat(after.getNextRetryAt()).isEqualTo(T.plusHours(24));
        assertThat(attempt(attemptId).getCompletedAt()).isEqualTo(T);
        assertThat(attemptsOf(payment.getId())).hasSize(1);
    }

    @Test
    @DisplayName("ON 접수 → OFF 만료 → ON 재개를 두 번 거쳐도 미발송 CANCELED는 PG 예산을 쓰지 않는다(행 5·발송 3)")
    void canceledReadiesDoNotConsumeDispatchBudget() {
        // given
        Long membershipId = activeMembership();
        Long paymentId = renewalProcessor.accept(member.getId(), membershipId).membershipPaymentId();

        // when
        gate.set(false);
        clock.fixAt(T.plusMinutes(11));
        recovery.recoverOnce();
        MembershipPaymentStatus afterFirstExpiry = payments.findById(paymentId).orElseThrow().getStatus();
        gate.set(true);
        MembershipRenewalDecision second = renewalProcessor.resumeExisting(member.getId(), membershipId, paymentId);
        gate.set(false);
        clock.fixAt(T.plusMinutes(22));
        recovery.recoverOnce();
        gate.set(true);
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        clock.fixAt(T.plusMinutes(22).plusHours(24));
        renewalFacade.runOnce();
        clock.fixAt(T.plusMinutes(22).plusHours(48));
        renewalFacade.runOnce();

        // then
        assertThat(afterFirstExpiry).isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(second.kind()).isEqualTo(Kind.CHARGE_READY);
        List<MembershipPaymentAttempt> all = attemptsOf(paymentId);
        assertThat(all).extracting(MembershipPaymentAttempt::getStatus).containsExactly(
            MembershipPaymentAttemptStatus.CANCELED, MembershipPaymentAttemptStatus.CANCELED,
            MembershipPaymentAttemptStatus.FAILED, MembershipPaymentAttemptStatus.FAILED,
            MembershipPaymentAttemptStatus.FAILED);
        assertThat(PORTONE.totalPostCount()).isEqualTo(3);
        MembershipPayment payment = payments.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("RENEWAL_ATTEMPTS_EXHAUSTED");
    }

    @Test
    @DisplayName("미발송 만료로 남은 PENDING은 재시도 대기로 바로잡힌 뒤 가드를 통과해 같은 회차에서 다시 청구한다")
    void pendingAfterUnsentExpiryIsNormalizedBeforeRetryGuard() {
        // given
        Long membershipId = activeMembership();
        Long paymentId = renewalProcessor.accept(member.getId(), membershipId).membershipPaymentId();
        clock.fixAt(T.plusMinutes(11));
        PORTONE.defaultPaid(FEE, T.plusMinutes(11));

        // when
        recovery.recoverOnce();

        // then
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.PAID);
        assertThat(attemptsOf(paymentId)).extracting(MembershipPaymentAttempt::getStatus)
            .containsExactly(MembershipPaymentAttemptStatus.CANCELED, MembershipPaymentAttemptStatus.PAID);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정규화 뒤에도 같은 이용권의 다른 미해결 회차가 있으면 새 시도를 만들지 않는다")
    void normalizedPendingStillBlockedByOtherUnsettledPayment() {
        // given
        Long membershipId = activeMembership();
        Long paymentId = renewalProcessor.accept(member.getId(), membershipId).membershipPaymentId();
        time.otherPayment(membershipId, FEE, MembershipPaymentStatus.UNKNOWN, FIRST_PAID_AT.toLocalDate().minusDays(5),
            FIRST_PAID_AT, null, null);
        clock.fixAt(T.plusMinutes(11));

        // when
        recovery.recoverOnce();

        // then
        assertThat(payments.findById(paymentId).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(attemptsOf(paymentId)).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    // ---------- 해지·마감 정리 ----------

    @Test
    @DisplayName("예약 없는 재시도 대기(null)에 해지 예약이 오면 플래그 OFF여도 G 전 정리에서 닫힌다(POST 추가 없음)")
    void canceledWaitingWithNullRetryIsClosedWhileFlagOff() {
        // given
        Long membershipId = activeMembership();
        clock.fixAt(T.plusHours(48).plusMinutes(1));
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        MembershipPayment waiting = onlyRenewalPayment(membershipId);
        cancelReservation.reserve(member.getId(), membershipId);
        gate.set(false);

        // when
        renewalFacade.settleOnce();

        // then
        assertThat(waiting.getStatus()).isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(waiting.getNextRetryAt()).isNull();
        MembershipPayment closed = payments.findById(waiting.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(closed.getFailureReason()).isEqualTo("RENEWAL_CANCELED");
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 시각이 미래여도 해지된 대기 회차는 예약 시각을 기다리지 않고 정리된다")
    void cancellationPrecedesFutureRetryWait() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        clock.fixAt(T.plusHours(1));
        cancelReservation.reserve(member.getId(), membershipId);

        // when
        renewalFacade.settleOnce();

        // then
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("RENEWAL_CANCELED");
        assertThat(membership(membershipId).getCancelReservedAt()).isEqualTo(T.plusHours(1));
    }

    @Test
    @DisplayName("임대가 G에서 끝나는 READY는 G에 선점되지 않고(POST 0) 정리 작업이 취소·회차 FAILED·자동갱신 끔")
    void readyAtDeadlineIsNotDispatchedAndSettled() {
        // given
        Long membershipId = activeMembership();
        clock.fixAt(G.minusMinutes(5));
        MembershipRenewalDecision accepted = renewalProcessor.accept(member.getId(), membershipId);
        clock.fixAt(G);

        // when
        MembershipChargeOutcome outcome = executor.execute(member.getId(), accepted.attemptId());
        renewalFacade.settleOnce();

        // then
        assertThat(attempt(accepted.attemptId()).getLeaseExpiresAt()).isEqualTo(G);
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.NOT_DISPATCHED);
        assertThat(PORTONE.totalPostCount()).isZero();
        assertThat(attempt(accepted.attemptId()).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("RENEWAL_DEADLINE_PASSED");
        assertThat(membership(membershipId).isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("실패를 반영할 때 남아 있던 만료 READY를 먼저 취소하고, 마감이 지났으면 회차를 닫는다")
    void expiredReadyIsCanceledBeforeFinalFailure() {
        // given
        Long membershipId = activeMembership();
        Long dispatchedId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(dispatchedId);
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        MembershipPaymentAttempt strayReady = attempts.saveAndFlush(MembershipPaymentAttempt.builder()
            .membershipPayment(payment).attemptNo(2).billingKey(cardA).portonePaymentId(
                MembershipPaymentAttempt.newPaymentId()).status(MembershipPaymentAttemptStatus.READY)
            .requestedAt(T).leaseExpiresAt(T.plusMinutes(10)).build());
        PORTONE.getPaymentFailed(paymentId, FEE);
        clock.fixAt(G.plusMinutes(1));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), dispatchedId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.RENEWAL_STOPPED);
        assertThat(attempt(strayReady.getId()).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(payments.findById(payment.getId()).orElseThrow().getFailureReason())
            .isEqualTo("RENEWAL_DEADLINE_PASSED");
    }

    @Test
    @DisplayName("READY 접수 뒤 해지 예약하고 임대가 끝나면 새 시도로 대체하지 않고 회차를 닫는다")
    void canceledMembershipDoesNotReissueExpiredReady() {
        // given
        Long membershipId = activeMembership();
        MembershipRenewalDecision accepted = renewalProcessor.accept(member.getId(), membershipId);
        cancelReservation.reserve(member.getId(), membershipId);
        clock.fixAt(T.plusMinutes(11));

        // when
        recovery.recoverOnce();

        // then
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        assertThat(attemptsOf(payment.getId())).hasSize(1);
        assertThat(attempt(accepted.attemptId()).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("RENEWAL_CANCELED");
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    @Test
    @DisplayName("해지 예약이 먼저면 회차를 만들지 않고, READY 접수 뒤의 예약은 그 시도를 처리해 연장하되 예약·유예 없음을 유지한다")
    void cancelReservationBeforeAndAfterAccept() {
        // given
        Long membershipId = activeMembership();
        MembershipRenewalDecision accepted = renewalProcessor.accept(member.getId(), membershipId);
        cancelReservation.reserve(member.getId(), membershipId);
        PORTONE.defaultPaid(FEE, T);

        // when
        MembershipChargeOutcome outcome = executor.execute(member.getId(), accepted.attemptId());

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.EXTENDED);
        Membership membership = membership(membershipId);
        assertThat(membership.getEndAt()).isEqualTo(E);
        assertThat(membership.getGraceEndAt()).isNull();
        assertThat(membership.isAutoRenew()).isFalse();
        assertThat(membership.getCancelReservedAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("해지 예약이 접수보다 먼저면 C-2 후보에서 빠져 회차·POST가 생기지 않는다")
    void reservationBeforeAcceptCreatesNothing() {
        // given
        Long membershipId = activeMembership();
        cancelReservation.reserve(member.getId(), membershipId);
        PORTONE.defaultPaid(FEE, T);

        // when
        renewalFacade.runOnce();

        // then
        assertThat(renewalPaymentIds(membershipId)).isEmpty();
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    // ---------- 카드 대기·교체 ----------


    @Test
    @DisplayName("처음부터 카드가 없으면 카드 대기 회차(시도 0)가 되고, 등록 뒤 재개는 같은 회차에서 한 번만 청구한다")
    void noCardThenRegisterChargesOnce() {
        // given
        Long membershipId = activeMembership();
        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.REPLACED);
        renewalFacade.runOnce();

        MembershipPayment waiting = onlyRenewalPayment(membershipId);

        // 카드 등록 전: 결제수단 대기 상태
        assertThat(waiting.getStatus())
            .isEqualTo(MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED);

        saveActiveKey(member.getId());
        PORTONE.defaultPaid(FEE, T);

        // when
        renewalFacade.resumeForMember(member.getId());
        renewalFacade.resumeForMember(member.getId());

        // then
        // 재개 후 DB에서 최신 회차 상태를 다시 조회
        assertThat(onlyRenewalPayment(membershipId).getStatus())
            .isEqualTo(MembershipPaymentStatus.PAID);

        assertThat(attemptsOf(waiting.getId())).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
    }


    @Test
    @DisplayName("카드 등록 뒤 재개 신호가 없어도 다음 갱신 실행이 같은 카드 대기 회차를 재개한다")
    void pollingResumesLostSignal() {
        // given
        Long membershipId = activeMembership();
        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.REPLACED);
        renewalFacade.runOnce();
        saveActiveKey(member.getId());
        PORTONE.defaultPaid(FEE, T);

        // when
        renewalFacade.runOnce();

        // then
        assertThat(onlyRenewalPayment(membershipId).getStatus()).isEqualTo(MembershipPaymentStatus.PAID);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 뒤 카드를 바꿔도 24시간 예약을 앞당기지 않고, 예약 시각의 새 시도는 새 카드로 청구한다")
    void cardReplacementDoesNotAdvanceRetry() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.REPLACED);
        BillingKey cardB = saveActiveKey(member.getId());

        // when
        clock.fixAt(T.plusHours(1));
        renewalFacade.resumeForMember(member.getId());
        renewalFacade.runOnce();
        int postsBeforeRetry = PORTONE.totalPostCount();
        clock.fixAt(T.plusHours(24));
        PORTONE.defaultPaid(FEE, T.plusHours(24));
        renewalFacade.runOnce();

        // then
        assertThat(postsBeforeRetry).isEqualTo(1);
        List<MembershipPaymentAttempt> all = attemptsOf(onlyRenewalPayment(membershipId).getId());
        assertThat(all).hasSize(2);
        assertThat(billingKeyIdOf(all.get(0).getId())).isEqualTo(cardA.getId());
        assertThat(billingKeyIdOf(all.get(1).getId())).isEqualTo(cardB.getId());
    }

    @Test
    @DisplayName("카드 A로 접수된 READY는 B를 등록한 뒤에도 A로 실행되고 A 시도의 키는 바뀌지 않는다")
    void acceptedCardAIsKeptAfterCardBRegistration() {
        // given
        Long membershipId = activeMembership();
        MembershipRenewalDecision accepted = renewalProcessor.accept(member.getId(), membershipId);
        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.REPLACED);
        saveActiveKey(member.getId());
        PORTONE.defaultPaid(FEE, T);

        // when
        MembershipChargeOutcome outcome = executor.execute(member.getId(), accepted.attemptId());

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.EXTENDED);
        assertThat(billingKeyIdOf(accepted.attemptId())).isEqualTo(cardA.getId());
    }

    @Test
    @DisplayName("조회해 둔 옛 카드 대기 회차가 그 사이 해결됐으면 재개는 거기서 끝나고 다음 기간 회차·POST를 만들지 않는다")
    void staleResumeReferenceNeverCreatesNextPeriod() {
        // given
        Long membershipId = activeMembership();
        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.REPLACED);
        renewalFacade.runOnce();
        Long staleId = onlyRenewalPayment(membershipId).getId();
        saveActiveKey(member.getId());
        PORTONE.defaultPaid(FEE, T);
        renewalFacade.runOnce();

        // when
        MembershipRenewalDecision again = renewalProcessor.resumeExisting(member.getId(), membershipId, staleId);

        // then
        assertThat(again.kind()).isEqualTo(Kind.ALREADY_RESOLVED);
        assertThat(renewalPaymentIds(membershipId)).containsExactly(staleId);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    // ---------- 늦은 결과·경계 ----------

    @Test
    @DisplayName("pgPaidAt = G면 마감 뒤 수납이라 연장 없이 환불 요청(LATE_PAID_AFTER_DEADLINE)·자동갱신 끔")
    void paidAtDeadlineBoundary() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", G);
        clock.fixAt(G.plusHours(1));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        MembershipPaymentAttempt paid = attempt(attemptId);
        assertThat(paid.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.LATE_PAID_AFTER_DEADLINE);
        assertThat(onlyRenewalPayment(membershipId).getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(membership(membershipId).isAutoRenew()).isFalse();
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("pgPaidAt = G − 1µs면 마감 안 수납이라 연장하고, 확인이 24시간 넘게 늦었으면 지연 표시만 남긴다")
    void paidJustBeforeDeadlineExtends() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", G.minusNanos(1_000));
        clock.fixAt(G.plusDays(1));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.EXTENDED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.RENEWAL_LONG_DELAY);
    }

    @Test
    @DisplayName("확인 시각 = E면 기간 종료라 연장 없이 환불 요청(RENEWAL_PERIOD_ENDED)")
    void confirmedAtPeriodEndBoundary() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusHours(1));
        clock.fixAt(E);

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.RENEWAL_PERIOD_ENDED);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("확인 시각 = E − 1µs면 연장한다")
    void confirmedJustBeforePeriodEndExtends() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusHours(1));
        clock.fixAt(E.minusNanos(1_000));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.EXTENDED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
    }

    @Test
    @DisplayName("PG 결제 시각이 지금보다 1µs라도 미래면 연장·환불 없이 운영 확인, 지금과 같으면 정상 연장")
    void futurePaidAtDoesNotExtendOrRefundAutomatically() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        LocalDateTime now = T.plusMinutes(30);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", now.plusNanos(1_000));
        clock.fixAt(now);

        // when
        MembershipChargeOutcome future = executor.reconcile(member.getId(), attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", now);
        MembershipChargeOutcome manual = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(future).isEqualTo(MembershipChargeOutcome.MANUAL_REVIEW);
        assertThat(manual).isEqualTo(MembershipChargeOutcome.MANUAL_REVIEW);
        assertThat(attempt(attemptId).getFailureReason()).isEqualTo("PAID_AT_IN_FUTURE");
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("PG 결제 시각이 지금과 같으면 정상 연장한다")
    void paidAtEqualToNowExtends() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        LocalDateTime now = T.plusMinutes(30);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", now);
        clock.fixAt(now);

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.EXTENDED);
    }

    @Test
    @DisplayName("결과 불명 중 해지 예약 → G 뒤 PAID 확인(pgPaidAt < G)은 저장된 G로 판정해 연장하고 예약·유예 없음은 그대로")
    void latePaidAfterCancelReservationUsesStoredDeadline() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        clock.fixAt(T.plusHours(1));
        cancelReservation.reserve(member.getId(), membershipId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(G.plusHours(1));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.EXTENDED);
        Membership membership = membership(membershipId);
        assertThat(membership.getEndAt()).isEqualTo(E);
        assertThat(membership.getGraceEndAt()).isNull();
        assertThat(membership.isAutoRenew()).isFalse();
        assertThat(membership.getCancelReservedAt()).isEqualTo(T.plusHours(1));
    }

    @Test
    @DisplayName("KRW 금액 불일치는 실제 수납액을 환불 요청하고 연장하지 않으며, USD는 환불액을 만들지 않고 운영 확인")
    void amountMismatchAndForeignCurrency() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, 3_800L, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusHours(1));

        // when
        MembershipChargeOutcome mismatch = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(mismatch).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(3_800L);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
        assertThat(membership(membershipId).isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("USD PAID는 환불액을 만들지 않고 시도·회차를 운영 확인으로 둔다")
    void foreignCurrencyGoesToManualReview() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "USD", T.plusMinutes(1));
        clock.fixAt(T.plusHours(1));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.MANUAL_REVIEW);
        assertThat(refundCountOfMember()).isZero();
        assertThat(onlyRenewalPayment(membershipId).getStatus()).isEqualTo(MembershipPaymentStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("운영 확인 시도는 자동 재조회하지 않고, 신뢰 가능한 FAILED가 들어오면 그때 재시도 대기로 돌아간다")
    void verifiedFailedCanLeaveManualReviewForRetry() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "USD", T.plusMinutes(1));
        clock.fixAt(T.plusHours(1));
        executor.reconcile(member.getId(), attemptId);
        int getsAfterReview = PORTONE.getCount(paymentId);

        // when
        MembershipChargeOutcome polled = executor.reconcile(member.getId(), attemptId);
        MembershipChargeOutcome failed = applyResult(member.getId(), attemptId,
            new MembershipPgVerdict(MembershipPgVerdict.Kind.FAILED, null, null, "PG_FAILED", true));

        // then
        assertThat(polled).isEqualTo(MembershipChargeOutcome.MANUAL_REVIEW);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsAfterReview);
        assertThat(failed).isEqualTo(MembershipChargeOutcome.RETRY_WAITING);
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(payment.getNextRetryAt()).isEqualTo(T.plusHours(25));
    }

    @Test
    @DisplayName("활성화 표시가 없는(비정상) 이용권에 온 수납은 C-4b 없이 환불 요청만 남고 이용권은 그대로다")
    void conflictingInactiveMembershipStillRecordsRefundRequest() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        time.membershipActivationCleared(membershipId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusHours(1));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_CONFLICT);
        assertThat(membership(membershipId).isAutoRenew()).isTrue();
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("확정 PAID 회차의 다른 시도 수납은 그 수납만 환불하고 현재 이용권은 그대로, 같은 PAID 재전송은 추가 없음")
    void closedPaymentReceiptDoesNotStopCurrentRenewal() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);
        renewalFacade.runOnce();
        MembershipPayment paid = onlyRenewalPayment(membershipId);
        MembershipPaymentAttempt stray = attempts.saveAndFlush(MembershipPaymentAttempt.builder()
            .membershipPayment(paid).attemptNo(2).billingKey(cardA)
            .portonePaymentId(MembershipPaymentAttempt.newPaymentId()).status(MembershipPaymentAttemptStatus.UNKNOWN)
            .requestedAt(T).leaseExpiresAt(T.plusMinutes(10)).dispatchedAt(T).build());
        PORTONE.getPaymentPaid(stray.getPortonePaymentId(), FEE, "KRW", T.plusMinutes(2));
        clock.fixAt(T.plusHours(1));

        // when
        MembershipChargeOutcome first = executor.reconcile(member.getId(), stray.getId());
        MembershipChargeOutcome replay = applyResult(member.getId(), stray.getId(),
            new MembershipPgVerdict(MembershipPgVerdict.Kind.PAID, T.plusMinutes(2), FEE, null, true));

        // then
        assertThat(first).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(replay).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(systemRefundAmounts(stray.getId())).containsExactly(FEE);
        assertThat(attempt(stray.getId()).getOpsReviewReason())
            .isEqualTo(MembershipOpsReviewReason.PAID_ON_CLOSED_PAYMENT);
        Membership membership = membership(membershipId);
        assertThat(membership.isAutoRenew()).isTrue();
        assertThat(membership.getEndAt()).isEqualTo(E);
    }

    @Test
    @DisplayName("발송된 FAILED 시도에 신뢰 가능한 PAID가 오면 연장하지 않고 수납을 정정해 실제 수납액을 돌려준다")
    void latePaidOnFailedAttemptIsRefundedNotExtended() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        Long attemptId = attemptsOf(onlyRenewalPayment(membershipId).getId()).get(0).getId();

        // when
        MembershipChargeOutcome outcome = applyResult(member.getId(), attemptId,
            new MembershipPgVerdict(MembershipPgVerdict.Kind.PAID, T, FEE, null, true));

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("미해결 회차에 설명되지 않는 PAID 시도가 남아 있으면 마감이 지나도 FAILED로 덮지 않는다(C-17 차단 유지)")
    void paidChildOnUnresolvedPaymentDoesNotUnlockSettlement() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        MembershipPayment waiting = onlyRenewalPayment(membershipId);
        attempts.saveAndFlush(MembershipPaymentAttempt.builder()
            .membershipPayment(waiting).attemptNo(2).billingKey(cardA)
            .portonePaymentId(MembershipPaymentAttempt.newPaymentId()).status(MembershipPaymentAttemptStatus.PAID)
            .requestedAt(T).leaseExpiresAt(T.plusMinutes(10)).dispatchedAt(T).completedAt(T).pgPaidAt(T).build());
        clock.fixAt(G.plusMinutes(1));

        // when
        CloseResult result = renewalProcessor.settleExisting(member.getId(), membershipId, waiting.getId());

        // then
        assertThat(result.outcome()).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(payments.findById(waiting.getId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.RETRY_WAITING);
        assertThat(membership(membershipId).isAutoRenew()).isTrue();
    }

    // ---------- 원자성·정합성 ----------

    @Test
    @DisplayName("이용권 종료 시각이 회차 T와 다르면 C-4a 전에 거절·전체 롤백, 실행기가 원인을 재검증해 강한 차단을 남긴다")
    void renewalBaseMismatchHaltsWithoutMutation() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        time.membershipEndAt(membershipId, T.plusHours(1));
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusHours(2));

        // when
        MembershipChargeOutcome first = executor.reconcile(member.getId(), attemptId);
        recovery.recoverOnce();
        MembershipChargeOutcome afterFlag = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(first).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        MembershipPaymentAttempt halted = attempt(attemptId);
        assertThat(halted.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(halted.isSettlementBlocked()).isTrue();
        assertThat(onlyRenewalPayment(membershipId).getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T.plusHours(1));
        assertThat(refundCountOfMember()).isZero();
        assertThat(afterFlag).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
    }

    @Test
    @DisplayName("실제 C-4a 뒤 DB 저장이 실패하면 시도·회차·이용권이 모두 롤백되고, 장애 제거 뒤 같은 결제 ID 재조회로 한 번만 반영된다")
    void renewalFlushFailureRollsBackAndSameIdRecovers() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        PORTONE.defaultPaid(FEE, T);
        jdbc.execute("ALTER TABLE membership_payment ADD CONSTRAINT ck_test_renewal_paid_failure CHECK ("
            + "membership_id <> " + membershipId + " OR grace_deadline_at IS NULL OR status <> 'PAID')");
        Throwable thrown;
        try {
            // when
            thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> executor.execute(member.getId(), attemptId));
        } finally {
            jdbc.execute("ALTER TABLE membership_payment DROP CHECK ck_test_renewal_paid_failure");
        }
        MembershipPaymentAttemptStatus afterFailure = attempt(attemptId).getStatus();
        MembershipPaymentStatus paymentAfterFailure = onlyRenewalPayment(membershipId).getStatus();
        LocalDateTime endAfterFailure = membership(membershipId).getEndAt();
        clock.fixAt(T.plusMinutes(13));
        MembershipChargeOutcome recovered = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(thrown).isNotNull();
        assertThat(rootMessage(thrown)).contains("ck_test_renewal_paid_failure");
        assertThat(afterFailure).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(paymentAfterFailure).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(endAfterFailure).isEqualTo(T);
        assertThat(recovered).isEqualTo(MembershipChargeOutcome.EXTENDED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    // ---------- 플래그·트랜잭션 경계 ----------

    @Test
    @DisplayName("플래그 OFF면 신규 접수·재개·POST가 없고, 해지된 대기 회차 정리와 오래된 시도 재조회는 계속 돈다")
    void gateOffLeavesSettlementAndRecoveryRunning() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentFailed(paymentId, FEE);
        gate.set(false);
        clock.fixAt(T.plusMinutes(13));

        // when
        int accepted = renewalFacade.runOnce();
        recovery.recoverOnce();
        cancelReservation.reserve(member.getId(), membershipId);
        renewalFacade.settleOnce();

        // then
        assertThat(accepted).isZero();
        assertThat(PORTONE.totalPostCount()).isZero();
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.FAILED);
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("RENEWAL_CANCELED");
    }

    @Test
    @DisplayName("갱신 진입점은 바깥 트랜잭션 안에서 부르면 PG 전에 거절된다(실제 프록시)")
    void renewalEntrypointsRejectOuterTransaction() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);

        // when / then
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s -> renewalFacade.runOnce()))
            .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s -> renewalFacade.settleOnce()))
            .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
            s -> renewalFacade.resumeForMember(member.getId()))).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(renewalPaymentIds(membershipId)).isEmpty();
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    private Long onlyActive() {
        return jdbc.queryForObject("SELECT id FROM membership WHERE member_id = ?", Long.class, member.getId());
    }

    private static String rootMessage(Throwable thrown) {
        Throwable cause = thrown;
        StringBuilder messages = new StringBuilder();
        while (cause != null) {
            messages.append(cause.getMessage()).append('\n');
            cause = cause.getCause();
        }
        return messages.toString();
    }

    // 결과 반영을 직접 부를 때 실행기와 같은 관측값(GET 전에 본 선점 시각)을 넘긴다
    private MembershipChargeOutcome applyResult(Long memberId, Long attemptId, MembershipPgVerdict verdict) {
        return resultProcessor.apply(memberId, attemptId, attempts.findById(attemptId).orElseThrow().getDispatchedAt(),
            verdict, MembershipReconciliationTrigger.SCHEDULED);
    }
}
