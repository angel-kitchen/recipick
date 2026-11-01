package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.facade.MembershipChargeExecutor;
import com.recipick.domain.payment.facade.MembershipSubscriptionFacade;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor.SubscriptionReceipt;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipRequestHash;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

// 호출 경계와 정보 보호. 실제 Spring 빈(프록시)을 쓴다.
// ③ C-11·실행기는 바깥 트랜잭션 안에서 부르면 접수 행·PG 호출 전에 거절된다(Propagation.NEVER).
// ⑤ 재사용하는 결제 단건 조회(GET)가 성공 응답을 읽지 못해도(날짜 자리에 민감정보 모양의 문자열) 하위 클라이언트부터 실행기까지
//   어떤 로그 메시지·예외 출력에도 그 원문이 남지 않는다
class MembershipChargeGuardIntegrationTest extends MembershipChargeFlowSupport {

    private static final Logger ROOT_LOGGER = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    // 날짜로 읽힐 수 없는, 카드번호·이메일 모양의 값. 실제 값이 아니다
    private static final String SENSITIVE = "4111-1111-1111-1111 leak@example.test";

    @Autowired MembershipSubscriptionFacade facade;
    @Autowired MembershipSubscriptionProcessor processor;
    @Autowired MembershipChargeExecutor executor;
    @Autowired MembershipBillingService billing;
    @Autowired MembershipPaymentAttemptRepository attempts;

    private Member member;
    private MembershipConsent consent;
    private String key;

    @BeforeEach
    void setUp() {
        member = saveMember();
        saveActiveKey(member.getId());
        consent = new MembershipConsent(FEE, "v1", LocalDateTime.now(clock).minusDays(1));
        key = "guard-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("바깥 트랜잭션 안의 C-11은 접수 행을 만들기 전에 거절된다 — 이용권·회차·PG 호출 0")
    void subscribeRejectsOuterTransaction() {
        Throwable thrown = catchThrowable(() -> transactionTemplate.execute(s ->
            facade.subscribe(member.getId(), consent, key)));

        assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(billing.findMembershipIds(member.getId())).isEmpty();
        assertThat(PORTONE.totalRequests()).isZero();
    }

    @Test
    @DisplayName("바깥 트랜잭션 안의 execute·reconcile은 선점·PG 호출 전에 거절된다 — 시도는 READY 그대로")
    void executorRejectsOuterTransaction() {
        SubscriptionReceipt receipt = accept();

        Throwable execute = catchThrowable(() -> transactionTemplate.execute(s ->
            executor.execute(member.getId(), receipt.attemptId())));
        Throwable reconcile = catchThrowable(() -> transactionTemplate.execute(s ->
            executor.reconcile(member.getId(), receipt.attemptId())));

        assertThat(execute).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(reconcile).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.READY);
        assertThat(PORTONE.totalRequests()).isZero();
    }

    @Test
    @DisplayName("재조회 성공 응답의 날짜를 읽지 못하면 결과 불명(UNKNOWN)으로 두고, 클라이언트·실행기 어느 로그에도 응답 원문이 없다")
    void unreadableGetResponseLeavesNoRawValueInLogs() {
        SubscriptionReceipt receipt = accept();
        String paymentId = attempts.findById(receipt.attemptId()).orElseThrow().getPortonePaymentId();
        PORTONE.billingKeyPaymentPaid(paymentId, LocalDateTime.now(clock).withNano(0));
        PORTONE.getPaymentWithRawPaidAt(paymentId, FEE, SENSITIVE);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ROOT_LOGGER.addAppender(appender);

        MembershipChargeOutcome outcome;
        try {
            outcome = executor.execute(member.getId(), receipt.attemptId());
        } finally {
            ROOT_LOGGER.detachAppender(appender);
        }

        assertThat(outcome).isEqualTo(MembershipChargeOutcome.STILL_UNKNOWN);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(1);
        assertThat(attempts.findById(receipt.attemptId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentAttemptStatus.UNKNOWN);
        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(message -> message.contains("PortOne 결제 조회 응답 처리 실패"));
        assertThat(messages).noneMatch(message -> message.contains("4111-1111") || message.contains("leak@"));
        assertThat(appender.list).noneMatch(event -> throwableContains(event.getThrowableProxy(), "4111-1111")
            || throwableContains(event.getThrowableProxy(), "leak@"));
    }

    // ---------- 보조 ----------

    private SubscriptionReceipt accept() {
        return transactionTemplate.execute(s -> processor.accept(member.getId(), consent, member.getId() + ":" + key,
            MembershipRequestHash.of(consent), LocalDateTime.now(clock)));
    }

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
}
