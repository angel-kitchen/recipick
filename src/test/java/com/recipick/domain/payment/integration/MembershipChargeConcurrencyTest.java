package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.facade.MembershipChargeExecutor;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor.ExpiredReadyResult;
import com.recipick.domain.payment.facade.MembershipSubscriptionFacade;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor.SubscriptionReceipt;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipRequestHash;
import com.recipick.domain.payment.service.MembershipSubscribeResult;
import com.recipick.domain.payment.service.MembershipSubscribeStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

// 실제 MySQL·복수 스레드 경합. 순차 호출로 대신하지 않는다.
// 스텁이 첫 POST를 래치로 붙잡아 이긴 쪽이 PG 호출 중인 동안 진 쪽의 결과·PG 호출 횟수를 본다. 고정 sleep으로 순서를 맞추지 않고
// 래치·Future에 한도를 두며, 작업 스레드가 끝난 것을 확인한 뒤에만 DB를 정리한다(끝나지 않으면 정리를 건너뛰고 실패)
class MembershipChargeConcurrencyTest extends MembershipChargeFlowSupport {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired MembershipSubscriptionFacade facade;
    @Autowired MembershipSubscriptionProcessor processor;
    @Autowired MembershipChargeExecutor executor;
    @Autowired MembershipChargeResultProcessor resultProcessor;
    @Autowired MembershipBillingService billing;
    @Autowired MembershipPaymentRepository payments;
    @Autowired MembershipPaymentAttemptRepository attempts;

    private Member member;
    private MembershipConsent consent;
    private String key;

    @BeforeEach
    void setUp() {
        member = saveMember();
        saveActiveKey(member.getId());
        consent = new MembershipConsent(FEE, "v1", LocalDateTime.now(clock).minusDays(1));
        key = "conc-" + UUID.randomUUID();
    }

