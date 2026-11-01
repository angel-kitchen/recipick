package com.recipick.domain.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// Logback 컨텍스트를 직접 만들어 필터 판정만 본다 — Spring·DB 없음. 실제 UNIQUE 충돌에서의 효과는
// BillingKeyRegistrationConcurrencyTest.uniqueConflictLeavesNoKeyInLogs가 본다
class BillingKeyLogMaskingFilterTest {

    private static final String KEY = "billing-key-test-0001";
    private static final String KEY_DUPLICATE =
        "Duplicate entry '" + KEY + "' for key 'billing_key.uk_billing_key_portone_billing_key'";
    private static final String TOKEN_DUPLICATE =
        "Duplicate entry 'rcp-bk-0f8a1c2e' for key 'billing_key_issue_request.uk_billing_key_issue_request_issue_id'";
    private static final String MEMBER_DUPLICATE = "Duplicate entry 'a@b.c' for key 'member.uk_member_email'";
    // 중복 값 쪽에 같은 글자가 들어 있는 회원 이메일 충돌 — 제약은 회원 것이다
    private static final String MEMBER_DUPLICATE_LOOKALIKE =
        "Duplicate entry 'uk_billing_key_user@example.test' for key 'member.uk_member_email'";
    private static final String GLOBAL_HANDLER_LOGGER = "com.recipick.global.exception.GlobalExceptionHandler";

    private final LoggerContext context = new LoggerContext();
    private final Logger hibernate = context.getLogger(BillingKeyLogMaskingFilter.HIBERNATE_SQL_EXCEPTION_LOGGER);
    private final Logger handler = context.getLogger(GLOBAL_HANDLER_LOGGER);
    private final BillingKeyLogMaskingFilter filter = new BillingKeyLogMaskingFilter();
    private final ListAppender<ILoggingEvent> maskingAppender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        maskingAppender.start();
        context.getLogger(BillingKeyLogMaskingFilter.MASKING_LOGGER).addAppender(maskingAppender);
    }

    // ---------- Hibernate 메시지 줄 ----------

    @Test
    @DisplayName("Hibernate SQL 예외 로거의 빌링키 UNIQUE 충돌 메시지는 막는다")
    void deniesBillingKeyDuplicate() {
        assertThat(decide(hibernate, KEY_DUPLICATE, null)).isEqualTo(FilterReply.DENY);
    }

    @Test
    @DisplayName("발급 요청 토큰의 UNIQUE 충돌 메시지도 막는다")
    void deniesIssueRequestDuplicate() {
        assertThat(decide(hibernate, TOKEN_DUPLICATE, null)).isEqualTo(FilterReply.DENY);
    }

    @Test
    @DisplayName("테이블 접두어·따옴표·대소문자가 달라도 제약 이름이 같으면 막는다")
    void normalizesConstraintIdentifier() {
        assertThat(decide(hibernate, "Duplicate entry 'x' for key 'uk_billing_key_portone_billing_key'", null))
            .isEqualTo(FilterReply.DENY);
        assertThat(decide(hibernate,
            "Duplicate entry 'x' for key '`billing_key`.`UK_BILLING_KEY_PORTONE_BILLING_KEY`'", null))
            .isEqualTo(FilterReply.DENY);
    }

    @Test
    @DisplayName("중복 값에 uk_billing_key_가 들어 있어도 제약이 회원 이메일이면 막지 않는다")
    void keepsLookalikeValueOfOtherConstraint() {
        assertThat(decide(hibernate, MEMBER_DUPLICATE_LOOKALIKE, null)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    @DisplayName("같은 로거라도 오류 코드·SQLState 줄과 다른 테이블의 충돌 메시지는 그대로 남긴다")
    void keepsDiagnostics() {
        assertThat(decide(hibernate, "SQL Error: 1062, SQLState: 23000", null)).isEqualTo(FilterReply.NEUTRAL);
        assertThat(decide(hibernate, MEMBER_DUPLICATE, null)).isEqualTo(FilterReply.NEUTRAL);
        assertThat(decide(hibernate, null, null)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    @DisplayName("Hibernate가 아닌 로거의 메시지 줄은 내용으로 판정하지 않는다 — 예외가 없으면 그대로 둔다")
    void ignoresMessagesOfOtherLoggers() {
        assertThat(decide(handler, KEY_DUPLICATE, null)).isEqualTo(FilterReply.NEUTRAL);
    }

    // ---------- 예외 객체를 함께 넘기는 줄 ----------

    @Test
    @DisplayName("GlobalExceptionHandler가 넘긴 예외의 안쪽 cause에 빌링키 충돌이 있으면 막고, 원문 없는 진단 한 줄을 남긴다")
    void deniesNestedCauseOnAnyLogger() {
        // given — Spring → Hibernate → JDBC로 감싸인 실제 모양
        Throwable nested = new DataIntegrityViolationException("could not execute statement",
            new RuntimeException("could not execute statement",
                new SQLIntegrityConstraintViolationException(KEY_DUPLICATE, "23000", 1062)));

        // when
        FilterReply reply = decide(handler, "Unhandled exception", nested);

        // then
        assertThat(reply).isEqualTo(FilterReply.DENY);
        assertThat(maskingAppender.list).hasSize(1);
        String diagnostic = maskingAppender.list.get(0).getFormattedMessage();
        assertThat(diagnostic).contains(GLOBAL_HANDLER_LOGGER)
            .contains(DataIntegrityViolationException.class.getName())
            .contains("uk_billing_key_portone_billing_key")
            .doesNotContain(KEY);
    }

    @Test
    @DisplayName("suppressed에 들어 있어도 막는다")
    void deniesSuppressed() {
        // given
        RuntimeException outer = new RuntimeException("rollback failed");
        outer.addSuppressed(new SQLException(KEY_DUPLICATE));

        // then
        assertThat(decide(handler, "x", outer)).isEqualTo(FilterReply.DENY);
    }

    @Test
    @DisplayName("다른 제약의 중첩 충돌과 일반 SQL 오류는 막지 않는다 — 진단 정보가 사라지면 안 된다")
    void keepsOtherNestedErrors() {
        assertThat(decide(handler, "Unhandled exception",
            new RuntimeException("x", new SQLException(MEMBER_DUPLICATE_LOOKALIKE)))).isEqualTo(FilterReply.NEUTRAL);
        assertThat(decide(handler, "Unhandled exception",
            new RuntimeException("x", new SQLException("Lock wait timeout exceeded")))).isEqualTo(FilterReply.NEUTRAL);
        assertThat(maskingAppender.list).isEmpty();
    }

    @Test
    @DisplayName("자기 자신을 cause로 가리키는 예외에도 끝난다")
    void survivesSelfCause() {
        // given
        Throwable self = new SelfCausedException();

        // then
        assertThat(decide(handler, "x", self)).isEqualTo(FilterReply.NEUTRAL);
    }

    private FilterReply decide(Logger logger, String message, Throwable throwable) {
        return filter.decide(null, logger, Level.ERROR, message, null, throwable);
    }

    private static final class SelfCausedException extends RuntimeException {
        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}
