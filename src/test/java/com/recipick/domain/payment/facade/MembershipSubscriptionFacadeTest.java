package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// C-11 입력 검증. 접수·청구 전에 끝나는 검사라 협력 객체 없이(null) 만든다 — Mockito 없음
class MembershipSubscriptionFacadeTest {

    private static final MembershipConsent CONSENT = new MembershipConsent(3_900L, "v1",
        LocalDateTime.of(2026, 11, 6, 10, 0));

    private final MembershipSubscriptionFacade facade = new MembershipSubscriptionFacade(null, null,
        Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), ZoneId.of("Asia/Seoul")));

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("멱등 키가 없으면 COMMON-007이다")
    void missingKey(String key) {
        assertThat(codeOf(catchThrowable(() -> facade.subscribe(7L, CONSENT, key))))
            .isEqualTo(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        assertThat(codeOf(catchThrowable(() -> facade.subscribe(7L, CONSENT, null))))
            .isEqualTo(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName("65자 이상이거나 공백이 섞인 키는 COMMON-001이다")
    void invalidKey() {
        assertThat(codeOf(catchThrowable(() -> facade.subscribe(7L, CONSENT, "a".repeat(65)))))
            .isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThat(codeOf(catchThrowable(() -> facade.subscribe(7L, CONSENT, "key with space"))))
            .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("동의 정보가 없으면 MEMBERSHIP-004다")
    void missingConsent() {
        assertThat(codeOf(catchThrowable(() -> facade.subscribe(7L, null, "sub-1"))))
            .isEqualTo(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
    }

    private static ErrorCode codeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }
}
