package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.facade.ProductPaymentSettlementChecker;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import com.recipick.global.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

// 탈퇴 정산의 결제 판정이 REPEATABLE READ 스냅숏이 아니라 잠금 읽기(최신 커밋)로 이뤄지는지, 그 잠금이 다른 흐름과 교착하지 않는지 본다.
// 결제 확정·늦은 승인 환불·외부 취소·웹훅은 PAYMENT만 잠그고 MEMBER는 잠그지 않으므로 회원 잠금만으로는 직렬화되지 않는다 —
// 탈퇴 TX가 첫 일반 조회로 스냅숏을 만든 뒤 커밋된 환불·웹훅을 일반 조회는 보지 못하고 잠금 읽기는 본다.
// 탈퇴 TX는 회원 Processor를 통하지 않고 같은 순서(일반 조회 → Checker)를 테스트가 직접 밟아 멈출 자리를 만든다.
// 실행 SQL은 Hibernate의 org.hibernate.SQL 로거를 테스트 중에만 DEBUG로 받아 탈퇴 스레드의 문장만 골라 본다
class ProductPaymentSettlementConcurrencyTest extends PaymentFlowIntegrationSupport {

    private static final String WEBHOOK_INDEX = "idx_webhook_event_portone_payment_id_status";
    private static final Duration HOLD_LOCK = Duration.ofMillis(300);
    private static final Logger SQL_LOGGER = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");

    @Autowired
    ProductPaymentSettlementChecker checker;

    @Autowired
    OrderService orderService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    PaymentReconciliationFacade paymentReconciliationFacade;

    private ListAppender<ILoggingEvent> sqlAppender;
    private Level previousSqlLevel;

    @BeforeEach
    void captureSql() {
        sqlAppender = new ListAppender<>();
        sqlAppender.start();
        previousSqlLevel = SQL_LOGGER.getLevel();
        SQL_LOGGER.setLevel(Level.DEBUG);
        SQL_LOGGER.addAppender(sqlAppender);
    }

    // 인덱스 확인용으로 깐 다른 결제의 웹훅은 부모의 정리 범위(이 테스트의 결제)에 없어 직접 지운다
    @AfterEach
    void stopCaptureAndCleanSeeds() {
        SQL_LOGGER.detachAppender(sqlAppender);
        SQL_LOGGER.setLevel(previousSqlLevel);
        jdbcTemplate.update("DELETE FROM webhook_event WHERE webhook_id LIKE 'wh_seed_%'");
    }

    // ---------- T-10 ----------

