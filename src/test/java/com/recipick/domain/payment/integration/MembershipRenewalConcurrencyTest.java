package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipRenewalDecision;
import com.recipick.domain.payment.service.MembershipRenewalDecision.Kind;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

// 갱신 경합을 실제 MySQL·복수 스레드로 재현한다. 스텁이 첫 POST를 래치로 붙잡아 이긴 쪽이 PG 호출 중인 동안 진 쪽을 본다.
// 고정 sleep으로 겹침을 가정하지 않고 래치·Future 한도·DB의 잠금 대기 상태로 순서를 확인하며, 작업 스레드가 끝난 뒤에만 정리한다
class MembershipRenewalConcurrencyTest extends MembershipRenewalFlowSupport {

    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final Logger SQL_LOGGER = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
    private static final Logger BIND_LOGGER = (Logger) LoggerFactory.getLogger("org.hibernate.orm.jdbc.bind");
    private static final Pattern PRIMARY_KEY_CONDITION = Pattern.compile("^(\\w+\\.)?`?id`?\\s*=\\s*\\?$");
    private static final Pattern BOUND_VALUE = Pattern.compile("<-\\s*\\[(\\d+)]");

    @Autowired MemberService memberService;

    @RepeatedTest(3)
    @DisplayName("두 스케줄러가 같은 갱신 후보를 동시에 집어도 회차 1개·시도 1개·POST 1회·연장 1회다")
    void twoSchedulersSameTarget() throws Exception {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);
        CountDownLatch postArrived = PORTONE.holdPosts();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> run = () -> {
            awaitLatch(start);
            return renewalFacade.runOnce();
        };

        // when
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletionService<Integer> done = new ExecutorCompletionService<>(pool);
            done.submit(run);
            done.submit(run);
            start.countDown();
            assertThat(postArrived.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            take(done);
            assertThat(PORTONE.totalPostCount()).isEqualTo(1);
            assertThat(PORTONE.totalGetCount()).isZero();
            PORTONE.releasePosts();
            take(done);
        } finally {
            PORTONE.releasePosts();
            finish(pool);
        }

