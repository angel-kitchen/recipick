package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.entity.BillingKeyIssueRequest;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.BillingKeyRegistrationProcessor;
import com.recipick.domain.payment.repository.BillingKeyIssueRequestRepository;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.service.BillingKeyRegistration;
import com.recipick.domain.payment.service.BillingKeyRegistrationOutcome;
import com.recipick.domain.payment.service.BillingKeyService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

// BK-05. 같은 회원·같은 토큰·두 회원의 동시 등록을 실제 MySQL에서 본다. 각 경우를 3회 반복한다.
// 마지막 경우는 "없는 ACTIVE 행을 잠그지 않는다"의 증거다 — 한 회원이 회원 잠금과 ACTIVE 행 ID 조회까지 마친 채 멈춰 있어도
// 다른 회원의 첫 등록이 끝난다. v2의 빈 구간 FOR UPDATE였다면 (member_id, status) 인덱스의 갭 잠금에 걸려 기다렸을 자리다
class BillingKeyRegistrationConcurrencyTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Original1!";
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 10, 31, 14, 0);
    private static final Logger ROOT_LOGGER = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    // 래치·결과·스레드 종료를 기다리는 한도
    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    BillingKeyRegistrationProcessor processor;

    @Autowired
    BillingKeyService billingKeyService;

    @Autowired
    MemberService memberService;

    @Autowired
    BillingKeyRepository billingKeys;

    @Autowired
    BillingKeyIssueRequestRepository issueRequests;

    @Autowired
    MemberRepository members;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    private final List<Long> memberIds = new ArrayList<>();
    // 작업 스레드가 끝났는지. 끝나지 않았으면 정리하지 않는다(테스트는 이미 실패로 표시됐다)
    private boolean workersTerminated = true;

    @AfterEach
    void cleanUp() {
        if (!workersTerminated) {
            return;
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM billing_key_issue_request WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM billing_key WHERE member_id = ?", memberId);
            members.deleteById(memberId);
        }
        memberIds.clear();
    }

    @RepeatedTest(3)
    @DisplayName("같은 회원이 카드 두 장을 동시에 등록하면 회원 잠금이 직렬화해 ACTIVE 하나·REPLACED 하나가 된다")
    void sameMemberTwoCards() throws Exception {
        // given
        Member member = saveMember();
        BillingKeyIssueRequest requestA = requested(member.getId());
        BillingKeyIssueRequest requestB = requested(member.getId());
        String keyA = newKey();
        String keyB = newKey();

        Callable<Object> cardA = () -> processor.register(member.getId(), requestA.getId(), keyA, ISSUED_AT);
        Callable<Object> cardB = () -> processor.register(member.getId(), requestB.getId(), keyB, ISSUED_AT);

        // when
        List<Object> results = runConcurrently(List.of(cardA, cardB));

        // then
        assertThat(results).extracting(result -> ((BillingKeyRegistration) result).outcome())
            .containsExactlyInAnyOrder(
                BillingKeyRegistrationOutcome.REGISTERED, BillingKeyRegistrationOutcome.REPLACED);
        assertThat(countOf(member.getId(), BillingKeyStatus.ACTIVE)).isEqualTo(1L);
        assertThat(countOf(member.getId(), BillingKeyStatus.REPLACED)).isEqualTo(1L);
    }

    @RepeatedTest(3)
    @DisplayName("같은 토큰으로 키 두 개를 동시에 보내면 하나만 등록되고 늦은 쪽은 BILLING-007이다")
    void sameTokenTwoKeys() throws Exception {
        // given
        Member member = saveMember();
        BillingKeyIssueRequest request = requested(member.getId());
        String key1 = newKey();
        String key2 = newKey();

        Callable<Object> withKey1 = () -> registerOrError(member.getId(), request.getId(), key1);
        Callable<Object> withKey2 = () -> registerOrError(member.getId(), request.getId(), key2);

        // when
        List<Object> results = runConcurrently(List.of(withKey1, withKey2));

        // then
        assertThat(results).containsExactlyInAnyOrder(
            BillingKeyRegistrationOutcome.REGISTERED, PaymentErrorCode.BILLING_KEY_ISSUE_REQUEST_INVALID);
        assertThat(countOf(member.getId(), BillingKeyStatus.ACTIVE)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE member_id = ?",
            Long.class, member.getId())).isEqualTo(1L);
    }

    @RepeatedTest(3)
    @DisplayName("두 회원이 같은 키를 동시에 등록하면 UNIQUE가 한 명만 통과시키고 다른 쪽은 BILLING-002다")
    void twoMembersSameKey() throws Exception {
        // given
        Member first = saveMember();
        Member second = saveMember();
        BillingKeyIssueRequest requestFirst = requested(first.getId());
        BillingKeyIssueRequest requestSecond = requested(second.getId());
        String key = newKey();

        Callable<Object> byFirst = () -> registerOrError(first.getId(), requestFirst.getId(), key);
        Callable<Object> bySecond = () -> registerOrError(second.getId(), requestSecond.getId(), key);

        // when
        List<Object> results = runConcurrently(List.of(byFirst, bySecond));

        // then
        assertThat(results).containsExactlyInAnyOrder(
            BillingKeyRegistrationOutcome.REGISTERED, PaymentErrorCode.BILLING_KEY_ALREADY_USED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE portone_billing_key = ?",
            Long.class, key)).isEqualTo(1L);
    }

    @RepeatedTest(3)
    @DisplayName("한 회원이 회원 잠금과 ACTIVE 행 조회를 마친 채 멈춰 있어도 다른 회원의 첫 등록은 기다리지 않고 끝난다")
    void firstCardsOfDifferentMembersDoNotWait() throws Exception {
        // given — ID가 이웃한 두 회원. 둘 다 아직 키가 없다
        Member paused = saveMember();
        Member other = saveMember();
        BillingKeyIssueRequest pausedRequest = requested(paused.getId());
        BillingKeyIssueRequest otherRequest = requested(other.getId());
        String pausedKey = newKey();
        String otherKey = newKey();
        CountDownLatch pausedAfterLookup = new CountDownLatch(1);
        CountDownLatch otherFinished = new CountDownLatch(1);

        // 등록 트랜잭션의 앞부분(회원 잠금 → ACTIVE 행 ID 조회)을 그대로 재현한 뒤 멈춘다
        Callable<Object> pausedRegistration = () -> transactionTemplate.execute(status -> {
            memberService.lockActiveMember(paused.getId());
            assertThat(billingKeys.findIdByMemberIdAndStatus(paused.getId(), BillingKeyStatus.ACTIVE)).isEmpty();
            pausedAfterLookup.countDown();
            await(otherFinished);
            return billingKeyService.register(paused.getId(), pausedRequest.getId(), pausedKey, ISSUED_AT);
        });
        Callable<Object> otherRegistration = () -> {
            await(pausedAfterLookup);
            try {
                return processor.register(other.getId(), otherRequest.getId(), otherKey, ISSUED_AT);
            } finally {
                otherFinished.countDown();
            }
        };

        // when
        List<Object> results = runConcurrently(List.of(pausedRegistration, otherRegistration));

        // then
        assertThat(results).extracting(result -> ((BillingKeyRegistration) result).outcome())
            .containsExactly(BillingKeyRegistrationOutcome.REGISTERED, BillingKeyRegistrationOutcome.REGISTERED);
        assertThat(countOf(paused.getId(), BillingKeyStatus.ACTIVE)).isEqualTo(1L);
        assertThat(countOf(other.getId(), BillingKeyStatus.ACTIVE)).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 키 사전 조회가 끝난 뒤 다른 회원이 그 키를 커밋하면 UNIQUE 충돌이 실제로 나고, BILLING-002가 되며 로그 어디에도 키 원문이 없다")
    void uniqueConflictLeavesNoKeyInLogs() throws Exception {
        // given — 두 회원, 같은 키. 늦은 쪽의 사전 조회가 먼저 끝나도록 순서를 고정한다(동시 출발만으로는 조회에서 걸러질 수 있다)
        Member late = saveMember();
        Member early = saveMember();
        BillingKeyIssueRequest lateRequest = requested(late.getId());
        BillingKeyIssueRequest earlyRequest = requested(early.getId());
        String key = newKey();
        CountDownLatch lateLookedUp = new CountDownLatch(1);
        CountDownLatch earlyCommitted = new CountDownLatch(1);
        ListAppender<ILoggingEvent> rootAppender = new ListAppender<>();
        rootAppender.start();
        ROOT_LOGGER.addAppender(rootAppender);

        // 늦은 회원: 회원 잠금 → 같은 키 조회(여기서 스냅숏이 만들어져 뒤에 커밋된 행을 보지 못한다) → 대기 → 등록 → INSERT가 UNIQUE에 걸린다
        Callable<Object> lateRegistration = () -> transactionTemplate.execute(status -> {
            memberService.lockActiveMember(late.getId());
            assertThat(billingKeys.findIdByPortoneBillingKey(key)).isEmpty();
            lateLookedUp.countDown();
            await(earlyCommitted);
            try {
                return billingKeyService.register(late.getId(), lateRequest.getId(), key, ISSUED_AT).outcome();
            } catch (BusinessException e) {
                status.setRollbackOnly();
                return e.getErrorCode();
            }
        });
        Callable<Object> earlyRegistration = () -> {
            await(lateLookedUp);
            try {
                return processor.register(early.getId(), earlyRequest.getId(), key, ISSUED_AT).outcome();
            } finally {
                earlyCommitted.countDown();
            }
        };

        // when
        List<Object> results;
        try {
            results = runConcurrently(List.of(lateRegistration, earlyRegistration));
        } finally {
            ROOT_LOGGER.detachAppender(rootAppender);
        }

        // then — 결과
        assertThat(results.get(0)).isEqualTo(PaymentErrorCode.BILLING_KEY_ALREADY_USED);
        assertThat(results.get(1)).isEqualTo(BillingKeyRegistrationOutcome.REGISTERED);
        assertThat(jdbc.queryForObject("SELECT member_id FROM billing_key WHERE portone_billing_key = ?",
            Long.class, key)).isEqualTo(early.getId());
        assertThat(countOf(late.getId(), BillingKeyStatus.ACTIVE)).isZero();

        // then — 로그. 키 원문은 0건, 오류 코드(Hibernate WARN)와 제약 이름(서비스 WARN)은 남는다
        List<String> messages = rootAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).noneMatch(message -> message.contains(key));
        assertThat(rootAppender.list).noneMatch(event -> throwableContains(event.getThrowableProxy(), key));
        assertThat(messages).anyMatch(message -> message.contains("SQL Error: 1062"));
        assertThat(messages).anyMatch(message -> message.contains("uk_billing_key_portone_billing_key"));
    }

    // ---------- 실행 ----------

    // 로그 이벤트의 예외를 cause·suppressed까지 따라가며 메시지에 문자열이 있는지 본다(출력되는 스택과 같은 범위)
    private static boolean throwableContains(IThrowableProxy proxy, String text) {
        if (proxy == null) {
            return false;
        }
        if (proxy.getMessage() != null && proxy.getMessage().contains(text)) {
            return true;
        }
        for (IThrowableProxy suppressed : proxy.getSuppressed()) {
            if (throwableContains(suppressed, text)) {
                return true;
            }
        }
        return throwableContains(proxy.getCause(), text);
    }

    private Object registerOrError(Long memberId, Long requestId, String key) {
        try {
            return processor.register(memberId, requestId, key, ISSUED_AT).outcome();
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
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

    // ---------- 픽스처·조회 ----------

    private Member saveMember() {
        String suffix = UUID.randomUUID().toString();
        Member saved = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
            .name("빌링키동시성").passwordHash(encoder.encode(PASSWORD)).build());
        memberIds.add(saved.getId());
        return saved;
    }

    private BillingKeyIssueRequest requested(Long memberId) {
        return issueRequests.saveAndFlush(
            BillingKeyIssueRequest.requested(memberId, "rcp-bk-" + UUID.randomUUID(), LocalDateTime.now(clock)));
    }

    private static String newKey() {
        return "billing-key-test-" + UUID.randomUUID();
    }

    private Long countOf(Long memberId, BillingKeyStatus status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE member_id = ? AND status = ?",
            Long.class, memberId, status.name());
    }
}
