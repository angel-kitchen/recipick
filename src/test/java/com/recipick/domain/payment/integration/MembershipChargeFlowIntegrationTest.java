package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.facade.MembershipChargeDispatchProcessor;
import com.recipick.domain.payment.facade.MembershipChargeExecutor;
import com.recipick.domain.payment.facade.MembershipChargeRecoveryFacade;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor.ExpiredReadyResult;
import com.recipick.domain.payment.facade.MembershipSubscriptionFacade;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor.SubscriptionReceipt;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPgVerdict;
import com.recipick.domain.payment.service.MembershipRequestHash;
import com.recipick.domain.payment.service.MembershipSubscribeResult;
import com.recipick.domain.payment.service.MembershipSubscribeStatus;
import com.recipick.infra.portone.PortOneBillingClient;
import com.recipick.infra.portone.PortOnePaymentClient;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

// 실행기를 PG 스텁까지 끝까지 타는 흐름(④: 복구·T-8·G-1·창 경계). PG 호출 횟수(POST·GET)를 결제 ID별로 세어 I-12를 본다.
// 시각 픽스처는 JPA 경로(MembershipTimeFixtures)로만 쓰고, 시각을 옮겨야 하는 경우는 MovableClock을 고정한다
class MembershipChargeFlowIntegrationTest extends MembershipChargeFlowSupport {

    @Autowired MembershipSubscriptionFacade facade;
    @Autowired MembershipSubscriptionProcessor processor;
    @Autowired MembershipChargeExecutor executor;
    @Autowired MembershipChargeDispatchProcessor dispatchProcessor;
    @Autowired MembershipChargeResultProcessor resultProcessor;
    @Autowired MembershipChargeRecoveryFacade recovery;
    @Autowired MemberService memberService;
    @Autowired MembershipPaymentRepository payments;
    @Autowired MembershipPaymentAttemptRepository attempts;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipBillingProperties properties;
    @Autowired PortOneBillingClient portOneBillingClient;
    @Autowired PortOnePaymentClient portOnePaymentClient;

    private Member member;
    private MembershipConsent consent;
    private String key;

