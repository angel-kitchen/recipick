package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.facade.BillingKeyReconciliationFacade;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor.ExpiredReadyResult;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.MembershipRenewalDecision;
import com.recipick.domain.payment.service.MembershipRequestHash;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 결과 불명 창·신선한 선점·마감 누락 이용권·탈퇴 회원 정산·다른 이용권 충돌·빌링키 폐기. 실제 MySQL + PortOne 스텁 HTTP
class MembershipReconciliationRecoveryIntegrationTest extends MembershipRenewalFlowSupport {

    @Autowired BillingKeyReconciliationFacade billingKeyReconciliation;
    @Autowired MembershipSubscriptionProcessor subscriptionProcessor;

    // ---------- 결과 불명 창(W) ----------

    @Test
    @DisplayName("결과 불명 창은 발송 시각 + 24시간에 끝난다 — 1초 전은 UNKNOWN 유지, 정각에 운영 확인 대기, 중간 갱신으로 늘어나지 않는다")
    void unknownWindowEndsOnDispatchedAt() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        claim(attemptId);
        clock.fixAt(T.plusHours(1));
        executor.reconcile(member.getId(), attemptId);

        // when
        clock.fixAt(T.plusHours(24).minusSeconds(1));
        MembershipChargeOutcome before = executor.reconcile(member.getId(), attemptId);
        clock.fixAt(T.plusHours(24));
        MembershipChargeOutcome at = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(before).isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);
        assertThat(at).isEqualTo(MembershipChargeOutcome.MANUAL_REVIEW);
        MembershipPaymentAttempt expired = attempt(attemptId);
        assertThat(expired.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.MANUAL_REVIEW);
        assertThat(expired.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.UNKNOWN_WINDOW_EXPIRED);
        assertThat(expired.isSettlementBlocked()).isFalse();
        assertThat(onlyRenewalPayment(membershipId).getStatus()).isEqualTo(MembershipPaymentStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("결과 불명 창이 끝난 뒤의 PAID 웹훅은 저장된 결제 마감으로 판정해 연장한다(새 시도 없음)")
    void paidWebhookAfterWindowUsesStoredDeadline() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        clock.fixAt(T.plusHours(24));
        executor.reconcile(member.getId(), attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.EXTENDED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(attemptsOf(onlyRenewalPayment(membershipId).getId())).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    @Test
    @DisplayName("신선한 DISPATCHING에 웹훅 GET이 진행 중 응답이면 상태를 바꾸지 않고 재시도 결과, 복구 Sweep은 GET하지 않는다")
    void freshDispatchingIsNotRecordedAsUnknown() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentInStatus(paymentId, "PENDING", FEE);
        clock.fixAt(T.plusMinutes(1));

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);
        int getsAfterWebhook = PORTONE.getCount(paymentId);
        recovery.recoverOnce();

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsAfterWebhook);
        assertThat(attempt(attemptId).isSettlementBlocked()).isFalse();
    }

    // ---------- 마감 누락 이용권 ----------

    @Test
    @DisplayName("결제 마감까지 갱신 접수가 없던 이용권은 FAILED 회차 1건·시도 0건·POST 0으로 닫히고 자동갱신이 꺼진다, 다시 돌려도 변경 없음")
    void missedRenewalCreatesOneFailedRoundAndStopsRenewal() {
        // given
        Long membershipId = activeMembership();
        clock.fixAt(G.plusMinutes(1));

        // when
        renewalFacade.settleOnce();
        renewalFacade.settleOnce();

        // then
        MembershipPayment closed = onlyRenewalPayment(membershipId);
        assertThat(closed.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(closed.getFailureReason()).isEqualTo("RENEWAL_MISSED_NO_ATTEMPT");
        assertThat(attemptsOf(closed.getId())).isEmpty();
        assertThat(PORTONE.totalPostCount()).isZero();
        assertThat(membership(membershipId).isAutoRenew()).isFalse();
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("마감 누락 이용권에 다른 이용권 충돌이 있어도 미정산이 없으면 FAILED 회차 1건으로 닫는다(소급 청구 없음)")
    void missedRenewalWithConflictStillCloses() {
        // given
        Long membershipId = activeMembership();
        grantOtherMembership();
        clock.fixAt(G.plusMinutes(1));

        // when
        renewalFacade.settleOnce();

        // then
        assertThat(onlyRenewalPayment(membershipId).getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(membership(membershipId).isAutoRenew()).isFalse();
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    // ---------- 다른 이용권 충돌 ----------

    @Test
    @DisplayName("다른 이용권과 충돌하면 갱신을 접수하지 않는다 — 회차 0·POST 0")
    void conflictingMembershipBlocksAccept() {
        // given
        Long membershipId = activeMembership();
        grantOtherMembership();

        // when
        MembershipRenewalDecision decision = renewalProcessor.accept(member.getId(), membershipId);

        // then
        assertThat(decision.kind()).isEqualTo(MembershipRenewalDecision.Kind.SKIPPED);
        assertThat(renewalPaymentIds(membershipId)).isEmpty();
    }

    @Test
    @DisplayName("접수 뒤 다른 이용권이 생긴 상태로 수납이 확인되면 연장하지 않고 환불 필요(CONFLICTING_MEMBERSHIP)·자동갱신 끔")
    void latePaidWithConflictingMembershipIsRefunded() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        grantOtherMembership();
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusHours(1));

        // when
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.CONFLICTING_MEMBERSHIP);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
        assertThat(membership(membershipId).isAutoRenew()).isFalse();
    }

    // ---------- 탈퇴 회원의 기존 거래 ----------

    @Test
    @DisplayName("탈퇴 회원의 늦은 갱신 수납은 재조회·환불 요청까지 진행하고 연장하지 않는다(PAID_AFTER_WITHDRAWAL)")
    void withdrawnMemberLatePaidIsRefundedNotExtended() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        withdraw();
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusHours(1));

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_AFTER_WITHDRAWAL);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("탈퇴 회원의 첫 결제 수납이 늦게 확인돼도 이용권을 활성화하지 않고 실제 수납액을 환불 요청한다")
    void withdrawnMemberFirstPaymentIsRefundedNotActivated() {
        // given
        clock.fixAt(FIRST_PAID_AT);
        subscriptionFacade.subscribe(member.getId(), new MembershipConsent(FEE, "v1", FIRST_PAID_AT.minusDays(1)),
            "first-" + UUID.randomUUID());
        Long attemptId = jdbc.queryForObject("SELECT a.id FROM membership_payment_attempt a JOIN membership_payment p "
                + "ON a.membership_payment_id = p.id JOIN membership m ON p.membership_id = m.id WHERE m.member_id = ?",
            Long.class, member.getId());
        Long membershipId = jdbc.queryForObject("SELECT id FROM membership WHERE member_id = ?", Long.class,
            member.getId());
        assertThat(attempt(attemptId).isDispatched()).isTrue();
        withdraw();
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", FIRST_PAID_AT);
        clock.fixAt(FIRST_PAID_AT.plusMinutes(5));

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_AFTER_WITHDRAWAL);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        assertThat(membership(membershipId).getActivatedAt()).isNull();
        assertThat(membership(membershipId).getTerminatedAt()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴 회원의 첫 결제 READY가 만료되면 유효한 다른 READY도 실행 대상으로 돌려주지 않고, 새 시도·POST 없이 회차를 닫는다")
    void withdrawnMemberFirstPaymentReadyExpiryDoesNotReissue() {
        // given
        clock.fixAt(FIRST_PAID_AT);
        MembershipConsent consent = new MembershipConsent(FEE, "v1", FIRST_PAID_AT.minusDays(1));
        Long firstId = transactionTemplate.execute(s -> subscriptionProcessor.accept(member.getId(), consent,
            member.getId() + ":first-" + UUID.randomUUID(), MembershipRequestHash.of(consent),
            LocalDateTime.now(clock))).attemptId();
        MembershipPayment payment = payments.findById(attempt(firstId).getMembershipPayment().getId()).orElseThrow();
        assertThat(attempt(firstId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        withdraw();
        clock.fixAt(FIRST_PAID_AT.plusMinutes(11));
        Long siblingId = attempts.saveAndFlush(MembershipPaymentAttempt.ready(payment, 2, cardA,
            LocalDateTime.now(clock), Duration.ofMinutes(10))).getId();

        // when
        ExpiredReadyResult withSibling = resultProcessor.handleExpiredReady(member.getId(), firstId,
            LocalDateTime.now(clock));
        clock.fixAt(FIRST_PAID_AT.plusMinutes(22));
        recovery.recoverOnce();

        // then
        assertThat(withSibling.outcome()).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
        assertThat(withSibling.attemptId()).isNull();
        assertThat(attempt(firstId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(attempt(siblingId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(attemptsOf(payment.getId())).hasSize(2);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(membership(payment.getMembershipId()).getTerminatedAt()).isNotNull();
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    @Test
    @DisplayName("탈퇴 회원의 정상 반영 PAID 재전송은 변경 없이 끝난다")
    void withdrawnMemberAppliedPaidReplayIsIdempotent() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);
        renewalFacade.runOnce();
        Long attemptId = attemptsOf(onlyRenewalPayment(membershipId).getId()).get(0).getId();
        withdraw();

        // when
        MembershipChargeOutcome outcome = webhook(attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("탈퇴 회원의 READY가 만료되면 접기만 하고 새 시도·POST를 만들지 않는다")
    void withdrawnMemberReadyExpiryDoesNotReissue() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        withdraw();
        PORTONE.defaultPaid(FEE, T);
        clock.fixAt(T.plusHours(1));

        // when
        recovery.recoverOnce();

        // then
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(attemptsOf(onlyRenewalPayment(membershipId).getId())).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    // ---------- 빌링키 폐기 ----------

    @Test
    @DisplayName("빌링키는 PortOne이 삭제됨 또는 정확한 BILLING_KEY_NOT_FOUND로 답할 때만 REVOKED, 다른 404·권한 오류·장애는 그대로")
    void onlyExplicitNotFoundOrDeletedRevokes() {
        // given
        String key = cardA.getPortoneBillingKey();

        // when · then
        PORTONE.onBillingKeyLookup(key, 404, "{\"type\":\"SOMETHING_ELSE\"}");
        assertThat(billingKeyReconciliation.verifyUnusable(member.getId(), cardA.getId())).isFalse();
        PORTONE.onBillingKeyLookup(key, 401, "{\"type\":\"UNAUTHORIZED\"}");
        assertThat(billingKeyReconciliation.verifyUnusable(member.getId(), cardA.getId())).isFalse();
        PORTONE.onBillingKeyLookup(key, 503, "{\"type\":\"UNAVAILABLE\"}");
        assertThat(billingKeyReconciliation.verifyUnusable(member.getId(), cardA.getId())).isFalse();
        assertThat(keyStatus(cardA.getId())).isEqualTo(BillingKeyStatus.ACTIVE);

        PORTONE.billingKeyDeleted(key);
        assertThat(billingKeyReconciliation.verifyUnusable(member.getId(), cardA.getId())).isTrue();
        assertThat(keyStatus(cardA.getId())).isEqualTo(BillingKeyStatus.REVOKED);

        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.ACTIVE);
        PORTONE.onBillingKeyLookup(key, 404, "{\"type\":\"BILLING_KEY_NOT_FOUND\"}");
        assertThat(billingKeyReconciliation.verifyUnusable(member.getId(), cardA.getId())).isTrue();
        assertThat(keyStatus(cardA.getId())).isEqualTo(BillingKeyStatus.REVOKED);
    }

    @Test
    @DisplayName("다른 키의 DELETED 응답으로는 우리 키를 폐기하지 않는다")
    void deletedResponseForAnotherKeyDoesNotRevoke() {
        // given
        PORTONE.onBillingKeyLookup(cardA.getPortoneBillingKey(), 200, "{\"status\":\"DELETED\","
            + "\"billingKey\":\"billing-key-test-other\",\"deletedAt\":\"2027-02-27T00:00:00Z\"}");

        // when
        boolean revoked = billingKeyReconciliation.verifyUnusable(member.getId(), cardA.getId());

        // then
        assertThat(revoked).isFalse();
        assertThat(keyStatus(cardA.getId())).isEqualTo(BillingKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("사용할 수 없는 키 응답 뒤 결제 결과 반영이 실패해도 키 확인은 하고, 결제 쪽 예외는 그대로 올라간다")
    void keyIsVerifiedEvenWhenResultApplyFails() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = attempt(attemptId).getPortonePaymentId();
        PORTONE.onBillingKeyPayment(paymentId, 404, "{\"type\":\"BILLING_KEY_NOT_FOUND\"}");
        PORTONE.getPaymentFailed(paymentId, FEE);
        PORTONE.billingKeyDeleted(cardA.getPortoneBillingKey());
        jdbc.execute("ALTER TABLE membership_payment_attempt ADD CONSTRAINT ck_test_apply_failure CHECK ("
            + "portone_payment_id <> '" + paymentId + "' OR status <> 'FAILED')");
        Throwable thrown;
        try {
            // when
            thrown = catchThrowable(() -> executor.execute(member.getId(), attemptId));
        } finally {
            jdbc.execute("ALTER TABLE membership_payment_attempt DROP CHECK ck_test_apply_failure");
        }

        // then
        assertThat(thrown).isNotNull();
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(keyStatus(cardA.getId())).isEqualTo(BillingKeyStatus.REVOKED);
        assertThat(PORTONE.billingKeyLookupCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("폐기된 키로 접수된 READY는 선점·POST하지 않는다")
    void revokedKeyReadyIsNotDispatched() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.REVOKED);
        PORTONE.defaultPaid(FEE, T);

        // when
        MembershipChargeOutcome outcome = executor.execute(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.NOT_DISPATCHED);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    @Test
    @DisplayName("청구가 사용할 수 없는 키로 거절되면 결제는 같은 ID 재조회로 두고 그 키만 확인해 REVOKED로 바꾼다")
    void unusableKeyResponseRevokesOnlyThatKey() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = attempt(attemptId).getPortonePaymentId();
        PORTONE.onBillingKeyPayment(paymentId, 404, "{\"type\":\"BILLING_KEY_NOT_FOUND\"}");

        // when
        MembershipChargeOutcome outcome = executor.execute(member.getId(), attemptId);

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.UNKNOWN);
        assertThat(keyStatus(cardA.getId())).isEqualTo(BillingKeyStatus.REVOKED);
        assertThat(PORTONE.billingKeyLookupCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("결과 불명 창 안의 404 UNKNOWN 시도에 묶인 키는 복구가 한 번 확인하고, 조회 장애면 그대로 둔다")
    void recoveryRechecksKeyOfNotFoundUnknownAttempt() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        claim(attemptId);
        clock.fixAt(T.plusHours(1));
        executor.reconcile(member.getId(), attemptId);
        PORTONE.onBillingKeyLookup(cardA.getPortoneBillingKey(), 503, "{\"type\":\"UNAVAILABLE\"}");

        // when
        recovery.recoverOnce();
        BillingKeyStatus afterOutage = keyStatus(cardA.getId());
        PORTONE.billingKeyDeleted(cardA.getPortoneBillingKey());
        clock.fixAt(T.plusHours(2));
        recovery.recoverOnce();

        // then
        assertThat(afterOutage).isEqualTo(BillingKeyStatus.ACTIVE);
        assertThat(keyStatus(cardA.getId())).isEqualTo(BillingKeyStatus.REVOKED);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.UNKNOWN);
    }

    // ---------- 보조 ----------

    private MembershipChargeOutcome webhook(Long attemptId) {
        return executor.reconcile(member.getId(), attemptId, MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
    }

    // 테스트 픽스처: 대상 이용권보다 나중에 만든, 현재 활성인 다른 이용권(관리자 지급과 같은 형태)
    private void grantOtherMembership() {
        LocalDateTime now = LocalDateTime.now(clock);
        memberships.saveAndFlush(Membership.builder().member(member).type(MembershipType.PAID)
            .source(MembershipSource.ADMIN_GRANT).startAt(now).endAt(now.plusDays(30)).activatedAt(now)
            .autoRenew(false).build());
    }

    private void withdraw() {
        jdbc.update("UPDATE member SET withdrawn_at = ? WHERE id = ?", LocalDateTime.now(clock), member.getId());
    }

    private BillingKeyStatus keyStatus(Long billingKeyId) {
        return BillingKeyStatus.valueOf(jdbc.queryForObject("SELECT status FROM billing_key WHERE id = ?",
            String.class, billingKeyId));
    }
}