    @RepeatedTest(3)
    @DisplayName("X-1: 같은 멱등 키로 동시 구독 → 이용권·회차·시도 1건, PG POST 1회. 진 쪽은 이긴 쪽이 PG 호출 중일 때 IN_PROGRESS")
    void sameKeyConcurrentSubscribe() throws Exception {
        CountDownLatch postArrived = PORTONE.holdPosts();
        CountDownLatch start = new CountDownLatch(1);
        Callable<MembershipSubscribeResult> subscribe = () -> {
            awaitLatch(start);
            return facade.subscribe(member.getId(), consent, key);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletionService<MembershipSubscribeResult> done = new ExecutorCompletionService<>(pool);
            done.submit(subscribe);
            done.submit(subscribe);
            start.countDown();
            assertThat(postArrived.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

            MembershipSubscribeResult loser = take(done);
            assertThat(loser.status()).isEqualTo(MembershipSubscribeStatus.IN_PROGRESS);
            assertThat(PORTONE.totalPostCount()).isEqualTo(1);
            assertThat(PORTONE.totalGetCount()).isZero();

            PORTONE.releasePosts();
            take(done);
        } finally {
            PORTONE.releasePosts();
            finish(pool);
        }

        assertThat(billing.findMembershipIds(member.getId())).hasSize(1);
        Long paymentRowId = payments.findByIdempotencyKey(member.getId() + ":" + key).orElseThrow().getId();
        assertThat(attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentRowId)).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("X-5: 같은 READY에 동시 execute → 선점 1건. 진 쪽은 신선한 DISPATCHING이라 GET·POST 없이 IN_PROGRESS")
    void concurrentExecuteOnSameReady() throws Exception {
        SubscriptionReceipt receipt = accept();
        String paymentId = attempts.findById(receipt.attemptId()).orElseThrow().getPortonePaymentId();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        PORTONE.billingKeyPaymentPaid(paymentId, pgPaidAt);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", pgPaidAt);
        CountDownLatch postArrived = PORTONE.holdPosts();
        CountDownLatch start = new CountDownLatch(1);
        Callable<MembershipChargeOutcome> execute = () -> {
            awaitLatch(start);
            return executor.execute(member.getId(), receipt.attemptId());
        };

        MembershipChargeOutcome winner;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletionService<MembershipChargeOutcome> done = new ExecutorCompletionService<>(pool);
            done.submit(execute);
            done.submit(execute);
            start.countDown();
            assertThat(postArrived.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

            MembershipChargeOutcome loser = take(done);
            assertThat(loser).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
            assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
            assertThat(PORTONE.getCount(paymentId)).isZero();

            PORTONE.releasePosts();
            winner = take(done);
        } finally {
            PORTONE.releasePosts();
            finish(pool);
        }

        assertThat(winner).isEqualTo(MembershipChargeOutcome.ACTIVATED);
        assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("X-6: READY 만료 처리와 선점이 동시에 오면 둘 중 하나만 성립한다. 취소됐으면 그 시도의 PG POST는 0")
    void expiryRacesWithClaim() throws Exception {
        SubscriptionReceipt receipt = accept();
        MembershipPaymentAttempt attempt = attempts.findById(receipt.attemptId()).orElseThrow();
        String paymentId = attempt.getPortonePaymentId();
        LocalDateTime pgPaidAt = LocalDateTime.now(clock).withNano(0);
        PORTONE.billingKeyPaymentPaid(paymentId, pgPaidAt);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", pgPaidAt);
        // 선점은 실제 시각(임대 안)으로, 만료 처리는 임대가 끝난 시각을 넘겨 — 둘 다 자격이 있는 상태에서 잠금으로만 갈린다
        LocalDateTime expiredNow = attempt.getLeaseExpiresAt().plusSeconds(1);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> claimAndCharge = () -> {
            awaitLatch(start);
            return executor.execute(member.getId(), receipt.attemptId());
        };
        Callable<Object> expire = () -> {
            awaitLatch(start);
            return resultProcessor.handleExpiredReady(member.getId(), receipt.attemptId(), expiredNow);
        };

        List<Object> results = runConcurrently(start, List.of(claimAndCharge, expire));

        MembershipChargeOutcome charged = (MembershipChargeOutcome) results.get(0);
        ExpiredReadyResult expired = (ExpiredReadyResult) results.get(1);
        MembershipPaymentAttemptStatus finalStatus = attempts.findById(receipt.attemptId()).orElseThrow().getStatus();
        if (finalStatus == MembershipPaymentAttemptStatus.CANCELED) {
            // 만료가 먼저 — 선점은 실패했고 그 시도로는 PG에 아무것도 보내지 않았다
            assertThat(PORTONE.postCount(paymentId)).isZero();
            assertThat(charged).isNotEqualTo(MembershipChargeOutcome.ACTIVATED);
        } else {
            // 선점이 먼저 — 만료 처리는 아무것도 바꾸지 않았다
            assertThat(finalStatus).isEqualTo(MembershipPaymentAttemptStatus.PAID);
            assertThat(expired.outcome()).isIn(MembershipChargeOutcome.IN_PROGRESS,
                MembershipChargeOutcome.ALREADY_APPLIED);
            assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
        }
    }

    // ---------- 보조 ----------

    private SubscriptionReceipt accept() {
        return transactionTemplate.execute(s -> processor.accept(member.getId(), consent, member.getId() + ":" + key,
            MembershipRequestHash.of(consent), LocalDateTime.now(clock)));
    }

    private static void awaitLatch(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(WAIT.toSeconds(), TimeUnit.SECONDS)) {
            throw new IllegalStateException("출발 신호를 받지 못함");
        }
    }

    private static <T> T take(CompletionService<T> done) throws Exception {
        Future<T> future = done.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertThat(future).as("작업이 한도 안에 끝나야 한다").isNotNull();
        return future.get();
    }

    // 결과는 넘긴 순서대로. 실패해도 작업 스레드 종료를 확인한 뒤 돌아간다
    private List<Object> runConcurrently(CountDownLatch start, List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(WAIT.toSeconds(), TimeUnit.SECONDS));
            }
            return results;
        } finally {
            finish(pool);
        }
    }

    // 작업 스레드가 끝났는지 확인한다. 끝나지 않았으면 정리를 건너뛰게 표시하고 실패시킨다
    private void finish(ExecutorService pool) {
        pool.shutdown();
        boolean terminated;
        try {
            terminated = pool.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS);
            if (!terminated) {
                pool.shutdownNow();
                terminated = pool.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            terminated = false;
        }
        if (!terminated) {
            workersTerminated = false;
            throw new AssertionError("작업 스레드가 한도 안에 끝나지 않았습니다. 픽스처 정리를 건너뜁니다.");
        }
    }
}
