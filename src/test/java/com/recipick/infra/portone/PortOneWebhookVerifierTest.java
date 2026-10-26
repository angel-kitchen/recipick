package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// 기대 서명은 Standard Webhooks 공개 예제 값이다. 우리 코드로 계산한 값을 정답으로 쓰면 틀린 구현도 통과한다
class PortOneWebhookVerifierTest {

    // 공개 예제 키(실제 키 아님). 접두사를 따로 붙이는 건 저장소 비밀값 스캐너가 실제 시크릿으로 오인하지 않게 하려는 것
    private static final String SECRET_KEY = "MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    private static final String SECRET = "whsec_" + SECRET_KEY;
    private static final String WEBHOOK_ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
    private static final String TIMESTAMP = "1614265330";
    private static final long TIMESTAMP_SECONDS = 1614265330L;
    private static final String BODY = "{\"test\": 2432232314}";
    private static final String SIGNATURE = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";

    @Test
    @DisplayName("Standard Webhooks 예제 서명이면 VALID다")
    void verifyStandardWebhooksExample() {
        // when
        PortOneWebhookVerification result = verifier(SECRET).verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.VALID);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("시크릿에 whsec_ 접두사가 없어도 같은 키로 검증한다")
    void verifySecretWithoutPrefix() {
        // when
        PortOneWebhookVerification result = verifier(SECRET_KEY).verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.VALID);
    }

    @Test
    @DisplayName("원문 body가 한 글자라도 다르면 INVALID_SIGNATURE다")
    void rejectChangedBody() {
        // when
        PortOneWebhookVerification result =
            verifier(SECRET).verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, "{\"test\": 2432232315}");

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("webhook-id가 다르면 서명 대상이 달라져 INVALID_SIGNATURE다")
    void rejectChangedWebhookId() {
        // when
        PortOneWebhookVerification result = verifier(SECRET).verify("msg_other", TIMESTAMP, SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("공백으로 구분된 서명 여러 개 중 하나만 맞아도 VALID다 — 시크릿 교체 기간")
    void acceptAnyMatchingSignature() {
        // when
        PortOneWebhookVerification result =
            verifier(SECRET).verify(WEBHOOK_ID, TIMESTAMP, "v1,AAAAAAAA " + SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.VALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "v2,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=",
        "g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=",
        "v1,!!!not-base64!!!"
    })
    @DisplayName("v1이 아닌 버전·접두사 없는 값·Base64가 깨진 값은 서명으로 인정하지 않는다")
    void rejectMalformedSignature(String signature) {
        // when
        PortOneWebhookVerification result = verifier(SECRET).verify(WEBHOOK_ID, TIMESTAMP, signature, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.INVALID_SIGNATURE);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "NULL, 1614265330, 'v1,x'",
        "' ', 1614265330, 'v1,x'",
        "msg_1, NULL, 'v1,x'",
        "msg_1, '', 'v1,x'",
        "msg_1, 1614265330, NULL",
        "msg_1, 1614265330, ' '"
    }, nullValues = "NULL")
    @DisplayName("헤더 셋 중 하나라도 없거나 비면 MISSING_HEADERS다")
    void rejectMissingHeaders(String webhookId, String timestamp, String signature) {
        // when
        PortOneWebhookVerification result = verifier(SECRET).verify(webhookId, timestamp, signature, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.MISSING_HEADERS);
    }

    @Test
    @DisplayName("body가 null이면 MISSING_HEADERS다")
    void rejectNullBody() {
        // when
        PortOneWebhookVerification result = verifier(SECRET).verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, null);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.MISSING_HEADERS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1614265330.5", "99999999999999999999"})
    @DisplayName("timestamp가 long으로 읽히지 않으면 INVALID_TIMESTAMP다")
    void rejectInvalidTimestamp(String timestamp) {
        // when
        PortOneWebhookVerification result = verifier(SECRET).verify(WEBHOOK_ID, timestamp, SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.INVALID_TIMESTAMP);
    }

    @Test
    @DisplayName("long으로는 읽히지만 터무니없는 미래 값은 TIMESTAMP_OUT_OF_RANGE다 — 공식 verifier와 같은 분류")
    void rejectFarFutureTimestamp() {
        // when
        PortOneWebhookVerification result =
            verifier(SECRET).verify(WEBHOOK_ID, String.valueOf(Long.MAX_VALUE), SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.TIMESTAMP_OUT_OF_RANGE);
    }

    @ParameterizedTest
    @CsvSource({
        "300, VALID",
        "301, TIMESTAMP_OUT_OF_RANGE",
        "-300, VALID",
        "-301, TIMESTAMP_OUT_OF_RANGE"
    })
    @DisplayName("현재 시각과 5분까지 차이는 허용하고 넘으면 TIMESTAMP_OUT_OF_RANGE다 — 과거·미래 모두")
    void timestampTolerance(long nowOffsetSeconds, PortOneWebhookVerification expected) {
        // given — 서명은 그대로 두고 서버 시계만 옮긴다
        PortOneWebhookVerifier verifier = verifier(SECRET, TIMESTAMP_SECONDS + nowOffsetSeconds);

        // when
        PortOneWebhookVerification result = verifier.verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("현재 시각에 밀리초가 있어도 epoch 초 기준 정확히 5분 차이는 허용한다")
    void allowExactlyFiveMinutesWithSubSecondClock() {
        // given — 초 단위로 자르면 300초, 밀리초까지 보면 300.999초. 공식 verifier는 초 단위로 비교한다
        Clock clock = Clock.fixed(Instant.ofEpochSecond(TIMESTAMP_SECONDS + 300).plusMillis(999), ZoneOffset.UTC);
        PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(new PortOneWebhookProperties(SECRET), clock);

        // when
        PortOneWebhookVerification result = verifier.verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.VALID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("시크릿이 설정되지 않으면 앱은 뜨고 모든 웹훅이 NOT_CONFIGURED다")
    void notConfigured(String secret) {
        // when
        PortOneWebhookVerification result = verifier(secret).verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, BODY);

        // then
        assertThat(result).isEqualTo(PortOneWebhookVerification.NOT_CONFIGURED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"whsec_!!!", "whsec_"})
    @DisplayName("시크릿이 Base64가 아니거나 접두사만 있으면 기동 시점에 실패한다")
    void failFastOnInvalidSecret(String secret) {
        // when, then
        assertThatThrownBy(() -> verifier(secret))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageNotContaining(secret);
    }

    private static PortOneWebhookVerifier verifier(String secret) {
        return verifier(secret, TIMESTAMP_SECONDS);
    }

    private static PortOneWebhookVerifier verifier(String secret, long nowSeconds) {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(nowSeconds), ZoneOffset.UTC);
        return new PortOneWebhookVerifier(new PortOneWebhookProperties(secret), clock);
    }
}
