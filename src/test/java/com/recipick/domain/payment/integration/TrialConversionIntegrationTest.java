package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.facade.MembershipChargeDispatchProcessor;
import com.recipick.domain.payment.facade.MembershipChargeExecutor;
import com.recipick.domain.payment.facade.TrialConversionFacade;
import com.recipick.domain.payment.facade.TrialConversionProcessor;
import com.recipick.domain.payment.facade.TrialConversionProcessor.ConversionReceipt;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

// C-1·C-2·C-4·C-5. 체험 종료 → 전환 접수(TX1) → 실행 → PAID면 TRIAL_CONVERT 유료 이용권 활성화(startAt = pgPaidAt).
// 전환 대상 조회는 회원 도메인 쿼리(findTrialConversionTargets)를 그대로 쓴다
class TrialConversionIntegrationTest extends MembershipChargeFlowSupport {

    @Autowired TrialConversionFacade facade;
    @Autowired TrialConversionProcessor processor;
    @Autowired MembershipChargeExecutor executor;
    @Autowired MembershipChargeDispatchProcessor dispatchProcessor;
    @Autowired MembershipBillingService billing;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipPaymentRepository payments;
    @Autowired MembershipPaymentAttemptRepository attempts;

    @Test
    @DisplayName("C-1: 종료된 체험 + ACTIVE 카드 → 전환 회차 PENDING(멱등 키 없음)·READY → PAID → TRIAL_CONVERT 활성화, 체험 넘김")
    void convertsEndedTrial() {
        Member member = saveMember();
        saveActiveKey(member.getId());
        Membership trial = saveEndedTrial(member);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime pgPaidAt = now.withNano(0);

        ConversionReceipt receipt = processor.accept(member.getId(), trial.getId(), now);
        assertThat(receipt.kind()).isEqualTo(ConversionReceipt.Kind.CHARGE_READY);
        MembershipPaymentAttempt attempt = attempts.findById(receipt.attemptId()).orElseThrow();
        // 지연 로딩 연관을 트랜잭션 밖에서 읽지 않는다 — 회차 PK 투영으로 회차를 다시 조회한다
        Long paymentRowId = attempts.findRef(receipt.attemptId()).orElseThrow().membershipPaymentId();
        MembershipPayment payment = payments.findById(paymentRowId).orElseThrow();
        assertThat(payment.getIdempotencyKey()).isNull();
        assertThat(payment.getGraceDeadlineAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        PORTONE.billingKeyPaymentPaid(attempt.getPortonePaymentId(), pgPaidAt);
        PORTONE.getPaymentPaid(attempt.getPortonePaymentId(), FEE, "KRW", pgPaidAt);

        MembershipChargeOutcome outcome = executor.execute(member.getId(), receipt.attemptId());

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        Membership paid = memberships.findById(payment.getMembershipId()).orElseThrow();
        assertThat(paid.getType()).isEqualTo(MembershipType.PAID);
        assertThat(paid.getSource()).isEqualTo(MembershipSource.TRIAL_CONVERT);
        assertThat(paid.getActivatedAt()).isEqualTo(pgPaidAt);
        assertThat(paid.getStartAt()).isEqualTo(pgPaidAt);
        assertThat(paid.getEndAt()).isEqualTo(pgPaidAt.plusMonths(1));
        assertThat(memberships.findById(trial.getId()).orElseThrow().isAutoRenew()).isFalse();
        assertThat(PORTONE.postCount(attempt.getPortonePaymentId())).isEqualTo(1);
        assertThat(PORTONE.getCount(attempt.getPortonePaymentId())).isEqualTo(1);
        // 같은 체험은 다음 실행에서 대상이 아니다
        assertThat(processor.accept(member.getId(), trial.getId(), now.plusMinutes(1)).kind())
            .isEqualTo(ConversionReceipt.Kind.SKIPPED);
    }

    @Test
    @DisplayName("C-2: 종료된 체험인데 ACTIVE 카드가 없으면 전환 종료(일반 회원) — 회차·시도·PG 호출 없음, 체험 자격은 소진")
    void closesConversionWithoutCard() {
        Member member = saveMember();
        Membership trial = saveEndedTrial(member);
        List<Long> before = billing.findMembershipIds(member.getId());

        int processed = facade.runOnce();

        assertThat(processed).isGreaterThanOrEqualTo(1);
        Membership closed = memberships.findById(trial.getId()).orElseThrow();
        assertThat(closed.isAutoRenew()).isFalse();
        assertThat(closed.getType()).isEqualTo(MembershipType.TRIAL);
        assertThat(billing.findMembershipIds(member.getId())).isEqualTo(before);
        assertThat(PORTONE.totalRequests()).isZero();
        assertThat(billing.findTrialConversionTargets(LocalDateTime.now(clock), 0L, 100))
            .noneMatch(t -> t.membershipId().equals(trial.getId()));
    }

    @Test
    @DisplayName("C-4: 전환 접수 뒤 결과 불명(UNKNOWN)이면 체험은 넘겨진 채 유료 이용권은 미활성, 다음 전환 실행은 새 회차를 만들지 않는다")
    void unknownResultLeavesPendingAndSkipsNextRun() {
        Member member = saveMember();
        saveActiveKey(member.getId());
        Membership trial = saveEndedTrial(member);
        LocalDateTime now = LocalDateTime.now(clock);
        ConversionReceipt receipt = processor.accept(member.getId(), trial.getId(), now);
        String paymentId = attempts.findById(receipt.attemptId()).orElseThrow().getPortonePaymentId();
        PORTONE.onBillingKeyPayment(paymentId, 502, "{\"type\":\"PG_PROVIDER\",\"pgMessage\":\"x\"}");
        PORTONE.getPaymentNotFound(paymentId);

        MembershipChargeOutcome outcome = executor.execute(member.getId(), receipt.attemptId());
        int processedAgain = facade.runOnce();

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.UNKNOWN);
        assertThat(billing.findMembershipIds(member.getId())).hasSize(2);
        assertThat(processedAgain).isZero();
        assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("C-5: 플래그 OFF면 전환 실행은 대상을 건드리지 않는다(0건)")
    void flagOffSkipsConversion() {
        Member member = saveMember();
        saveActiveKey(member.getId());
        Membership trial = saveEndedTrial(member);
        gate.set(false);

        int processed = facade.runOnce();

        assertThat(processed).isZero();
        assertThat(memberships.findById(trial.getId()).orElseThrow().isAutoRenew()).isTrue();
        assertThat(PORTONE.totalRequests()).isZero();
    }

    @Test
    @DisplayName("전환 실행은 바깥 트랜잭션 안에서 부르면 대상 조회·PG 호출 전에 거절된다(Propagation.NEVER)")
    void runOnceRejectsOuterTransaction() {
        Member member = saveMember();
        saveActiveKey(member.getId());
        Membership trial = saveEndedTrial(member);

        Throwable thrown = catchThrowable(() -> transactionTemplate.execute(s -> facade.runOnce()));

        assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(memberships.findById(trial.getId()).orElseThrow().isAutoRenew()).isTrue();
        assertThat(billing.findMembershipIds(member.getId())).hasSize(1);
        assertThat(PORTONE.totalRequests()).isZero();
    }

    // 7일 체험이 방금 끝난 회원. 자동결제 동의는 체험 시작 때(3,900원·v1)
    private Membership saveEndedTrial(Member member) {
        LocalDateTime start = LocalDateTime.now(clock).minusDays(7).minusMinutes(1).withNano(0);
        return memberships.saveAndFlush(Membership.builder().member(member).type(MembershipType.TRIAL)
            .source(MembershipSource.TRIAL).startAt(start).endAt(start.plusDays(7)).activatedAt(start).autoRenew(true)
            .consentedAmount(FEE).consentTextVersion("v1").autopayConsentedAt(start).build());
    }
}
