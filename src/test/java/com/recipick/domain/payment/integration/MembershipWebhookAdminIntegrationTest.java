package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.facade.MembershipRefundReconciliationFacade;
import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 웹훅 Inbox의 멤버십 분기와 운영 조회·재조회 API. 웹훅은 수신 Facade가 저장하는 것과 같은 RECEIVED 행을 만든 뒤 Inbox 처리
// (claim → 판정 → 결과 기록)를 그대로 돌린다. 서명 검증·수신은 기존 웹훅 테스트가 다룬다
class MembershipWebhookAdminIntegrationTest extends MembershipRenewalFlowSupport {

    @Autowired PaymentReconciliationFacade reconciliation;
    @Autowired WebhookEventRepository webhookEvents;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired AdminRepository admins;

    private static final Duration WAIT = Duration.ofSeconds(15);

    private final List<Long> eventIds = new ArrayList<>();
    private Long superAdminId;

    @BeforeEach
    void setUpAdmin() {
        superAdminId = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).name("결제 운영")
            .passwordHash("hash").role(AdminRole.SUPER_ADMIN).active(true).build()).getId();
    }

    @AfterEach
    void cleanUpWebhookAndAdmin() {
        eventIds.forEach(webhookEvents::deleteById);
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", superAdminId);
        admins.deleteById(superAdminId);
    }

    // ---------- 웹훅 ----------

    @Test
    @DisplayName("멤버십 결제 PAID 웹훅은 같은 결제 ID 재조회로 한 번만 연장하고, 같은 결제의 웹훅이 또 와도 변경 없이 처리 완료")
    void paidWebhookExtendsOnce() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusMinutes(5));

        // when
        WebhookEvent first = received("Transaction.Paid", paymentId);
        WebhookEvent second = received("Transaction.Paid", paymentId);
        WebhookProcessOutcome firstOutcome = reconciliation.processWebhook(first.getId());
        WebhookProcessOutcome secondOutcome = reconciliation.processWebhook(second.getId());

        // then
        assertThat(firstOutcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(secondOutcome).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(PORTONE.postCount(paymentId)).isZero();
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("시도가 없는 멤버십 결제 ID 웹훅은 PG 조회 없이 운영 확인으로 보존한다(무시하지 않는다)")
    void unknownMembershipPaymentIdIsPreservedForReview() {
        // given
        String paymentId = "mpay_" + UUID.randomUUID().toString().replace("-", "");
        WebhookEvent event = received("Transaction.Paid", paymentId);

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        assertThat(PORTONE.getCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("환불 요청이 없는 시도의 취소 계열 웹훅은 외부 취소라 자동 귀속하지 않고 운영 확인으로 남기며 PG 조회·이용권 변경이 없다")
    void cancelEventsWithoutRefundAreExternalCancellations() {
        // given
        Long membershipId = activeMembership();
        PORTONE.defaultPaid(FEE, T);
        renewalFacade.runOnce();
        String paymentId = attemptsOf(onlyRenewalPayment(membershipId).getId()).get(0).getPortonePaymentId();
        int getsBefore = PORTONE.getCount(paymentId);

        // when
        List<WebhookProcessOutcome> outcomes = new ArrayList<>();
        for (String type : List.of("Transaction.Cancelled", "Transaction.PartialCancelled",
            "Transaction.CancelPending")) {
            WebhookEvent event = received(type, paymentId);
            outcomes.add(reconciliation.processWebhook(event.getId()));
            assertThat(webhookEvents.findById(event.getId()).orElseThrow().getFailureReason())
                .contains(MembershipRefundReconciliationFacade.REASON_EXTERNAL_CANCELLATION_UNEXPLAINED);
        }

        // then
        assertThat(outcomes).containsOnly(WebhookProcessOutcome.MANUAL_REVIEW);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("신선한 DISPATCHING에 PG가 진행 중이라고 하면 웹훅은 재시도로 남고 시도 상태는 그대로다")
    void webhookOnFreshDispatchingRetries() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentInStatus(paymentId, "PENDING", FEE);
        WebhookEvent event = received("Transaction.Paid", paymentId);

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);
    }

    @Test
    @DisplayName("실패로 확정한 시도의 Paid 웹훅은 PG 조회 장애면 재시도로 남고, 다음 조회가 PAID면 환불 요청 1건으로 끝난다 — 추가 POST 없음")
    void failedAttemptPaidWebhookRetriesThenRefunds() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = failedRenewalAttempt(membershipId);
        String paymentId = attempt(attemptId).getPortonePaymentId();
        PORTONE.getPaymentServerError(paymentId);
        WebhookEvent event = received("Transaction.Paid", paymentId);

        // when
        WebhookProcessOutcome first = reconciliation.processWebhook(event.getId());
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        WebhookProcessOutcome second = reconciliation.processWebhook(event.getId());

        // then
        assertThat(first).isEqualTo(WebhookProcessOutcome.RETRY_PENDING);
        assertThat(second).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.LATE_PAID_AFTER_FAILED);
        assertThat(systemRefundAmounts(attemptId)).containsExactly(FEE);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(T);
        assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("SYSTEM 환불 행이 수납액과 모순이면 웹훅은 강한 차단 기록을 남기고 운영 확인으로 끝난다")
    void contradictoryRefundRowSendsWebhookToManualReview() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = failedRenewalAttempt(membershipId);
        String paymentId = attempt(attemptId).getPortonePaymentId();
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        reconciliation.processWebhook(received("Transaction.Paid", paymentId).getId());
        jdbc.update("UPDATE membership_refund SET amount = 3800 WHERE membership_payment_attempt_id = ?", attemptId);
        WebhookEvent event = received("Transaction.Paid", paymentId);

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.MANUAL_REVIEW);
        WebhookEvent recorded = webhookEvents.findById(event.getId()).orElseThrow();
        assertThat(recorded.getStatus()).isEqualTo(WebhookEventStatus.MANUAL_REVIEW);
        assertThat(recorded.getFailureReason()).isEqualTo(MembershipChargeOutcome.RECONCILE_HALTED.name());
        MembershipPaymentAttempt blocked = attempt(attemptId);
        assertThat(blocked.isSettlementBlocked()).isTrue();
        assertThat(blocked.getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_refund WHERE membership_payment_attempt_id = ?",
            Long.class, attemptId)).isEqualTo(1L);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실행기가 PG 응답을 기다리는 신선한 DISPATCHING에는 복구가 GET하지 않고, 웹훅이 먼저 반영해도 연장 1회·POST 1회다")
    void webhookRecoveryAndExecutorOnFreshDispatching() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = attempt(attemptId).getPortonePaymentId();
        PORTONE.defaultPaid(FEE, T);
        CountDownLatch postArrived = PORTONE.holdPosts();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        MembershipChargeOutcome executed;
        WebhookProcessOutcome webhook;
        try {
            Future<MembershipChargeOutcome> executing = pool.submit(() -> executor.execute(member.getId(), attemptId));
            assertThat(postArrived.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.DISPATCHING);

            // when
            recovery.recoverOnce();
            int getsAfterRecovery = PORTONE.getCount(paymentId);
            webhook = reconciliation.processWebhook(received("Transaction.Paid", paymentId).getId());
            PORTONE.releasePosts();
            executed = executing.get(WAIT.toSeconds(), TimeUnit.SECONDS);

            // then
            assertThat(getsAfterRecovery).as("신선한 DISPATCHING은 복구가 조회하지 않는다").isZero();
        } finally {
            PORTONE.releasePosts();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
        assertThat(webhook).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(executed).isEqualTo(MembershipChargeOutcome.ALREADY_APPLIED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(PORTONE.postCount(paymentId)).isEqualTo(1);
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("오래된 DISPATCHING에 웹훅과 복구가 동시에 결과를 반영해도 연장 1회·POST 0회다")
    void webhookAndRecoveryRaceOnStaleDispatching() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusMinutes(30));
        WebhookEvent event = received("Transaction.Paid", paymentId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        WebhookProcessOutcome webhook;
        int recovered;
        try {
            Future<WebhookProcessOutcome> webhookRun = pool.submit(() -> {
                awaitStart(start);
                return reconciliation.processWebhook(event.getId());
            });
            Future<Integer> recoveryRun = pool.submit(() -> {
                awaitStart(start);
                return recovery.recoverOnce();
            });

            // when
            start.countDown();
            webhook = webhookRun.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            recovered = recoveryRun.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }

        // then
        assertThat(webhook).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(recovered).isGreaterThanOrEqualTo(1);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(2);
        assertThat(PORTONE.postCount(paymentId)).isZero();
        assertThat(refundCountOfMember()).isZero();
    }

    @Test
    @DisplayName("업무 반영은 커밋됐는데 Inbox PROCESSED 기록만 실패하면 PROCESSING으로 남고, 처리 임대 만료 뒤 재실행은 추가 연장·환불 없이 PROCESSED가 된다")
    void inboxRecordFailureIsRecoveredWithoutDoubleApply() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.plusMinutes(1));
        clock.fixAt(T.plusMinutes(5));
        WebhookEvent event = received("Transaction.Paid", paymentId);
        jdbc.execute("ALTER TABLE webhook_event ADD CONSTRAINT ck_test_record_failure CHECK (webhook_id <> '"
            + event.getWebhookId() + "' OR status <> 'PROCESSED')");
        Throwable thrown;
        try {
            // when
            thrown = catchThrowable(() -> reconciliation.processWebhook(event.getId()));
        } finally {
            jdbc.execute("ALTER TABLE webhook_event DROP CHECK ck_test_record_failure");
        }

        // then
        assertThat(thrown).isNotNull();
        assertThat(webhookEvents.findById(event.getId()).orElseThrow().getStatus())
            .isEqualTo(WebhookEventStatus.PROCESSING);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);

        // when — 처리 임대 만료 뒤 재실행
        clock.fixAt(T.plusMinutes(5).plus(PaymentReconciliationFacade.PROCESSING_TIMEOUT).plusSeconds(1));
        WebhookProcessOutcome again = reconciliation.processWebhook(event.getId());

        // then
        assertThat(again).isEqualTo(WebhookProcessOutcome.PROCESSED);
        assertThat(webhookEvents.findById(event.getId()).orElseThrow().getStatus())
            .isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.PAID);
        assertThat(refundCountOfMember()).isZero();
        assertThat(PORTONE.postCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("상품 결제 ID 형식의 웹훅은 기존 경로로 간다(멤버십 분기에 들어가지 않는다)")
    void productPaymentIdStillRoutesToProductPath() {
        // given
        WebhookEvent event = received("Transaction.Paid", "pay_" + UUID.randomUUID().toString().replace("-", ""));

        // when
        WebhookProcessOutcome outcome = reconciliation.processWebhook(event.getId());

        // then
        assertThat(outcome).isEqualTo(WebhookProcessOutcome.IGNORED);
    }

    // ---------- 운영 API ----------

    @Test
    @DisplayName("차단 시도의 운영자 재조회는 감사 기록 뒤 GET 1회로 PG 관측값을 돌려주고 반영하지 않는다(200·RECONCILE_HALTED)")
    void recheckAuditsAndDoesNotForceBlocked() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = attempt(attemptId).getPortonePaymentId();
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", T.minusMinutes(1));
        reconciliation.processWebhook(received("Transaction.Paid", paymentId).getId());
        int getsBefore = PORTONE.getCount(paymentId);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/membership-payment-attempts/"
                + attemptId + "/recheck").header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
            .andReturn().getResponse();

        // then
        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body).contains("\"outcome\":\"RECONCILE_HALTED\"").contains("\"pgVerdictKind\":\"PAID\"")
            .contains("\"pgPaidAmount\":3900").contains("\"blockedReason\":\"UNDISPATCHED_PAID\"");
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsBefore + 1);
        assertThat(auditActions()).containsExactly("MEMBERSHIP_PAYMENT_RECHECK");
        assertThat(attempt(attemptId).getStatus()).isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.UNDISPATCHED_PAID);
        assertThat(body).doesNotContain(cardA.getPortoneBillingKey()).doesNotContain("billingKey");
    }

    @Test
    @DisplayName("목록·상세는 감사 기록을 남기고 빌링키·고객 정보를 담지 않는다")
    void listAndDetailAreAuditedAndExposeNoSecrets() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();

        // when
        MockHttpServletResponse list = mockMvc.perform(get("/api/admin/membership-payment-attempts?membershipId="
                + membershipId).header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
            .andReturn().getResponse();
        MockHttpServletResponse detail = mockMvc.perform(get("/api/admin/membership-payment-attempts/" + attemptId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()))
            .andReturn().getResponse();

        // then
        assertThat(list.getStatus()).isEqualTo(200);
        assertThat(detail.getStatus()).isEqualTo(200);
        assertThat(list.getContentAsString()).contains("\"attemptId\":" + attemptId);
        assertThat(auditActions()).containsExactlyInAnyOrder("MEMBERSHIP_PAYMENT_LIST_VIEW",
            "MEMBERSHIP_PAYMENT_DETAIL_VIEW");
        for (String body : List.of(list.getContentAsString(), detail.getContentAsString())) {
            assertThat(body).doesNotContain(cardA.getPortoneBillingKey()).doesNotContain(member.getEmail())
                .doesNotContain(member.getPhone());
        }
    }

    @Test
    @DisplayName("회원 토큰은 운영 재조회를 부를 수 없고 PG 조회도 일어나지 않는다")
    void memberTokenCannotCall() throws Exception {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String memberToken = tokens.createTokens(new AuthPrincipal(SubjectType.MEMBER, member.getId(), null, null))
            .accessToken();

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/membership-payment-attempts/"
                + attemptId + "/recheck").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
            .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(PORTONE.totalGetCount()).isZero();
        assertThat(auditActions()).isEmpty();
    }

    // ---------- 보조 ----------

    // 발송돼 실패로 확정한 갱신 시도(POST 1회). 늦은 수납 시각(T + 1분)이 미래가 되지 않게 시계를 그 뒤로 둔다
    private Long failedRenewalAttempt(Long membershipId) {
        PORTONE.defaultFailed(FEE);
        renewalFacade.runOnce();
        PORTONE.clearDefault();
        MembershipPaymentAttempt failed = attemptsOf(onlyRenewalPayment(membershipId).getId()).get(0);
        assertThat(failed.getStatus()).isEqualTo(MembershipPaymentAttemptStatus.FAILED);
        clock.fixAt(T.plusMinutes(10));
        return failed.getId();
    }

    private static void awaitStart(CountDownLatch start) throws InterruptedException {
        if (!start.await(WAIT.toSeconds(), TimeUnit.SECONDS)) {
            throw new IllegalStateException("출발 신호를 받지 못함");
        }
    }

    // 수신 Facade가 저장하는 것과 같은 RECEIVED 행
    private WebhookEvent received(String type, String portonePaymentId) {
        WebhookEvent saved = webhookEvents.saveAndFlush(WebhookEvent.received("wh_" + UUID.randomUUID(), type,
            portonePaymentId, "{}"));
        eventIds.add(saved.getId());
        return saved;
    }

    private String superAdminToken() {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, superAdminId, "SUPER_ADMIN", null))
            .accessToken();
    }

    private List<String> auditActions() {
        return jdbc.queryForList("SELECT action FROM admin_audit_log WHERE admin_id = ?", String.class, superAdminId);
    }
}