        // then
        MembershipPayment payment = onlyRenewalPayment(membershipId);
        assertThat(attemptsOf(payment.getId())).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
    }

    @RepeatedTest(3)
    @DisplayName("같은 갱신 READY를 세 Worker가 실행하면 POST 1회, 진 쪽은 이긴 쪽이 PG 호출 중일 때 GET 없이 IN_PROGRESS")
    void sameReadyManyWorkers() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        PORTONE.defaultPaid(FEE, T);
        CountDownLatch postArrived = PORTONE.holdPosts();
        CountDownLatch start = new CountDownLatch(1);
        Callable<MembershipChargeOutcome> execute = () -> {
            awaitLatch(start);
            return executor.execute(member.getId(), attemptId);
        };

        // when
        MembershipChargeOutcome winner;
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            CompletionService<MembershipChargeOutcome> done = new ExecutorCompletionService<>(pool);
            done.submit(execute);
            done.submit(execute);
            done.submit(execute);
            start.countDown();
            assertThat(postArrived.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(take(done)).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
            assertThat(take(done)).isEqualTo(MembershipChargeOutcome.IN_PROGRESS);
            assertThat(PORTONE.totalGetCount()).isZero();
            PORTONE.releasePosts();
            winner = take(done);
        } finally {
            PORTONE.releasePosts();
            finish(pool);
        }

        // then
        assertThat(winner).isEqualTo(MembershipChargeOutcome.EXTENDED);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
        assertThat(PORTONE.totalGetCount()).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("해지 예약과 갱신 접수가 동시에 오면 회원 잠금 순서대로 둘 중 하나의 결과만 남고 교착이 없다")
    void cancelReservationVersusAccept() throws Exception {
        // given
        Long membershipId = activeMembership();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> accept = () -> {
            awaitLatch(start);
            return renewalProcessor.accept(member.getId(), membershipId);
        };
        Callable<Object> reserve = () -> {
            awaitLatch(start);
            return cancelReservation.reserve(member.getId(), membershipId);
        };

        // when
        List<Object> results = runConcurrently(start, List.of(accept, reserve));

        // then
        MembershipRenewalDecision decision = (MembershipRenewalDecision) results.get(0);
        assertThat(membership(membershipId).getCancelReservedAt()).isNotNull();
        if (decision.kind() == Kind.SKIPPED) {
            // 예약이 먼저 — 회차를 만들지 않았다
            assertThat(renewalPaymentIds(membershipId)).isEmpty();
        } else {
            // 접수가 먼저 — 접수된 READY는 그대로 남고(해지 예약이 폐기하지 않는다) 실행은 하지 않았다
            assertThat(decision.kind()).isEqualTo(Kind.CHARGE_READY);
            assertThat(attempt(decision.attemptId()).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        }
        assertThat(PORTONE.totalPostCount()).isZero();
    }

    @Test
    @DisplayName("결과 반영이 회원 잠금을 기다리는 사이 시계가 E를 넘으면 잠금 뒤 시각으로 판정해 연장하지 않는다")
    void freshClockAfterLockControlsDeadline() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusHours(1));
        clock.fixAt(E.minusSeconds(1));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Callable<Object> holdMember = () -> {
            transactionTemplate.executeWithoutResult(s -> {
                memberService.lockActiveMember(member.getId());
                locked.countDown();
                try {
                    awaitLatch(release);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return null;
        };
        Callable<Object> reconcile = () -> executor.reconcile(member.getId(), attemptId);

        // when
        MembershipChargeOutcome outcome;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> holder = pool.submit(holdMember);
            assertThat(locked.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            Future<Object> waiting = pool.submit(reconcile);
            awaitLockWait();
            clock.fixAt(E);
            release.countDown();
            holder.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            outcome = (MembershipChargeOutcome) waiting.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            release.countDown();
            finish(pool);
        }

        // then
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.RENEWAL_PERIOD_ENDED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("갱신 회차의 시도들은 PK 단일 조건으로 한 행씩, ID 오름차순으로 잠근다")
    void locksAttemptsByPrimaryKeyInAscendingOrder() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        clock.fixAt(T.plusHours(24));
        renewalFacade.runOnce();
        Long paymentId = onlyRenewalPayment(membershipId).getId();
        List<Long> attemptIds = attemptsOf(paymentId).stream().map(MembershipPaymentAttempt::getId).toList();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previousSql = SQL_LOGGER.getLevel();
        Level previousBind = BIND_LOGGER.getLevel();
        SQL_LOGGER.setLevel(Level.DEBUG);
        BIND_LOGGER.setLevel(Level.TRACE);
        SQL_LOGGER.addAppender(appender);
        BIND_LOGGER.addAppender(appender);
        List<String> lockingSql = new ArrayList<>();
        List<Long> lockedIds = new ArrayList<>();
        try {
            // when
            clock.fixAt(T.plusHours(25));
            renewalProcessor.settleExisting(member.getId(), membershipId, paymentId);

            // then
            String thread = Thread.currentThread().getName();
            boolean afterAttemptLock = false;
            for (ILoggingEvent event : appender.list) {
                if (!thread.equals(event.getThreadName())) {
                    continue;
                }
                String message = event.getFormattedMessage().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                if (event.getLoggerName().equals(SQL_LOGGER.getName())) {
                    afterAttemptLock = message.contains("from membership_payment_attempt")
                        && message.contains(" for update");
                    if (afterAttemptLock) {
                        lockingSql.add(message);
                    }
                } else if (afterAttemptLock) {
                    Matcher bound = BOUND_VALUE.matcher(message);
                    if (bound.find()) {
                        lockedIds.add(Long.valueOf(bound.group(1)));
                    }
                }
            }
        } finally {
            SQL_LOGGER.detachAppender(appender);
            BIND_LOGGER.detachAppender(appender);
            SQL_LOGGER.setLevel(previousSql);
            BIND_LOGGER.setLevel(previousBind);
        }
        assertThat(lockingSql).hasSize(attemptIds.size());
        assertThat(lockingSql).allMatch(sql -> PRIMARY_KEY_CONDITION.matcher(whereClauseOf(sql)).matches());
        assertThat(lockedIds).containsExactlyElementsOf(attemptIds.stream().sorted().toList());
    }

    // ---------- 보조 ----------

    // 다른 트랜잭션이 회원 행 잠금을 기다리기 시작할 때까지. 고정 sleep이 아니라 InnoDB의 잠금 대기 목록을 직접 확인한다.
    // 앱 계정은 performance_schema를 읽을 권한이 없어서, 같은 컨테이너의 관리자 계정으로 별도 연결을 열어 조회한다
    private void awaitLockWait() throws SQLException {
        HikariDataSource pool = jdbc.getDataSource().unwrap(HikariDataSource.class);
        try (Connection admin = DriverManager.getConnection(pool.getJdbcUrl(), "root", pool.getPassword());
             Statement statement = admin.createStatement()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
            while (System.nanoTime() < deadline) {
                try (ResultSet waiting = statement.executeQuery(
                    "SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
                    if (waiting.next() && waiting.getLong(1) > 0) {
                        return;
                    }
                }
                Thread.onSpinWait();
            }
        }
        throw new AssertionError("결과 반영 트랜잭션이 회원 잠금을 기다리지 않았습니다");
    }

    private static String whereClauseOf(String sql) {
        int where = sql.indexOf(" where ");
        if (where < 0) {
            return "";
        }
        String tail = sql.substring(where + " where ".length());
        int lock = tail.indexOf(" for update");
        return (lock < 0 ? tail : tail.substring(0, lock)).trim();
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
