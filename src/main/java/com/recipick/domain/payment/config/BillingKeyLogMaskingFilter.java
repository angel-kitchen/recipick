package com.recipick.domain.payment.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Marker;

// 빌링키·토큰 원문이 로그에 남는 경로를 막는다.
// MySQL은 UNIQUE 위반 메시지에 중복 값을 그대로 넣는다("Duplicate entry '<값>' for key '<테이블>.<제약>'").
// 판정은 메시지 전체가 아니라 "for key" 뒤의 제약 식별자를 꺼내 아래 두 제약과 정확히 비교한다 — 중복 값 쪽에 같은 글자가
// 들어 있어도(예: 회원 이메일이 uk_billing_key_로 시작) 다른 제약의 충돌은 막지 않는다.
// 1) Hibernate SqlExceptionHelper가 예외 변환 전에 남기는 메시지 줄: 그 로거에 한해 메시지로 판정해 DENY
// 2) 예외 객체를 함께 넘기는 줄(GlobalExceptionHandler 등): 로거와 관계없이 cause 체인에 두 제약 위반이 있으면 DENY하고,
//    대신 원문 없는 진단 한 줄(로거·예외 클래스·제약 이름)을 남긴다. 다른 SQL 오류와 오류 코드·SQLState 줄은 그대로 남는다
public final class BillingKeyLogMaskingFilter extends TurboFilter {

    static final String HIBERNATE_SQL_EXCEPTION_LOGGER = "org.hibernate.engine.jdbc.spi.SqlExceptionHelper";
    static final String MASKING_LOGGER = "com.recipick.domain.payment.config.BillingKeyLogMasking";
    static final Set<String> SENSITIVE_CONSTRAINTS = Set.of(
        "uk_billing_key_portone_billing_key",
        "uk_billing_key_issue_request_issue_id");

    // MySQL 1062 메시지의 제약 식별자. 마지막 "for key '…'"를 쓴다(값 안에 같은 문구가 있어도 끝의 것이 제약이다)
    private static final Pattern DUPLICATE_KEY = Pattern.compile("^Duplicate entry .* for key '([^']+)'\\s*$",
        Pattern.DOTALL);

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
                              Throwable t) {
        if (logger == null || MASKING_LOGGER.equals(logger.getName())) {
            return FilterReply.NEUTRAL;
        }
        if (HIBERNATE_SQL_EXCEPTION_LOGGER.equals(logger.getName()) && sensitiveConstraintOf(format).isPresent()) {
            return FilterReply.DENY;
        }
        Optional<String> constraint = sensitiveConstraintOf(t);
        if (constraint.isPresent()) {
            logger.getLoggerContext().getLogger(MASKING_LOGGER).warn(
                "빌링키 원문이 포함된 로그를 차단함: logger={}, level={}, exception={}, constraint={}",
                logger.getName(), level, t.getClass().getName(), constraint.get());
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }

    // MySQL 메시지면 제약 식별자를 꺼내 "테이블." 접두어·따옴표를 떼고 소문자로 맞춘 뒤 두 제약과 비교한다
    static Optional<String> sensitiveConstraintOf(String message) {
        if (message == null) {
            return Optional.empty();
        }
        Matcher matcher = DUPLICATE_KEY.matcher(message.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String identifier = matcher.group(1).replace("`", "").toLowerCase(Locale.ROOT);
        String constraint = identifier.substring(identifier.lastIndexOf('.') + 1);
        return SENSITIVE_CONSTRAINTS.contains(constraint) ? Optional.of(constraint) : Optional.empty();
    }

    // cause 체인과 suppressed까지 본다. 순환 참조는 깊이로 끊는다
    static Optional<String> sensitiveConstraintOf(Throwable throwable) {
        return sensitiveConstraintOf(throwable, 0);
    }

    private static Optional<String> sensitiveConstraintOf(Throwable throwable, int depth) {
        if (throwable == null || depth > 20) {
            return Optional.empty();
        }
        Optional<String> own = sensitiveConstraintOf(throwable.getMessage());
        if (own.isPresent()) {
            return own;
        }
        for (Throwable suppressed : throwable.getSuppressed()) {
            Optional<String> found = sensitiveConstraintOf(suppressed, depth + 1);
            if (found.isPresent()) {
                return found;
            }
        }
        return throwable.getCause() == throwable
            ? Optional.empty()
            : sensitiveConstraintOf(throwable.getCause(), depth + 1);
    }
}
