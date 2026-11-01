package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.member.service.MembershipCancelReservationService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.exception.MembershipSettlementInvariantException;
import com.recipick.domain.payment.exception.MembershipSettlementInvariantException.Kind;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.MembershipChargeDispatchProcessor;
import com.recipick.domain.payment.facade.MembershipChargeExecutor;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor.ExpiredReadyResult;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor.SubscriptionReceipt;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipChargeTicket;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.domain.payment.service.MembershipPgVerdict;
import com.recipick.domain.payment.service.MembershipRequestHash;
import com.recipick.domain.payment.service.MembershipSubscribeStatus;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.PortOneBillingClient;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import jakarta.persistence.EntityManager;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

// S-·T- 계열. 바로 구독 TX1·TX2를 실제 MySQL 위에서 본다. PortOne은 끼지 않는다 — 재조회 결과(PortOnePayment)를 테스트가 만들어
// 판정(MembershipPgVerdict)으로 넘기고 TX2(ResultProcessor)를 직접 부른다.
// 플래그 OFF는 토글 gate를 넘긴 수동 구성 인스턴스로 재현한다(@Transactional 프록시가 없는 Processor는 TransactionTemplate로 감싼다).
// 시각은 JPA로만 읽고, 시각 픽스처도 JPA 경로(MembershipTimeFixtures)로만 쓴다(UTC JVM에서도 같다).
// LAZY 연관은 트랜잭션 밖에서 건드리지 않는다 — 검증 값은 짧은 TransactionTemplate 안에서 읽는다
class MembershipSubscriptionIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Original1!";
    private static final long FEE = 3_900L;
    private static final String KEY = "sub-" + UUID.randomUUID();

    @Autowired MembershipSubscriptionProcessor processor;
    @Autowired MembershipChargeResultProcessor resultProcessor;
    @Autowired MembershipChargeDispatchProcessor dispatchProcessor;
    @Autowired MembershipChargeExecutor executor;
    @Autowired MemberService memberService;
    @Autowired MembershipService membershipService;
    @Autowired MembershipBillingService billing;
    @Autowired MembershipCancelReservationService cancelReservation;
    @Autowired MembershipPaymentQueryService queryService;
    @Autowired MembershipPaymentRepository payments;
    @Autowired MembershipPaymentAttemptRepository attempts;
    @Autowired MembershipRefundRepository refunds;
    @Autowired BillingKeyRepository billingKeys;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipBillingProperties properties;
    @Autowired PortOneBillingClient portOneBillingClient;
    @Autowired PortOnePaymentClient portOnePaymentClient;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired EntityManager em;

    private final List<Long> memberIds = new ArrayList<>();
    private MembershipTimeFixtures time;
    private Member member;
    private MembershipConsent consent;
    private String storedKey;
    private String hash;

    @BeforeEach
    void setUp() {
        member = saveMember();
        saveActiveKey(member.getId());
        consent = new MembershipConsent(FEE, "v1", LocalDateTime.now(clock).minusDays(1));
        storedKey = member.getId() + ":" + KEY;
        hash = MembershipRequestHash.of(consent);
        time = new MembershipTimeFixtures(em, transactionTemplate);
    }

    @AfterEach
    void cleanUp() {
        for (Long memberId : memberIds) {
            jdbc.update("DELETE r FROM membership_refund r JOIN membership_payment p ON r.membership_payment_id = p.id "
                + "JOIN membership m ON p.membership_id = m.id WHERE m.member_id = ?", memberId);
            jdbc.update("DELETE a FROM membership_payment_attempt a JOIN membership_payment p "
                    + "ON a.membership_payment_id = p.id JOIN membership m ON p.membership_id = m.id WHERE m.member_id = ?",
                memberId);
            jdbc.update("DELETE p FROM membership_payment p JOIN membership m ON p.membership_id = m.id "
                + "WHERE m.member_id = ?", memberId);
            jdbc.update("DELETE FROM membership WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM billing_key WHERE member_id = ?", memberId);
            members.deleteById(memberId);
        }
    }

    // ---------- S-1·S-2·S-4: 접수·멱등 ----------

    @Test
    @DisplayName("바로 구독 접수는 결제 대기 이용권 1·회차 PENDING 1·시도 READY 1(mpay_, 빌링키 고정)을 만든다")
    void acceptsSubscription() {
        SubscriptionReceipt receipt = accept();

        assertThat(receipt.existingStatus()).isNull();
        Membership membership = memberships.findById(receipt.membershipId()).orElseThrow();
        assertThat(membership.getType()).isEqualTo(MembershipType.PAID);
        assertThat(membership.getSource()).isEqualTo(MembershipSource.SUBSCRIBE);
        assertThat(membership.getActivatedAt()).isNull();
        MembershipPayment payment = payments.findByIdempotencyKey(storedKey).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualTo(FEE);
        assertThat(payment.getGraceDeadlineAt()).isNull();
        MembershipPaymentAttempt attempt = attempts.findById(receipt.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(attempt.getPortonePaymentId()).startsWith("mpay_");
        Long keyOwnerId = transactionTemplate.execute(s -> attempts.findById(receipt.attemptId()).orElseThrow()
            .getBillingKey().getMemberId());
        assertThat(keyOwnerId).isEqualTo(member.getId());
        assertThat(membershipService.getTypeAt(member.getId(), LocalDateTime.now(clock))).isEmpty();
    }

    @Test
    @DisplayName("같은 키·같은 동의(시각만 다름) 재요청은 행을 늘리지 않고 IN_PROGRESS·같은 READY 시도를 돌려준다")
    void sameKeyIsIdempotent() {
        SubscriptionReceipt first = accept();
        MembershipConsent later = new MembershipConsent(FEE, "v1", consent.autopayConsentedAt().plusMinutes(5));

        SubscriptionReceipt again = transactionTemplate.execute(s -> processor.accept(member.getId(), later,
            storedKey, MembershipRequestHash.of(later), LocalDateTime.now(clock)));

        assertThat(again.existingStatus()).isEqualTo(MembershipSubscribeStatus.IN_PROGRESS);
        assertThat(again.latestAttemptReady()).isTrue();
        assertThat(again.attemptId()).isEqualTo(first.attemptId());
        assertThat(again.membershipId()).isEqualTo(first.membershipId());
        assertThat(billing.findMembershipIds(member.getId())).hasSize(1);
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId())).hasSize(1);
    }

    @Test
    @DisplayName("같은 키·다른 금액은 COMMON-011이다")
    void sameKeyDifferentPayload() {
        accept();
        MembershipConsent other = new MembershipConsent(3_800L, "v1", consent.autopayConsentedAt());

        Throwable thrown = catchThrowable(() -> transactionTemplate.execute(s -> processor.accept(member.getId(),
            other, storedKey, MembershipRequestHash.of(other), LocalDateTime.now(clock))));

        assertThat(codeOf(thrown)).isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
    }

    // ---------- S-5·S-6: 거절 ----------

    @Test
    @DisplayName("ACTIVE 카드가 없으면 MEMBERSHIP-005, 동의 금액이 3,900이 아니면 MEMBERSHIP-004이고 행은 생기지 않는다")
    void rejectsWithoutCardOrWrongAmount() {
        jdbc.update("DELETE FROM billing_key WHERE member_id = ?", member.getId());
        assertThat(codeOf(catchThrowable(this::accept))).isEqualTo(MemberErrorCode.PAYMENT_METHOD_REQUIRED);

        saveActiveKey(member.getId());
        MembershipConsent wrong = new MembershipConsent(3_800L, "v1", consent.autopayConsentedAt());
        Throwable thrown = catchThrowable(() -> transactionTemplate.execute(s -> processor.accept(member.getId(),
            wrong, storedKey, MembershipRequestHash.of(wrong), LocalDateTime.now(clock))));
        assertThat(codeOf(thrown)).isEqualTo(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);

        assertThat(billing.findMembershipIds(member.getId())).isEmpty();
        assertThat(payments.findByIdempotencyKey(storedKey)).isEmpty();
    }

    @Test
    @DisplayName("이용권이 있으면 MEMBERSHIP-002, 종료된 옛 이용권에만 미해결 회차가 있어도 MEMBERSHIP-009다")
    void rejectsBlockingMembershipAndUnresolvedPayment() {
        SubscriptionReceipt first = accept();
        Throwable blocked = catchThrowable(() -> transactionTemplate.execute(s -> processor.accept(member.getId(),
            consent, member.getId() + ":other-key", hash, LocalDateTime.now(clock))));
        assertThat(codeOf(blocked)).isEqualTo(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE);

        // 대기 이용권을 종료(C-3b)해도 회차가 미해결이면 새 구독은 009
        transactionTemplate.executeWithoutResult(s -> {
            memberService.lockActiveMember(member.getId());
            billing.applyFirstPaymentFailed(first.membershipId(), LocalDateTime.now(clock));
        });
        Throwable unresolved = catchThrowable(() -> transactionTemplate.execute(s -> processor.accept(member.getId(),
            consent, member.getId() + ":third-key", hash, LocalDateTime.now(clock))));
        assertThat(codeOf(unresolved)).isEqualTo(MemberErrorCode.MEMBERSHIP_PAYMENT_IN_PROGRESS);
    }

    // ---------- S-7·G-1: 플래그 OFF ----------

    @Test
    @DisplayName("플래그 OFF: 신규 접수는 BILLING-009지만 기존 키의 결과 조회는 그대로 되고, READY 시도의 PG POST는 나가지 않는다")
    void flagOffBlocksOnlyNewAcceptanceAndNewPost() {
        SubscriptionReceipt first = accept();
        MembershipSubscriptionProcessor offProcessor = new MembershipSubscriptionProcessor(memberService, billing,
            queryService, payments, attempts, billingKeys, () -> false, properties);

        SubscriptionReceipt existing = transactionTemplate.execute(s -> offProcessor.accept(member.getId(), consent,
            storedKey, hash, LocalDateTime.now(clock)));
        assertThat(existing.existingStatus()).isEqualTo(MembershipSubscribeStatus.IN_PROGRESS);
        assertThat(existing.attemptId()).isEqualTo(first.attemptId());

        Throwable thrown = catchThrowable(() -> transactionTemplate.execute(s -> offProcessor.accept(
            member.getId(), consent, member.getId() + ":new-key", hash, LocalDateTime.now(clock))));
        assertThat(codeOf(thrown)).isEqualTo(PaymentErrorCode.MEMBERSHIP_BILLING_DISABLED);

        MembershipChargeExecutor offExecutor = new MembershipChargeExecutor(() -> false, properties, memberService,
            dispatchProcessor, resultProcessor, attempts, portOneBillingClient, portOnePaymentClient, clock);
        assertThat(offExecutor.execute(member.getId(), first.attemptId()))
            .isEqualTo(MembershipChargeOutcome.NOT_DISPATCHED);
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.READY);
    }

    // ---------- X-5(축소): 선점 경합 ----------

    @Test
    @DisplayName("다른 Worker가 임대 안에서 선점한 시도에 execute하면 재조회 없이 IN_PROGRESS이고 시도는 DISPATCHING 그대로다")
    void loserOfClaimWaitsWithoutRecheck() {
        SubscriptionReceipt first = accept();
        MembershipChargeTicket ticket = dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();

        MembershipChargeOutcome outcome = executor.execute(member.getId(), first.attemptId());

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(attempt.getDispatchedAt()).isEqualTo(ticket.dispatchedAt());
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
    }

    @Test
    @DisplayName("POST를 보내지 않은 같은 선점자만 READY로 되돌리고, 다른 선점 시각으로는 되돌리지 못한다")
    void releaseUnsentOnlyForSameClaim() {
        SubscriptionReceipt first = accept();
        MembershipChargeTicket ticket = dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        MembershipChargeTicket other = new MembershipChargeTicket(ticket.attemptId(), ticket.membershipPaymentId(),
            ticket.membershipId(), ticket.portonePaymentId(), ticket.billingKey(), ticket.amount(),
            ticket.dispatchedAt().plusSeconds(1), ticket.acceptUntil());

        assertThat(dispatchProcessor.releaseUnsent(other)).isFalse();
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(dispatchProcessor.releaseUnsent(ticket)).isTrue();
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(attempt.getDispatchedAt()).isNull();
    }

    // ---------- T-1·T-2·T-4·T-12: PAID 반영 ----------

    @Test
    @DisplayName("PAID 반영은 이용권 활성화(start·activated·anchor = pgPaidAt)·회차 PAID·기간 덧씌움을 한 번만 한다")
    void appliesPaidOnce() {
        SubscriptionReceipt first = accept();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).minusMinutes(1).withNano(0);
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();

        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, FEE));
        MembershipChargeOutcome again = resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, FEE));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        assertThat(again).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        Membership membership = memberships.findById(first.membershipId()).orElseThrow();
        assertThat(membership.getStartAt()).isEqualTo(pgPaidAt);
        assertThat(membership.getActivatedAt()).isEqualTo(pgPaidAt);
        assertThat(membership.getBillingAnchorDate()).isEqualTo(pgPaidAt.toLocalDate());
        assertThat(membership.getEndAt()).isEqualTo(pgPaidAt.plusMonths(1));
        assertThat(membership.getGraceEndAt()).isEqualTo(pgPaidAt.plusMonths(1).plusDays(3));
        MembershipPayment payment = payments.findById(paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(pgPaidAt);
        assertThat(payment.getPeriodStartDate()).isEqualTo(pgPaidAt.toLocalDate());
        assertThat(payment.getPeriodEndDate()).isEqualTo(pgPaidAt.toLocalDate().plusMonths(1).minusDays(1));
        assertThat(payment.getMembershipAppliedAt()).isNotNull();
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt.getOpsReviewRequiredAt()).isNull();
        assertThat(membershipService.getTypeAt(member.getId(), LocalDateTime.now(clock))).contains(MembershipType.PAID);
        assertThat(refundCount()).isZero();
    }

    @Test
    @DisplayName("해지 예약된 결제 대기 행의 PAID는 graceEndAt을 non-null로 넘겨도 활성화되고 예약·grace null이 유지된다")
    void paidOnCancelReservedPending() {
        SubscriptionReceipt first = accept();
        cancelReservation.reserve(member.getId(), first.membershipId());
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);

        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, FEE));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        Membership membership = memberships.findById(first.membershipId()).orElseThrow();
        assertThat(membership.getActivatedAt()).isEqualTo(pgPaidAt);
        assertThat(membership.isAutoRenew()).isFalse();
        assertThat(membership.getCancelReservedAt()).isNotNull();
        assertThat(membership.getGraceEndAt()).isNull();
    }

    @Test
    @DisplayName("확인이 D(24시간) 이상 늦은 PAID도 PG 시각 기준으로 활성화하고 운영 확인 표시만 남긴다")
    void longDelayedPaid() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).minusHours(25).withNano(0);

        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, FEE));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getActivatedAt()).isEqualTo(pgPaidAt);
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.FIRST_PAYMENT_LONG_DELAY);
        assertThat(attempt.getRefundRequiredAt()).isNull();
    }

    // ---------- T-3·T-9: FAILED·UNKNOWN ----------

    @Test
    @DisplayName("조회로 확정된 FAILED는 대기 이용권을 종료하고 회차·시도를 FAILED로 닫는다(체험 자격 유지)")
    void failedClosesPending() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();

        String paymentId = paymentIdOf(first.attemptId());
        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), first.attemptId(),
            MembershipPgVerdict.of(paymentId, FEE, new PortOnePayment(paymentId, "tx", PortOnePaymentStatus.FAILED, FEE,
                "KRW", null, null, "declined")));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.CLOSED_UNPAID);
        Membership membership = memberships.findById(first.membershipId()).orElseThrow();
        assertThat(membership.getTerminatedAt()).isNotNull();
        assertThat(membership.getActivatedAt()).isNull();
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getFailureReason()).isEqualTo("PG_FAILED");
        assertThat(membershipService.isTrialEligible(member.getId())).isTrue();
        assertThat(existsUnresolved()).isFalse();
    }

    @Test
    @DisplayName("결과 불명·404는 시도·회차를 UNKNOWN으로 두고 이용권을 바꾸지 않으며 새 시도도 만들지 않는다")
    void unresolvedKeepsUnknown() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();

        assertThat(resultProcessor.apply(member.getId(), first.attemptId(),
            MembershipPgVerdict.unresolved("COMMON-009"))).isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);
        assertThat(resultProcessor.apply(member.getId(), first.attemptId(), MembershipPgVerdict.notFound()))
            .isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);

        assertThat(attempts.findById(first.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.UNKNOWN);
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.UNKNOWN);
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getActivatedAt()).isNull();
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId())).hasSize(1);
        assertThat(existsUnresolved()).isTrue();
    }

    // ---------- T-6·T-10·T-11·T-14·R-8: 반영하지 않는 PAID·환불·미정산 ----------

    @Test
    @DisplayName("PAID인데 첫 결제기간이 끝났으면 활성화 없이 대기 행 종료·회차 FAILED·SYSTEM 환불 3,900 REQUESTED이고 C-17이 막는다")
    void paidAfterPeriodEnded() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).minusMonths(2).withNano(0);

        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, FEE));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertRefundRequested(first, FEE, MembershipOpsReviewReason.FIRST_PAYMENT_PERIOD_ENDED, true);
    }

    @Test
    @DisplayName("KRW 3,800 수납(금액 불일치)은 활성화 없이 실제 수납액 3,800 SYSTEM 환불을 요청하고 재실행해도 환불은 1건이다")
    void krwAmountMismatchRefundsActualAmount() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);

        MembershipChargeOutcome outcome =
            resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, 3_800L));
        MembershipChargeOutcome again =
            resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, 3_800L));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(again).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertRefundRequested(first, 3_800L, MembershipOpsReviewReason.PAID_AMOUNT_MISMATCH, true);
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getFailureReason()).isNull();
        assertThat(payments.findById(paymentId()).orElseThrow().getFailureReason()).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("USD 수납·ID 불일치 FAILED·paidAt 없는 PAID는 MANUAL_REVIEW이고 환불 금액을 만들지 않으며 혜택도 없다")
    void untrustedResultsGoToManualReview() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        String paymentId = paymentIdOf(first.attemptId());
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);

        MembershipChargeOutcome usd = resultProcessor.apply(member.getId(), first.attemptId(),
            MembershipPgVerdict.of(paymentId, FEE, new PortOnePayment(paymentId, "tx", PortOnePaymentStatus.PAID,
                FEE, "USD", pgPaidAt, null, null)));
        MembershipChargeOutcome wrongIdFailed = resultProcessor.apply(member.getId(), first.attemptId(),
            MembershipPgVerdict.of(paymentId, FEE, new PortOnePayment("mpay_other", "tx", PortOnePaymentStatus.FAILED,
                FEE, "KRW", null, null, null)));
        MembershipChargeOutcome noPaidAt = resultProcessor.apply(member.getId(), first.attemptId(),
            MembershipPgVerdict.of(paymentId, FEE, new PortOnePayment(paymentId, "tx", PortOnePaymentStatus.PAID,
                FEE, "KRW", null, null, null)));

        assertThat(usd).isEqualTo(MembershipChargeOutcome.MANUAL_REVIEW);
        assertThat(wrongIdFailed).isEqualTo(MembershipChargeOutcome.MANUAL_REVIEW);
        assertThat(noPaidAt).isEqualTo(MembershipChargeOutcome.MANUAL_REVIEW);
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.MANUAL_REVIEW);
        assertThat(attempt.getFailureReason()).isEqualTo(MembershipPgVerdict.CODE_UNTRUSTED_CURRENCY);
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.MANUAL_REVIEW);
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getActivatedAt()).isNull();
        assertThat(refundCount()).isZero();
        assertThat(existsUnresolved()).isTrue();
    }

    @Test
    @DisplayName("C-3a CONFLICT(이용권이 종료됨)가 확인되면 PAID를 반영하지 않고 SYSTEM 환불 요청으로 끝낸다")
    void conflictWithConfirmedReasonRefunds() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        time.membershipTerminated(first.membershipId(), LocalDateTime.now(clock).withNano(0));

        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), first.attemptId(),
            paid(LocalDateTime.now(clock).withNano(0), FEE));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertRefundRequested(first, FEE, MembershipOpsReviewReason.PAID_CONFLICT, false);
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getActivatedAt()).isNull();
    }

    @Test
    @DisplayName("T-15(b) 0b: 첫 결제 회차 미반영 + 이용권 활성 + 근거 회차 없음(I-22 위반) → 상태 전이·환불 없이 예외로 TX2 롤백, C-17 유지")
    void activatedMembershipWithUnappliedPaymentRollsBack() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        time.membershipActivated(first.membershipId(), pgPaidAt);

        Throwable thrown = catchThrowable(() -> resultProcessor.apply(member.getId(), first.attemptId(),
            paid(pgPaidAt, FEE)));
        Throwable again = catchThrowable(() -> resultProcessor.apply(member.getId(), first.attemptId(),
            paid(pgPaidAt, 3_800L)));

        assertThat(thrown).isInstanceOf(MembershipSettlementInvariantException.class);
        assertThat(((MembershipSettlementInvariantException) thrown).getKind())
            .isEqualTo(Kind.ACTIVATED_WITHOUT_APPLIED_PAYMENT);
        assertThat(again).isInstanceOf(MembershipSettlementInvariantException.class);
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(attempt.getRefundRequiredAt()).isNull();
        assertThat(attempt.getOpsReviewRequiredAt()).isNull();
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getActivatedAt()).isEqualTo(pgPaidAt);
        assertThat(refundCount()).isZero();
        assertThat(existsUnresolved()).isTrue();
    }

    @Test
    @DisplayName("T-15(a) 0a: 이용권 활성 + 반영된 다른 회차가 있음(중복 수납) → 반영 없이 실제 수납액 SYSTEM 환불, 이용권 변경 없음")
    void duplicatePaidOnActivatedMembershipRefunds() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        LocalDateTime activatedAt = pgPaidAt.minusDays(10);
        time.membershipActivatedFor(first.membershipId(), activatedAt, activatedAt.plusMonths(1),
            activatedAt.plusMonths(1).plusDays(3));
        // 활성화의 근거가 된 다른(반영된) 회차 — 기간이 겹치지 않게 열흘 전 날짜로
        time.otherPayment(first.membershipId(), FEE, MembershipPaymentStatus.PAID, activatedAt.toLocalDate(),
            activatedAt.plusMonths(1).plusDays(3), activatedAt, null);

        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, FEE));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_CONFLICT);
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        Membership membership = memberships.findById(first.membershipId()).orElseThrow();
        assertThat(membership.getActivatedAt()).isEqualTo(activatedAt);
        assertThat(membership.getTerminatedAt()).isNull();
        assertThat(refundCount()).isEqualTo(1);
        assertThat(existsUnresolved()).isTrue();
    }

    // ---------- R-6: READY 임대 만료 분기 ----------

    @Test
    @DisplayName("R-6 임대가 안 끝난 READY·다른 Worker가 선점한 DISPATCHING은 만료 처리하지 않고 상태별 결과만 돌려준다")
    void expiredReadyLeavesUnexpiredOrClaimedAlone() {
        SubscriptionReceipt first = accept();
        LocalDateTime now = LocalDateTime.now(clock);

        ExpiredReadyResult notExpired = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(), now);
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        ExpiredReadyResult claimed = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(),
            now.plusHours(1));

        assertThat(notExpired.outcome()).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
        assertThat(claimed.outcome()).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId())).hasSize(1);
    }

    @Test
    @DisplayName("R-6 창·횟수 안이면 새 READY(임대 = min(now+L, 창 끝))를 만들고, 같은 회차에 유효한 READY가 또 있으면 그것을 쓴다")
    void expiredReadyReissuesWithinWindow() {
        SubscriptionReceipt first = accept();
        LocalDateTime firstRequestedAt = attempts.findById(first.attemptId()).orElseThrow().getRequestedAt();
        LocalDateTime now = firstRequestedAt.plusMinutes(25);

        ExpiredReadyResult reissued = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(), now);

        assertThat(reissued.outcome()).isEqualTo(MembershipChargeOutcome.RETRY_READY);
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        MembershipPaymentAttempt second = attempts.findById(reissued.attemptId()).orElseThrow();
        assertThat(second.getAttemptNo()).isEqualTo(2);
        assertThat(second.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(second.getLeaseExpiresAt()).isEqualTo(firstRequestedAt.plus(properties.firstChargeAcceptWindow()));
        assertThat(second.getPortonePaymentId()).isNotEqualTo(paymentIdOf(first.attemptId()));

        // 첫 시도를 다시 만료된 READY로 돌려놓고 보면 유효한 READY(2번)를 재사용하고 새 ID를 만들지 않는다
        time.attemptBackToExpiredReady(first.attemptId(), now.minusMinutes(1));
        ExpiredReadyResult reused = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(), now);
        assertThat(reused.outcome()).isEqualTo(MembershipChargeOutcome.RETRY_READY);
        assertThat(reused.attemptId()).isEqualTo(second.getId());
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId())).hasSize(2);
    }

    @Test
    @DisplayName("R-6 같은 회차에 DISPATCHING·UNKNOWN 시도가 있으면 회차를 미해결로 두고 새 시도도 FAILED도 만들지 않는다")
    void expiredReadyKeepsUnresolvedWhenPgMayHaveSeen() {
        SubscriptionReceipt first = accept();
        LocalDateTime now = LocalDateTime.now(clock).plusMinutes(11);
        ExpiredReadyResult reissued = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(), now);
        dispatchProcessor.claim(reissued.attemptId()).ticket().orElseThrow();
        time.attemptBackToExpiredReady(first.attemptId(), now.minusMinutes(1));

        ExpiredReadyResult held = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(), now);

        assertThat(held.outcome()).isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.CANCELED);
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId())).hasSize(2);
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("R-6 창(30분)이 끝났거나 시도 수가 3이면 회차 FAILED(NOT_DISPATCHED) + 대기 이용권 종료, 환불 없음")
    void expiredReadyClosesWhenWindowOrCountExhausted() {
        SubscriptionReceipt first = accept();
        LocalDateTime firstRequestedAt = attempts.findById(first.attemptId()).orElseThrow().getRequestedAt();

        ExpiredReadyResult closed = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(),
            firstRequestedAt.plus(properties.firstChargeAcceptWindow()));

        assertThat(closed.outcome()).isEqualTo(MembershipChargeOutcome.CLOSED_UNPAID);
        MembershipPayment payment = payments.findById(paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("NOT_DISPATCHED");
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getTerminatedAt()).isNotNull();
        assertThat(refundCount()).isZero();
        assertThat(existsUnresolved()).isFalse();

        // 확정된 회차의 만료 READY를 다시 보면 CANCELED로만 접고 그 밖은 바꾸지 않는다(I-16)
        time.attemptBackToExpiredReady(first.attemptId(), firstRequestedAt);
        ExpiredReadyResult again = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(),
            firstRequestedAt.plusMinutes(1));
        assertThat(again.outcome()).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId())).hasSize(1);
    }

    @Test
    @DisplayName("R-6 시도 수 상한: 세 번째 시도까지 만료되면 창 안이라도 새 시도를 만들지 않고 회차를 닫는다")
    void expiredReadyRespectsMaxAttempts() {
        SubscriptionReceipt first = accept();
        LocalDateTime firstRequestedAt = attempts.findById(first.attemptId()).orElseThrow().getRequestedAt();
        ExpiredReadyResult second = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(),
            firstRequestedAt.plusMinutes(11));
        ExpiredReadyResult third = resultProcessor.handleExpiredReady(member.getId(), second.attemptId(),
            firstRequestedAt.plusMinutes(22));
        assertThat(third.outcome()).isEqualTo(MembershipChargeOutcome.RETRY_READY);
        // 세 번째 시도의 임대는 창의 끝(+30)이다. 창이 아직 열린 시점에 횟수만으로 닫히는지 보기 위해 임대만 앞당긴다
        time.attemptLeaseExpiresAt(third.attemptId(), firstRequestedAt.plusMinutes(25));

        ExpiredReadyResult closed = resultProcessor.handleExpiredReady(member.getId(), third.attemptId(),
            firstRequestedAt.plusMinutes(26));

        assertThat(closed.outcome()).isEqualTo(MembershipChargeOutcome.CLOSED_UNPAID);
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId())).hasSize(3)
            .allMatch(a -> a.getStatus() == MembershipPaymentAttemptStatus.CANCELED);
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
    }

    @Test
    @DisplayName("R-6 확정 회차(PAID·FAILED)에 만료 READY와 다른 유효 READY가 함께 남아도 RETRY_READY가 아니다 — 실행 대상 없음")
    void expiredReadyOnConfirmedPaymentDoesNotReturnOtherReady() {
        SubscriptionReceipt first = accept();
        LocalDateTime firstRequestedAt = attempts.findById(first.attemptId()).orElseThrow().getRequestedAt();
        ExpiredReadyResult second = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(),
            firstRequestedAt.plusMinutes(11));
        assertThat(second.outcome()).isEqualTo(MembershipChargeOutcome.RETRY_READY);

        List<MembershipPaymentStatus> confirmedStatuses =
            List.of(MembershipPaymentStatus.PAID, MembershipPaymentStatus.FAILED);
        for (MembershipPaymentStatus confirmed : confirmedStatuses) {
            time.paymentStatus(paymentId(), confirmed);
            time.attemptBackToExpiredReady(first.attemptId(), firstRequestedAt.plusMinutes(1));

            ExpiredReadyResult result = resultProcessor.handleExpiredReady(member.getId(), first.attemptId(),
                firstRequestedAt.plusMinutes(12));

            assertThat(result.outcome()).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
            assertThat(result.attemptId()).isNull();
            assertThat(attempts.findById(second.attemptId()).orElseThrow().getStatus())
                .isEqualTo(MembershipPaymentAttemptStatus.READY);
            assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId())).hasSize(2);
        }
    }

    @Test
    @DisplayName("T-8(Processor): TX2 안의 실제 UNIQUE 충돌(기간 덧씌우기)이면 시도·회차·반영 표시·이용권 활성화가 모두 롤백되고, "
        + "충돌을 치우면 같은 판정으로 다시 반영된다")
    void tx2ConstraintFailureRollsBackEverything() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        // 접수일과 다른 날짜의 PG 성공 시각 — 접수 회차의 임시 시작일과 겹치지 않아 픽스처 INSERT는 성공하고 TX2만 충돌한다
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).plusDays(1).withNano(0);
        Long conflicting = time.otherPayment(first.membershipId(), FEE, MembershipPaymentStatus.FAILED,
            pgPaidAt.toLocalDate(), pgPaidAt.plusMonths(1).plusDays(3), null, "TEST_FIXTURE");

        Throwable thrown = catchThrowable(() -> resultProcessor.apply(member.getId(), first.attemptId(),
            paid(pgPaidAt, FEE)));

        assertThat(hasConstraintViolation(thrown)).isTrue();
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(attempt.getPgPaidAt()).isNull();
        MembershipPayment payment = payments.findById(paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(payment.getMembershipAppliedAt()).isNull();
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getActivatedAt()).isNull();
        assertThat(refundCount()).isZero();

        time.deletePayment(conflicting);
        MembershipChargeOutcome outcome = resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, FEE));

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        assertThat(memberships.findById(first.membershipId()).orElseThrow().getActivatedAt()).isEqualTo(pgPaidAt);
    }

    @Test
    @DisplayName("0b 운영 표시 TX는 원인을 다시 확인한다 — 그대로면 자동 정합화 중단 표시, 그 사이 해소됐으면 아무것도 남기지 않는다")
    void flagInconsistentReverifiesCause() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        time.membershipActivated(first.membershipId(), pgPaidAt);
        LocalDateTime now = LocalDateTime.now(clock);

        // 해소된 경우: 이용권 활성 표시가 정정됨 → 표시하지 않는다
        time.membershipActivationCleared(first.membershipId());
        boolean flaggedWhenResolved = resultProcessor.flagInconsistent(member.getId(), first.attemptId(),
            Kind.ACTIVATED_WITHOUT_APPLIED_PAYMENT, now);
        assertThat(flaggedWhenResolved).isFalse();
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getOpsReviewRequiredAt()).isNull();

        // 원인이 그대로인 경우 → 상태는 그대로 두고 표시만
        time.membershipActivated(first.membershipId(), pgPaidAt);
        boolean flagged = resultProcessor.flagInconsistent(member.getId(), first.attemptId(),
            Kind.ACTIVATED_WITHOUT_APPLIED_PAYMENT, now);
        boolean flaggedAgain = resultProcessor.flagInconsistent(member.getId(), first.attemptId(),
            Kind.ACTIVATED_WITHOUT_APPLIED_PAYMENT, now);

        assertThat(flagged).isTrue();
        assertThat(flaggedAgain).isFalse();
        MembershipPaymentAttempt attempt = attempts.findById(first.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
        assertThat(attempt.isAutoReconcileHalted()).isTrue();
        assertThat(attempt.getRefundRequiredAt()).isNull();
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.PENDING);
        assertThat(refundCount()).isZero();
        assertThat(existsUnresolved()).isTrue();
        assertThat(resultProcessor.apply(member.getId(), first.attemptId(), paid(pgPaidAt, FEE)))
            .isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED);
    }

    @Test
    @DisplayName("C-17 미정산은 SYSTEM 환불 COMPLETED만 푼다 — REQUESTED·FAILED는 그대로 막는다")
    void unsettledUntilSystemRefundCompleted() {
        SubscriptionReceipt first = accept();
        dispatchProcessor.claim(first.attemptId()).ticket().orElseThrow();
        resultProcessor.apply(member.getId(), first.attemptId(), paid(LocalDateTime.now(clock).minusMonths(2), FEE));
        assertThat(existsUnresolved()).isTrue();

        jdbc.update("UPDATE membership_refund SET status = ? WHERE membership_payment_attempt_id = ?",
            MembershipRefundStatus.FAILED.name(), first.attemptId());
        assertThat(existsUnresolved()).isTrue();

        jdbc.update("UPDATE membership_refund SET status = ? WHERE membership_payment_attempt_id = ?",
            MembershipRefundStatus.COMPLETED.name(), first.attemptId());
        assertThat(existsUnresolved()).isFalse();
    }

    // ---------- 보조 ----------

    private SubscriptionReceipt accept() {
        return transactionTemplate.execute(s -> processor.accept(member.getId(), consent, storedKey, hash,
            LocalDateTime.now(clock)));
    }

    private MembershipPgVerdict paid(LocalDateTime pgPaidAt, long amount) {
        String paymentId = paymentIdOf(latestAttemptId());
        return MembershipPgVerdict.of(paymentId, FEE, new PortOnePayment(paymentId, "tx_1", PortOnePaymentStatus.PAID,
            amount, "KRW", pgPaidAt, null, null));
    }

    private Long latestAttemptId() {
        return attempts.findTopByMembershipPaymentIdOrderByAttemptNoDesc(paymentId()).orElseThrow().getId();
    }

    private String paymentIdOf(Long attemptId) {
        return attempts.findById(attemptId).orElseThrow().getPortonePaymentId();
    }

    private Long paymentId() {
        return payments.findByIdempotencyKey(storedKey).orElseThrow().getId();
    }

    private boolean existsUnresolved() {
        return Boolean.TRUE.equals(transactionTemplate.execute(s -> {
            memberService.lockActiveMember(member.getId());
            return queryService.existsUnresolved(billing.findMembershipIds(member.getId()));
        }));
    }

    private long refundCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM membership_refund r JOIN membership_payment p "
            + "ON r.membership_payment_id = p.id WHERE p.idempotency_key = ?", Long.class, storedKey);
        return count == null ? 0 : count;
    }

    private void assertRefundRequested(SubscriptionReceipt receipt, long amount, MembershipOpsReviewReason reason,
                                       boolean membershipClosed) {
        MembershipPaymentAttempt attempt = attempts.findById(receipt.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(attempt.getRefundRequiredAt()).isNotNull();
        assertThat(attempt.getOpsReviewReason()).isEqualTo(reason);
        assertThat(payments.findById(paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipPaymentStatus.FAILED);
        Membership membership = memberships.findById(receipt.membershipId()).orElseThrow();
        assertThat(membership.getActivatedAt()).isNull();
        if (membershipClosed) {
            assertThat(membership.getTerminatedAt()).isNotNull();
        }
        // 환불 행의 연관(시도)은 LAZY — 짧은 트랜잭션 안에서 걸러 읽는다
        List<MembershipRefund> rows = transactionTemplate.execute(s -> refunds.findAll().stream()
            .filter(r -> r.getMembershipPaymentAttempt().getId().equals(receipt.attemptId())).toList());
        assertThat(rows).hasSize(1);
        MembershipRefund refund = rows.get(0);
        assertThat(refund.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(refund.getAmount()).isEqualTo(amount);
        assertThat(refund.getReason()).isEqualTo(reason.name());
        assertThat(refund.getIdempotencyKey()).isEqualTo("mrf_sys_" + receipt.attemptId() + "_1");
        assertThat(existsUnresolved()).isTrue();
    }

    private Member saveMember() {
        String suffix = UUID.randomUUID().toString();
        Member saved = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
            .name("첫결제테스트").passwordHash(encoder.encode(PASSWORD)).build());
        memberIds.add(saved.getId());
        return saved;
    }

    private BillingKey saveActiveKey(Long memberId) {
        return billingKeys.saveAndFlush(BillingKey.builder().memberId(memberId)
            .portoneBillingKey("billing-key-test-" + UUID.randomUUID()).status(BillingKeyStatus.ACTIVE)
            .issuedAt(LocalDateTime.now(clock)).build());
    }

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

    private static ErrorCode codeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }
}
