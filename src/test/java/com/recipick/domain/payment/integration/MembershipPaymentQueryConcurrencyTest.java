package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

// MP-13·14. C-17이 일반 조회인데도 최신을 보는 전제(P1·P2)를 실제 MySQL 경합으로 본다.
// P1: 호출자 TX의 첫 DB 작업이 회원 잠금이다. P2: 이 회원의 회차를 만드는 TX도 회원 잠금을 먼저 잡는다.
// 둘이 지켜지면 두 TX는 직렬화되고, 판정은 직렬화 순서에 따른다. P1을 어기면(잠금 전에 일반 조회) 오래된 스냅숏으로
// 상대의 커밋을 놓친다 — 그 재현이 MP-14다. 회차 생성은 4단계 접수 TX1의 자리를 "회원 잠금 → INSERT → 커밋"으로 재현한다
class MembershipPaymentQueryConcurrencyTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Original1!";
    private static final AtomicLong MEMBERSHIP_SEQUENCE = new AtomicLong(9_820_100L);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 1);
    private static final Duration HOLD_BEFORE_CHECK = Duration.ofMillis(500);
    // 래치·결과·스레드 종료를 기다리는 한도
    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    MembershipPaymentQueryService queryService;

    @Autowired
    MembershipPaymentRepository payments;

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository members;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private Member member;
    private long membershipId;
    // 작업 스레드가 끝났는지. 끝나지 않았으면 정리하지 않는다(테스트는 이미 실패로 표시됐다)
    private boolean workersTerminated = true;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        member = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
            .name("미정산경합테스트").passwordHash(encoder.encode(PASSWORD)).build());
        membershipId = MEMBERSHIP_SEQUENCE.incrementAndGet();
    }

    @AfterEach
    void cleanUp() {
        if (!workersTerminated) {
            return;
        }
        jdbc.update("DELETE FROM membership_payment WHERE membership_id = ?", membershipId);
        members.deleteById(member.getId());
    }

    // ---------- MP-13 ----------

    @Test
    @DisplayName("탈퇴 TX가 회원 잠금을 쥐고 있는 동안 같은 회원의 회차 생성 TX는 잠금을 얻지 못하고, 탈퇴 판정은 false다")
    void withdrawalFirstSerializesCreation() throws Exception {
        // given
        CountDownLatch withdrawalLocked = new CountDownLatch(1);
        CountDownLatch creationAboutToLock = new CountDownLatch(1);
        AtomicBoolean withdrawalChecked = new AtomicBoolean();
        AtomicBoolean creationSawCheckDone = new AtomicBoolean();   // 생성 TX가 잠금을 얻은 순간 판정이 끝나 있었나

        Callable<Object> withdrawal = () -> transactionTemplate.execute(status -> {
            memberService.lockActiveMember(member.getId());
            withdrawalLocked.countDown();
            await(creationAboutToLock);
            // 생성 TX가 잠금을 요청한 뒤 잠시 기다린다 — 잠금이 막지 않는다면 이 사이에 생성이 끝나 판정이 true가 된다
            sleep(HOLD_BEFORE_CHECK);
            boolean result = queryService.existsUnresolved(List.of(membershipId));
            withdrawalChecked.set(true);
            return result;
        });
        Callable<Object> creation = () -> {
            await(withdrawalLocked);
            transactionTemplate.executeWithoutResult(status -> {
                // 잠금 요청 직전에 신호를 보낸다 — 스레드 시작·커넥션 확보 지연이 순서 판정에 끼지 않게
                creationAboutToLock.countDown();
                memberService.lockActiveMember(member.getId());
                creationSawCheckDone.set(withdrawalChecked.get());
                payments.save(pendingPayment());
            });
            return "CREATED";
        };

        // when
        List<Object> results = runConcurrently(List.of(withdrawal, creation));

        // then — 처리 시간이 아니라 순서와 결과를 본다. 생성 TX가 잠금을 얻은 때는 탈퇴 TX의 판정이 끝난 뒤였고
        // (잠금은 탈퇴 TX가 끝나야 풀린다), 탈퇴 판정은 생성 전 상태(false), 생성은 그 뒤에 반영됐다
        assertThat(results.get(0)).isEqualTo(false);
        assertThat(results.get(1)).isEqualTo("CREATED");
        assertThat(creationSawCheckDone.get()).isTrue();
        assertThat(existsUnresolvedInNewTransaction()).isTrue();
    }

    @Test
    @DisplayName("회차 생성 TX가 먼저 커밋되면 뒤에 회원을 잠근 탈퇴 TX는 그 회차를 보고 true다")
    void creationFirstIsVisibleToWithdrawal() throws Exception {
        // given
        CountDownLatch creationCommitted = new CountDownLatch(1);

        Callable<Object> creation = () -> {
            transactionTemplate.executeWithoutResult(status -> {
                memberService.lockActiveMember(member.getId());
                payments.save(pendingPayment());
            });
            creationCommitted.countDown();
            return "CREATED";
        };
        Callable<Object> withdrawal = () -> {
            await(creationCommitted);
            return transactionTemplate.execute(status -> {
                memberService.lockActiveMember(member.getId());
                return queryService.existsUnresolved(List.of(membershipId));
            });
        };

        // when
        List<Object> results = runConcurrently(List.of(creation, withdrawal));

        // then
        assertThat(results.get(1)).isEqualTo(true);
    }

    // ---------- MP-14 ----------

    @Test
    @DisplayName("회원 잠금 전에 일반 조회를 하면(P1 위반) 그 뒤 커밋된 회차를 잠근 뒤에도 보지 못한다 — 전제가 필요한 이유")
    void readBeforeLockMissesLaterCommit() throws Exception {
        // given
        CountDownLatch snapshotTaken = new CountDownLatch(1);
        CountDownLatch creationCommitted = new CountDownLatch(1);

        Callable<Object> withdrawal = () -> transactionTemplate.execute(status -> {
            // 잠금 전의 일반 조회 — REPEATABLE READ 스냅숏이 여기서 만들어진다
            boolean before = queryService.existsUnresolved(List.of(membershipId));
            snapshotTaken.countDown();
            await(creationCommitted);
            memberService.lockActiveMember(member.getId());
            boolean after = queryService.existsUnresolved(List.of(membershipId));
            return List.of(before, after);
        });
        Callable<Object> creation = () -> {
            await(snapshotTaken);
            transactionTemplate.executeWithoutResult(status -> {
                memberService.lockActiveMember(member.getId());
                payments.save(pendingPayment());
            });
            creationCommitted.countDown();
            return "CREATED";
        };

        // when
        List<Object> results = runConcurrently(List.of(withdrawal, creation));

        // then — 같은 TX 안에서는 잠근 뒤에도 false다. 새 TX는 true다
        assertThat(results.get(0)).isEqualTo(List.of(false, false));
        assertThat(existsUnresolvedInNewTransaction()).isTrue();
    }

    // ---------- 실행·픽스처 ----------

    private boolean existsUnresolvedInNewTransaction() {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            memberService.lockActiveMember(member.getId());
            return queryService.existsUnresolved(List.of(membershipId));
        }));
    }

    private MembershipPayment pendingPayment() {
        return MembershipPayment.builder()
            .membershipId(membershipId).amount(3_900L).status(MembershipPaymentStatus.PENDING)
            .billingDate(PERIOD_START).periodStartDate(PERIOD_START)
            .periodEndDate(PERIOD_START.plusMonths(1).minusDays(1)).build();
    }

    // 모든 작업을 같이 출발시키고 결과를 모은다. 끝나면(성공·실패 모두) 작업 스레드의 종료를 확인한다 —
    // 확인되면 정리로 넘어가고, 확인되지 않으면 workersTerminated를 false로 두고 실패시켜 @AfterEach가 아직 도는
    // 트랜잭션과 겹쳐 공유 DB의 픽스처를 지우지 않게 한다
    private List<Object> runConcurrently(List<? extends Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        Throwable failure = null;
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return task.call();
                }));
            }
            await(ready);
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<?> future : futures) {
                results.add(future.get(WAIT.toSeconds(), TimeUnit.SECONDS));
            }
            return results;
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            if (!shutdownAndWait(executor)) {
                workersTerminated = false;
                AssertionError notTerminated = new AssertionError(
                    "작업 스레드가 " + (WAIT.toSeconds() * 2) + "초 안에 끝나지 않았습니다. 픽스처 정리를 건너뜁니다.");
                if (failure != null) {
                    notTerminated.addSuppressed(failure);
                }
                throw notTerminated;
            }
        }
    }

    // 종료를 요청하고 기다린다. 첫 대기에서 안 끝나면 인터럽트로 한 번 더 요청하고 다시 기다린다.
    // 기다리는 중 이 스레드가 인터럽트되면 종료를 요청하고 인터럽트 상태를 되살린 뒤 "확인 못 함"으로 돌려준다
    private static boolean shutdownAndWait(ExecutorService executor) {
        executor.shutdown();
        try {
            if (executor.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS)) {
                return true;
            }
            executor.shutdownNow();
            return executor.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT.toSeconds(), TimeUnit.SECONDS)) {
                throw new IllegalStateException(WAIT.toSeconds() + "초 안에 상대 트랜잭션이 도착하지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("상대 트랜잭션을 기다리다 중단됐습니다.", e);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대기 중 중단됐습니다.", e);
        }
    }
}