    @BeforeEach
    void setUp() {
        member = saveMember();
        saveActiveKey(member.getId());
        consent = new MembershipConsent(FEE, "v1", LocalDateTime.now(clock).minusDays(1));
        key = "flow-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("E2E: 바로 구독 → POST 1·GET 1(같은 결제 ID) → PAID 활성화. 같은 키 재요청은 PAID만 돌려주고 PG를 다시 부르지 않는다")
    void subscribeEndToEnd() {
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        PORTONE.billingKeyPaymentPaid(paymentId, pgPaidAt);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", pgPaidAt);

        MembershipSubscribeResult result = facade.subscribe(member.getId(), consent, key);
        MembershipSubscribeResult again = facade.subscribe(member.getId(), consent, key);

        assertThat(result.status()).isEqualTo(MembershipSubscribeStatus.PAID);
        assertThat(again.status()).isEqualTo(MembershipSubscribeStatus.PAID);
        assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.totalRequests()).isEqualTo(2);
        assertThat(memberships.findById(receipt.membershipId()).orElseThrow().getActivatedAt()).isEqualTo(pgPaidAt);
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.PAID);
    }

    @Test
    @DisplayName("X-2a(순차 참고): 임대 안의 DISPATCHING에 execute하면 IN_PROGRESS — GET 0·POST 0. 실제 동시 실행은 ③ 동시성 테스트")
    void freshDispatchingLoserDoesNotTouchPg() {
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        dispatchProcessor.claim(receipt.attemptId()).ticket().orElseThrow();

        MembershipChargeOutcome outcome = executor.execute(member.getId(), receipt.attemptId());

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
        assertThat(PORTONE.postCount(paymentId)).isZero();
        assertThat(PORTONE.getCount(paymentId)).isZero();
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
    }

    @Test
    @DisplayName("X-2b/R-1: L+M을 넘긴 DISPATCHING은 같은 결제 ID로 GET만 하고(POST 0) 결과를 반영한다")
    void staleDispatchingIsReconciledByGetOnly() {
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        dispatchProcessor.claim(receipt.attemptId()).ticket().orElseThrow();
        time.attemptDispatchedAt(receipt.attemptId(), staleDispatchedAt());
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", pgPaidAt);

        MembershipChargeOutcome outcome = executor.execute(member.getId(), receipt.attemptId());

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        assertThat(PORTONE.postCount(paymentId)).isZero();
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
        assertThat(memberships.findById(receipt.membershipId()).orElseThrow().getActivatedAt()).isEqualTo(pgPaidAt);
    }

    @Test
    @DisplayName("R-9: 복구 1회는 오래된 DISPATCHING을 같은 ID로 재조회(FAILED → 대기 종료)하고 새 ID를 만들지 않는다")
    void recoveryReconcilesStaleDispatching() {
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        dispatchProcessor.claim(receipt.attemptId()).ticket().orElseThrow();
        time.attemptDispatchedAt(receipt.attemptId(), staleDispatchedAt());
        PORTONE.getPaymentFailed(paymentId, FEE);

        int processed = recovery.recoverOnce();

        assertThat(processed).isGreaterThanOrEqualTo(1);
        assertThat(PORTONE.postCount(paymentId)).isZero();
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
        assertThat(payments.findById(paymentRowId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentRowId())).hasSize(1);
        assertThat(memberships.findById(receipt.membershipId()).orElseThrow().getTerminatedAt()).isNotNull();
    }

    @Test
    @DisplayName("T-15(b) 복구: 0b 정합성 오류는 롤백 뒤 원인 재검증 TX로 자동 정합화를 멈추고, 이후 복구·reconcile·TX2 진입 모두 "
        + "GET·반영 없이 RECONCILE_HALTED다(미해결·C-17 유지, 환불 0)")
    void recoveryHaltsInconsistentAttemptEverywhere() {
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        dispatchProcessor.claim(receipt.attemptId()).ticket().orElseThrow();
        time.attemptDispatchedAt(receipt.attemptId(), staleDispatchedAt());
        time.membershipActivated(receipt.membershipId(), pgPaidAt);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", pgPaidAt);

        recovery.recoverOnce();
        recovery.recoverOnce();
        MembershipChargeOutcome reconciled = executor.reconcile(member.getId(), receipt.attemptId());
        MembershipChargeOutcome applied = resultProcessor.apply(member.getId(), receipt.attemptId(),
            MembershipPgVerdict.unresolved("PG_TIMEOUT"));

        assertThat(reconciled).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(applied).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.postCount(paymentId)).isZero();
        MembershipPaymentAttempt flagged = attempts.findById(receipt.attemptId()).orElseThrow();
        assertThat(flagged.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(flagged.getFailureReason()).isEqualTo(MembershipPaymentAttempt.REASON_APPLIED_STATE_INCONSISTENT);
        assertThat(flagged.isAutoReconcileHalted()).isTrue();
        assertThat(flagged.getRefundRequiredAt()).isNull();
        assertThat(payments.findById(paymentRowId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(refundCount(receipt.membershipId())).isZero();
    }

    @Test
    @DisplayName("다른 사유의 운영 표시(확인 지연)가 붙은 오래된 DISPATCHING은 자동 재조회 대상에 그대로 남는다")
    void otherOpsFlagDoesNotHaltRecovery() {
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        dispatchProcessor.claim(receipt.attemptId()).ticket().orElseThrow();
        time.attemptDispatchedAt(receipt.attemptId(), staleDispatchedAt());
        time.attemptOpsFlag(receipt.attemptId(), pgPaidAt, MembershipOpsReviewReason.FIRST_PAYMENT_LONG_DELAY);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", pgPaidAt);

        recovery.recoverOnce();

        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(memberships.findById(receipt.membershipId()).orElseThrow().getActivatedAt()).isEqualTo(pgPaidAt);
    }

    @Test
    @DisplayName("확정 회차에 만료 READY와 다른 유효 READY가 함께 남아도 복구는 RETRY_READY로 실행하지 않는다 — PG POST 0")
    void expiredReadyOnConfirmedPaymentIsNotExecuted() {
        SubscriptionReceipt receipt = accept();
        LocalDateTime firstRequestedAt = attempts.findById(receipt.attemptId()).orElseThrow().getRequestedAt();
        ExpiredReadyResult second = resultProcessor.handleExpiredReady(member.getId(), receipt.attemptId(),
            firstRequestedAt.plusMinutes(11));
        assertThat(second.outcome()).isEqualTo(MembershipChargeOutcome.RETRY_READY);
        time.paymentStatus(paymentRowId(), MembershipPaymentStatus.FAILED);
        time.attemptBackToExpiredReady(receipt.attemptId(), firstRequestedAt.plusMinutes(1));
        clock.fixAt(firstRequestedAt.plusMinutes(12));

        recovery.recoverOnce();

        assertThat(PORTONE.totalRequests()).isZero();
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(attempts.findById(second.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.READY);
    }

    @Test
    @DisplayName("T-8: 고정 시계로 23:59 접수 → 00:01 PG 성공. TX2가 기간을 00:01 날짜로 덧씌울 때만 UNIQUE(이용권+시작일)가 충돌해 "
        + "시도·회차·이용권이 모두 롤백되고, 충돌 행을 지운 뒤 L+M 지나 같은 결제 ID 재조회(POST 0)로 반영된다")
    void tx2FailureRollsBackAndReconcileSucceeds() {
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 11, 6, 23, 59);
        LocalDateTime pgPaidAt = LocalDateTime.of(2026, 11, 7, 0, 1);
        clock.fixAt(acceptedAt);
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        PORTONE.billingKeyPaymentPaid(paymentId, pgPaidAt);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", pgPaidAt);
        // 접수 회차의 임시 시작일은 11/6이라 11/7 시작의 다른 회차는 문제없이 들어간다. 충돌은 TX2의 기간 덧씌우기에서만 난다
        Long conflicting = time.otherPayment(receipt.membershipId(), FEE, MembershipPaymentStatus.FAILED,
            pgPaidAt.toLocalDate(), pgPaidAt.plusMonths(1).plusDays(3), null, "TEST_FIXTURE");
        clock.fixAt(pgPaidAt);

        Throwable thrown = catchThrowable(() -> executor.execute(member.getId(), receipt.attemptId()));

        assertThat(hasConstraintViolation(thrown)).isTrue();
        assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(payments.findById(paymentRowId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(payments.findById(paymentRowId()).orElseThrow().getMembershipAppliedAt()).isNull();
        assertThat(memberships.findById(receipt.membershipId()).orElseThrow().getActivatedAt()).isNull();

        time.deletePayment(conflicting);
        clock.fixAt(pgPaidAt.plus(properties.lease()).plus(properties.dispatchMargin()).plusSeconds(1));
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), receipt.attemptId());

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(2);
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getPortonePaymentId()).isEqualTo(paymentId);
        assertThat(memberships.findById(receipt.membershipId()).orElseThrow().getActivatedAt()).isEqualTo(pgPaidAt);
        assertThat(payments.findById(paymentRowId()).orElseThrow().getPeriodStartDate())
            .isEqualTo(pgPaidAt.toLocalDate());
    }

    @Test
    @DisplayName("G-1: 플래그 OFF면 READY 시도의 POST가 나가지 않고(IN_PROGRESS 응답) READY로 남는다")
    void flagOffBlocksPost() {
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        gate.set(false);

        MembershipSubscribeResult result = facade.subscribe(member.getId(), consent, key);
        MembershipChargeOutcome outcome = executor.execute(member.getId(), receipt.attemptId());

        assertThat(result.status()).isEqualTo(MembershipSubscribeStatus.IN_PROGRESS);
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.NOT_DISPATCHED);
        assertThat(PORTONE.totalRequests()).isZero();
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(PORTONE.postCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("접수 유효 창(30분) 경계: POST 직전에 창이 끝났으면 POST 없이 되돌린 뒤 임대 만료 규칙으로 회차를 닫는다")
    void acceptWindowClosedRightBeforePost() {
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        PORTONE.billingKeyPaymentPaid(paymentId, LocalDateTime.now(clock));
        // 선점(실제 시각)은 성공하고 POST 직전 재확인(창 + 1분 뒤 시각)만 닫힌 창을 보게 한다
        Clock afterWindow = Clock.offset(clock, properties.firstChargeAcceptWindow().plus(Duration.ofMinutes(1)));
        MembershipChargeExecutor lateExecutor = new MembershipChargeExecutor(gate, properties, memberService,
            dispatchProcessor, resultProcessor, attempts, portOneBillingClient, portOnePaymentClient, afterWindow);

        MembershipChargeOutcome outcome = lateExecutor.execute(member.getId(), receipt.attemptId());

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.CLOSED_UNPAID);
        assertThat(PORTONE.totalRequests()).isZero();
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(payments.findById(paymentRowId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(memberships.findById(receipt.membershipId()).orElseThrow().getTerminatedAt()).isNotNull();
    }

    @Test
    @DisplayName("접수 유효 창 안쪽 경계(29분)에서는 그대로 POST한다")
    void acceptWindowStillOpenRightBeforePost() {
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        SubscriptionReceipt receipt = accept();
        String paymentId = paymentIdOf(receipt.attemptId());
        PORTONE.billingKeyPaymentPaid(paymentId, pgPaidAt);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", pgPaidAt);
        Clock nearEnd = Clock.offset(clock, properties.firstChargeAcceptWindow().minus(Duration.ofMinutes(1)));
        MembershipChargeExecutor lateExecutor = new MembershipChargeExecutor(gate, properties, memberService,
            dispatchProcessor, resultProcessor, attempts, portOneBillingClient, portOnePaymentClient, nearEnd);

        MembershipChargeOutcome outcome = lateExecutor.execute(member.getId(), receipt.attemptId());

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("복구 진입점도 바깥 트랜잭션 안에서는 PG 요청 전에 거절된다(Propagation.NEVER)")
    void recoveryRejectsOuterTransaction() {
        SubscriptionReceipt receipt = accept();
        dispatchProcessor.claim(receipt.attemptId()).ticket().orElseThrow();
        time.attemptDispatchedAt(receipt.attemptId(), staleDispatchedAt());

        Throwable thrown = catchThrowable(() -> transactionTemplate.execute(s -> recovery.recoverOnce()));

        assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(PORTONE.totalRequests()).isZero();
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
    }

    // ---------- 보조 ----------

    private SubscriptionReceipt accept() {
        return transactionTemplate.execute(s -> processor.accept(member.getId(), consent, member.getId() + ":" + key,
            MembershipRequestHash.of(consent), LocalDateTime.now(clock)));
    }

    private LocalDateTime staleDispatchedAt() {
        return LocalDateTime.now(clock).minus(properties.lease()).minus(properties.dispatchMargin()).minusSeconds(1);
    }

    private String paymentIdOf(Long attemptId) {
        return attempts.findById(attemptId).orElseThrow().getPortonePaymentId();
    }

    private Long paymentRowId() {
        return payments.findByIdempotencyKey(member.getId() + ":" + key).orElseThrow().getId();
    }

    private long refundCount(Long membershipId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM membership_refund r JOIN membership_payment p "
            + "ON r.membership_payment_id = p.id WHERE p.membership_id = ?", Long.class, membershipId);
        return count == null ? 0 : count;
    }

    // 커밋 시점 flush든 중간 flush든 원인 사슬에 MySQL 1062(UNIQUE 위반)가 있으면 된다 — 예외 래퍼 종류에 기대지 않는다
    private static boolean hasConstraintViolation(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (t.getCause() == t) {
                return false;
            }
        }
        return false;
    }
}