    @Test
    @DisplayName("탈퇴 TX가 스냅숏을 만든 뒤 커밋된 SYSTEM 환불 REQUESTED를 잠금 읽기로 잡아 PAYMENT-012로 막는다 — 같은 TX의 일반 조회는 여전히 보지 못한다")
    void catchesSystemRefundCommittedAfterSnapshot() throws Exception {
        // given — 결제 대기 만료로 FAILED가 된 결제. 늦은 승인이 확인되면 SYSTEM 환불이 생기는 자리다
        Flow flow = pendingPayment();
        failPayment(flow);
        CountDownLatch snapshotTaken = new CountDownLatch(1);
        CountDownLatch refundCommitted = new CountDownLatch(1);
        AtomicReference<String> withdrawalThread = new AtomicReference<>();

        Callable<Object> withdrawal = () -> transactionTemplate.execute(status -> {
            withdrawalThread.set(Thread.currentThread().getName());
            // 회원 탈퇴 TX의 첫 일반 조회(진행 중 주문 확인)와 같은 자리. REPEATABLE READ 스냅숏이 여기서 만들어진다
            orderService.existsInProgressOrder(flow.memberId());
            snapshotTaken.countDown();
            await(refundCommitted);
            // v1처럼 일반 조회로 봤다면 "환불 없음"이다 — 스냅숏이 환불 커밋 전이다
            assertThat(refundRepository.findAllByPaymentIdIn(List.of(flow.paymentId()))).isEmpty();
            Throwable thrown = catchThrowable(() -> checker.requireSettled(flow.memberId(), 0L));
            status.setRollbackOnly();
            return thrown;
        });
        Callable<Object> lateApproval = () -> {
            await(snapshotTaken);
            transactionTemplate.executeWithoutResult(status -> paymentService.requestSystemRefund(
                paymentService.getByIdForUpdate(flow.paymentId()), AMOUNT, "늦은 승인 자동 반환"));
            refundCommitted.countDown();
            return "REFUND_COMMITTED";
        };

        // when
        List<Object> results = runConcurrently(List.of(withdrawal, lateApproval));

        // then
        assertThat(results.get(1)).isEqualTo("REFUND_COMMITTED");
        assertThat(results.get(0)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_UNSETTLED_FOR_WITHDRAWAL));
        List<String> sqls = sqlOf(withdrawalThread.get());
        assertLockingSql(sqls, "payment", "for update");
        assertLockingSql(sqls, "refund", "for share");
        assertPaymentLookupUsesPrimaryKey(flow.paymentId());
    }

    @ParameterizedTest
    @EnumSource(value = WebhookEventStatus.class, names = {"RECEIVED", "MANUAL_REVIEW"})
    @DisplayName("탈퇴 TX가 스냅숏을 만든 뒤 그 결제에 새 웹훅이 저장(·처리)돼도 잠금 읽기로 잡아 PAYMENT-012로 막고, 조회는 새 복합 인덱스를 탄다")
    void catchesWebhookInsertedAfterSnapshot(WebhookEventStatus committedStatus) throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        String webhookId = "wh_after_snapshot_" + flow.orderId() + "_" + committedStatus;
        CountDownLatch snapshotTaken = new CountDownLatch(1);
        CountDownLatch webhookCommitted = new CountDownLatch(1);
        AtomicReference<String> withdrawalThread = new AtomicReference<>();

        Callable<Object> withdrawal = () -> transactionTemplate.execute(status -> {
            withdrawalThread.set(Thread.currentThread().getName());
            orderService.existsInProgressOrder(flow.memberId());
            snapshotTaken.countDown();
            await(webhookCommitted);
            // 일반 조회는 스냅숏 뒤에 저장된 웹훅을 보지 못한다
            assertThat(eventsOf(webhookId)).isEmpty();
            Throwable thrown = catchThrowable(() -> checker.requireSettled(flow.memberId(), 0L));
            status.setRollbackOnly();
            return thrown;
        });
        Callable<Object> lateWebhook = () -> {
            await(snapshotTaken);
            saveWebhookInSeparateTransactions(flow, webhookId, committedStatus);
            webhookCommitted.countDown();
            return "WEBHOOK_COMMITTED";
        };

        // when
        List<Object> results = runConcurrently(List.of(withdrawal, lateWebhook));

        // then
        assertThat(results.get(1)).isEqualTo("WEBHOOK_COMMITTED");
        assertThat(results.get(0)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_UNSETTLED_FOR_WITHDRAWAL));
        List<String> sqls = sqlOf(withdrawalThread.get());
        assertLockingSql(sqls, "payment", "for update");
        assertLockingSql(sqls, "webhook_event", "for share");
        assertWebhookLookupUsesIndex(flow.portonePaymentId());
    }

    // ---------- T-11 ----------

    @Test
    @DisplayName("탈퇴 TX가 결제를 잠근 동안 같은 결제의 늦은 승인 환불 TX는 기다렸다가 탈퇴 커밋 뒤 정상 진행한다 — 후속 정합화는 막히지 않는다")
    void lateApprovalWaitsForWithdrawalThenProceeds() throws Exception {
        // given — FAILED 결제에 환불이 없어 탈퇴 검사는 통과하고, 커밋 전까지 PAYMENT X 잠금을 쥔다
        Flow flow = pendingPayment();
        failPayment(flow);
        CountDownLatch withdrawalLocked = new CountDownLatch(1);
        CountDownLatch lateApprovalStarted = new CountDownLatch(1);
        AtomicLong withdrawalHeldUntil = new AtomicLong();
        AtomicLong lateApprovalLockedAt = new AtomicLong();

        Callable<Object> withdrawal = () -> {
            transactionTemplate.executeWithoutResult(status -> {
                checker.requireSettled(flow.memberId(), 0L);
                withdrawalLocked.countDown();
                await(lateApprovalStarted);
                sleep(HOLD_LOCK);
                withdrawalHeldUntil.set(System.nanoTime());
            });
            return "WITHDRAWAL_COMMITTED";
        };
        Callable<Object> lateApproval = () -> {
            await(withdrawalLocked);
            lateApprovalStarted.countDown();
            return transactionTemplate.execute(status -> {
                Payment payment = paymentService.getByIdForUpdate(flow.paymentId());
                lateApprovalLockedAt.set(System.nanoTime());
                return paymentService.requestSystemRefund(payment, AMOUNT, "늦은 승인 자동 반환").getStatus();
            });
        };

        // when
        List<Object> results = runConcurrently(List.of(withdrawal, lateApproval));

        // then — 환불 TX는 탈퇴 TX가 잠금을 놓은 뒤에야 결제를 잠갔고, 그 뒤 정상으로 환불을 만들었다
        assertThat(results.get(0)).isEqualTo("WITHDRAWAL_COMMITTED");
        assertThat(results.get(1)).isEqualTo(RefundStatus.REQUESTED);
        assertThat(lateApprovalLockedAt.get()).isGreaterThan(withdrawalHeldUntil.get());
        List<Refund> refunds = refundsOf(flow);
        assertThat(refunds).singleElement().satisfies(refund -> {
            assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        });
    }

    @Test
    @DisplayName("탈퇴 TX가 잠금을 쥔 동안 다른 회원 결제의 환불 요청·웹훅 저장은 교착 없이 끝난다")
    void otherMemberRefundAndWebhookDoNotDeadlock() throws Exception {
        // given
        Flow mine = paidPaymentWithStock();
        Flow other = paidPaymentWithStock();
        String otherWebhookId = "wh_other_member_" + other.orderId();
        CountDownLatch withdrawalLocked = new CountDownLatch(1);
        CountDownLatch otherStarted = new CountDownLatch(1);

        Callable<Object> withdrawal = () -> {
            transactionTemplate.executeWithoutResult(status -> {
                checker.requireSettled(mine.memberId(), 0L);
                withdrawalLocked.countDown();
                await(otherStarted);
                sleep(HOLD_LOCK);
            });
            return "WITHDRAWAL_COMMITTED";
        };
        Callable<Object> otherMember = () -> {
            await(withdrawalLocked);
            otherStarted.countDown();
            transactionTemplate.executeWithoutResult(status -> {
                Payment payment = paymentService.getByIdForUpdate(other.paymentId());
                payment.requestCancel();
                paymentService.requestMemberRefund(payment, CANCEL_REASON, "wd-other-" + other.orderId());
                webhookEventRepository.save(
                    WebhookEvent.received(otherWebhookId, "Transaction.Cancelled", other.portonePaymentId(), "{}"));
            });
            return "OTHER_COMMITTED";
        };

        // when
        List<Object> results = runConcurrently(List.of(withdrawal, otherMember));

        // then
        assertThat(results).containsExactly("WITHDRAWAL_COMMITTED", "OTHER_COMMITTED");
        assertThat(paymentOf(other).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(refundsOf(other)).singleElement()
            .satisfies(refund -> assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED));
        assertThat(eventsOf(otherWebhookId)).singleElement()
            .satisfies(event -> assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED));
        assertThat(paymentOf(mine).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(refundsOf(mine)).isEmpty();
    }

    @Test
    @DisplayName("탈퇴 TX와 같은 결제의 웹훅 수신·Worker 처리가 겹쳐도 교착 없이 둘 다 끝난다 — Worker는 한 TX에서 웹훅과 결제를 함께 잠그지 않는다")
    void webhookWorkerOnSamePaymentDoesNotDeadlock() throws Exception {
        // given — 이미 끝난 웹훅 한 건이 있어 탈퇴 검사는 통과하고, 그 결제의 웹훅 인덱스 구간에 S 잠금이 남는다
        Flow flow = paidPaymentWithStock();
        saveWebhookInSeparateTransactions(flow, "wh_finished_" + flow.orderId(), WebhookEventStatus.PROCESSED);
        given(portOnePaymentClient.getPayment(flow.portonePaymentId())).willReturn(portOnePaid(flow));
        String webhookId = "wh_during_withdrawal_" + flow.orderId();
        CountDownLatch withdrawalLocked = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);

        Callable<Object> withdrawal = () -> {
            transactionTemplate.executeWithoutResult(status -> {
                checker.requireSettled(flow.memberId(), 0L);
                withdrawalLocked.countDown();
                await(workerStarted);
                sleep(HOLD_LOCK);
            });
            return "WITHDRAWAL_COMMITTED";
        };
        Callable<Object> worker = () -> {
            await(withdrawalLocked);
            workerStarted.countDown();
            // 수신(INSERT)은 잠긴 인덱스 구간에 걸리면 탈퇴 커밋까지 잠깐 기다릴 뿐 실패하지 않는다
            postWebhook(webhookId, "Transaction.Paid", flow.portonePaymentId(), null).andExpect(status().isOk());
            WebhookEvent event = eventsOf(webhookId).get(0);
            removeTask(PaymentDelayTask.webhookProcess(event.getId()));
            return paymentReconciliationFacade.processWebhook(event.getId());
        };

        // when
        List<Object> results = runConcurrently(List.of(withdrawal, worker));

        // then
        assertThat(results.get(0)).isEqualTo("WITHDRAWAL_COMMITTED");
        assertThat(results.get(1)).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(eventsOf(webhookId)).singleElement()
            .satisfies(event -> assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED));
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // ---------- 픽스처 ----------

    // 결제 대기 만료로 FAILED. 늦은 승인이 확인되면 SYSTEM 환불이 생기는 자리다
    private void failPayment(Flow flow) {
        transactionTemplate.executeWithoutResult(
            status -> paymentService.getByIdForUpdate(flow.paymentId()).fail(PaymentFailureReason.EXPIRED, null));
    }

    // Worker와 같은 모양으로 웹훅을 만든다: 수신 저장 TX → (끝난 상태면) claim·결과 기록 TX. 한 TX에서 웹훅과 결제를 함께 잠그지 않는다
    private void saveWebhookInSeparateTransactions(Flow flow, String webhookId, WebhookEventStatus target) {
        WebhookEvent saved = webhookEventRepository.save(
            WebhookEvent.received(webhookId, "Transaction.Paid", flow.portonePaymentId(), "{}"));
        if (target == WebhookEventStatus.RECEIVED) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        transactionTemplate.executeWithoutResult(status -> {
            WebhookEvent event = webhookEventRepository.findByIdForUpdate(saved.getId()).orElseThrow();
            event.claim(now, PaymentReconciliationFacade.PROCESSING_TIMEOUT);
            switch (target) {
                case PROCESSED -> event.markProcessed(now);
                case MANUAL_REVIEW -> event.markManualReview("부분 취소", now);
                default -> throw new IllegalArgumentException("이 테스트가 만들지 않는 웹훅 상태: " + target);
            }
        });
    }

    // ---------- SQL·인덱스 확인 ----------

    // 탈퇴 스레드가 실행한 SQL. 공백을 하나로 모으고 소문자로 맞춰 비교한다
    private List<String> sqlOf(String threadName) {
        return sqlAppender.list.stream()
            .filter(event -> threadName.equals(event.getThreadName()))
            .map(event -> event.getFormattedMessage().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))
            .toList();
    }

    private static void assertLockingSql(List<String> sqls, String table, String lockClause) {
        assertThat(sqls)
            .as("%s 조회 SQL에 %s가 붙어야 한다", table, lockClause)
            .anySatisfy(sql -> assertThat(sql).contains("from " + table + " ").contains(lockClause));
    }

    // 결제 잠금 조회는 PK IN 조건이라 PRIMARY를 탄다 — 빈 구간 없이 레코드 잠금만 걸린다
    private void assertPaymentLookupUsesPrimaryKey(Long paymentId) {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
            "EXPLAIN SELECT id FROM payment WHERE id IN (?) ORDER BY id FOR UPDATE", paymentId);
        assertThat(plan).singleElement().satisfies(row -> assertThat(row.get("key")).isEqualTo("PRIMARY"));
    }

    // 리포지토리 쿼리와 같은 모양의 SQL을 EXPLAIN해 새 복합 인덱스로 그 결제의 행만 읽는지 본다.
    // 행이 몇 개뿐이면 옵티마이저가 전체 스캔을 고를 수 있어 다른 결제의 웹훅을 넉넉히 깔아 둔다
    private void assertWebhookLookupUsesIndex(String portonePaymentId) {
        webhookEventRepository.saveAll(IntStream.range(0, 60)
            .mapToObj(i -> WebhookEvent.received(
                "wh_seed_" + UUID.randomUUID(), "Transaction.Paid", "pay_seed_" + i, "{}"))
            .toList());
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
            "EXPLAIN SELECT id FROM webhook_event WHERE portone_payment_id IN (?) AND status IN (?, ?, ?, ?) FOR SHARE",
            portonePaymentId, "RECEIVED", "PROCESSING", "RETRY_PENDING", "MANUAL_REVIEW");
        assertThat(plan).singleElement().satisfies(row -> {
            assertThat(String.valueOf(row.get("possible_keys"))).contains(WEBHOOK_INDEX);
            assertThat(row.get("key")).isEqualTo(WEBHOOK_INDEX);
        });
    }

    // ---------- 대기 ----------

    // 상대 트랜잭션이 15초 안에 오지 않으면 멈추지 않고 실패로 끝낸다
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("15초 안에 상대 트랜잭션이 도착하지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("상대 트랜잭션을 기다리다 중단됐습니다.", e);
        }
    }

    // 잠금을 쥔 채 잠깐 머물러 상대가 실제로 기다리게 만든다
    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("잠금 유지 중 중단됐습니다.", e);
        }
    }
}
