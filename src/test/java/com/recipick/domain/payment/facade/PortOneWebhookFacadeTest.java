package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.WebhookEventService;
import com.recipick.domain.payment.service.WebhookReceiveOutcome;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.PortOneProperties;
import com.recipick.infra.portone.PortOneWebhookParser;
import com.recipick.infra.portone.PortOneWebhookVerification;
import com.recipick.infra.portone.PortOneWebhookVerifier;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PortOneWebhookFacadeTest {

    private static final String WEBHOOK_ID = "wh_1";
    private static final String TIMESTAMP = "1761393600";
    private static final String SIGNATURE = "v1,abc";
    private static final String STORE_ID = "store-1";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 21, 0);
    private static final String PAID_BODY = body("Transaction.Paid", STORE_ID, "\"pay_1\"");

    @Mock
    private PortOneWebhookVerifier portOneWebhookVerifier;

    @Mock
    private WebhookEventService webhookEventService;

    @Captor
    private ArgumentCaptor<WebhookEvent> eventCaptor;

    private PortOneWebhookFacade facade;

    @BeforeEach
    void setUp() {
        facade = newFacade(STORE_ID);
    }

    @Test
    @DisplayName("시크릿이 설정되지 않았으면 500 PAYMENT-011이고 저장하지 않는다 — PortOne 재전송을 이용해 설정 뒤 다시 받는다")
    void notConfigured() {
        // given
        given(portOneWebhookVerifier.verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, PAID_BODY))
            .willReturn(PortOneWebhookVerification.NOT_CONFIGURED);

        // when
        Throwable thrown = catchThrowable(() -> facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, PAID_BODY));

        // then
        assertErrorCode(thrown, PaymentErrorCode.WEBHOOK_NOT_CONFIGURED);
        verifyNoInteractions(webhookEventService);
    }

    @ParameterizedTest
    @EnumSource(value = PortOneWebhookVerification.class,
        names = {"MISSING_HEADERS", "INVALID_TIMESTAMP", "TIMESTAMP_OUT_OF_RANGE", "INVALID_SIGNATURE"})
    @DisplayName("서명 검증에 실패하면 401 AUTH-010이고 DB에 아무것도 남기지 않는다")
    void invalidSignature(PortOneWebhookVerification verification) {
        // given
        given(portOneWebhookVerifier.verify(WEBHOOK_ID, TIMESTAMP, SIGNATURE, PAID_BODY)).willReturn(verification);

        // when
        Throwable thrown = catchThrowable(() -> facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, PAID_BODY));

        // then
        assertErrorCode(thrown, AuthErrorCode.INVALID_WEBHOOK_SIGNATURE);
        verifyNoInteractions(webhookEventService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "null", "[1, 2]"})
    @DisplayName("서명은 맞지만 본문을 읽을 수 없으면 400 COMMON-002고 저장하지 않는다")
    void unreadableBody(String body) {
        // given
        givenVerified();

        // when
        Throwable thrown = catchThrowable(() -> facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, body));

        // then
        assertErrorCode(thrown, CommonErrorCode.INVALID_REQUEST_FORMAT);
        verifyNoInteractions(webhookEventService);
    }

    @Test
    @DisplayName("처리 대상 type이고 storeId가 맞으면 원문 그대로 RECEIVED로 저장한다")
    void received() {
        // given
        givenValid();

        // when
        WebhookReceiveOutcome outcome = facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, PAID_BODY);

        // then
        assertThat(outcome).isEqualTo(WebhookReceiveOutcome.RECEIVED);
        WebhookEvent saved = savedEvent();
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(saved.getWebhookId()).isEqualTo(WEBHOOK_ID);
        assertThat(saved.getType()).isEqualTo("Transaction.Paid");
        assertThat(saved.getPortonePaymentId()).isEqualTo("pay_1");
        assertThat(saved.getPayload()).isEqualTo(PAID_BODY);
        assertThat(saved.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("처리하지 않는 type은 IGNORED로 저장한다 — 저장해야 재전송이 UNIQUE에 걸려 200으로 끝난다")
    void ignoredUnknownType() {
        // given
        givenValid();
        String body = body("BillingKey.Issued", STORE_ID, "null");

        // when
        WebhookReceiveOutcome outcome = facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, body);

        // then
        assertThat(outcome).isEqualTo(WebhookReceiveOutcome.IGNORED);
        WebhookEvent saved = savedEvent();
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(saved.getType()).isEqualTo("BillingKey.Issued");
        assertThat(saved.getFailureReason()).contains("BillingKey.Issued");
        assertThat(saved.getFinishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다른 storeId의 웹훅은 IGNORED로 저장한다")
    void ignoredOtherStore() {
        // given
        givenValid();
        String body = body("Transaction.Paid", "store-other", "\"pay_1\"");

        // when
        WebhookReceiveOutcome outcome = facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, body);

        // then
        assertThat(outcome).isEqualTo(WebhookReceiveOutcome.IGNORED);
        assertThat(savedEvent().getFailureReason()).contains("store-other");
    }

    @Test
    @DisplayName("storeId 설정이 비어 있으면 처리 대상 웹훅은 500 PAYMENT-011로 거절하고 저장하지 않는다 — fail-closed")
    void storeIdNotConfigured() {
        // given
        facade = newFacade("");
        givenVerified();

        // when
        Throwable thrown = catchThrowable(() -> facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, PAID_BODY));

        // then
        assertErrorCode(thrown, PaymentErrorCode.WEBHOOK_NOT_CONFIGURED);
        verifyNoInteractions(webhookEventService);
    }

    @Test
    @DisplayName("storeId 설정이 비어 있어도 처리하지 않는 type은 IGNORED로 저장한다 — 공식 지침대로 모르는 이벤트는 200")
    void storeIdNotConfiguredIgnoresUnknownType() {
        // given
        facade = newFacade("");
        givenValid();
        String body = body("BillingKey.Issued", STORE_ID, "null");

        // when
        WebhookReceiveOutcome outcome = facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, body);

        // then
        assertThat(outcome).isEqualTo(WebhookReceiveOutcome.IGNORED);
    }

    @Test
    @DisplayName("처리 대상 type인데 paymentId가 없으면 IGNORED로 저장한다 — 어느 결제인지 알 수 없다")
    void ignoredWithoutPaymentId() {
        // given
        givenValid();
        String body = body("Transaction.Paid", STORE_ID, "null");

        // when
        WebhookReceiveOutcome outcome = facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, body);

        // then
        assertThat(outcome).isEqualTo(WebhookReceiveOutcome.IGNORED);
        assertThat(savedEvent().getPortonePaymentId()).isNull();
    }

    @Test
    @DisplayName("같은 webhook_id가 UNIQUE에 걸리면 조회 없이 DUPLICATE로 끝낸다 — 재전송은 200")
    void duplicate() {
        // given
        givenVerified();
        given(webhookEventService.save(any()))
            .willThrow(duplicate("webhook_event." + WebhookEvent.WEBHOOK_ID_UNIQUE_CONSTRAINT));

        // when
        WebhookReceiveOutcome outcome = facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, PAID_BODY);

        // then
        assertThat(outcome).isEqualTo(WebhookReceiveOutcome.DUPLICATE);
    }

    @Test
    @DisplayName("다른 제약 위반은 재전송으로 오인하지 않고 그대로 던진다 — 500으로 PortOne이 다시 보낸다")
    void otherConstraintViolation() {
        // given
        givenVerified();
        DataIntegrityViolationException other = duplicate("uk_something_else");
        given(webhookEventService.save(any())).willThrow(other);

        // when
        Throwable thrown = catchThrowable(() -> facade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, PAID_BODY));

        // then
        assertThat(thrown).isSameAs(other);
    }

    private void givenVerified() {
        given(portOneWebhookVerifier.verify(anyString(), anyString(), anyString(), anyString()))
            .willReturn(PortOneWebhookVerification.VALID);
    }

    // 저장은 받은 엔티티를 그대로 돌려준다. ID는 없지만 Facade는 상태만 본다
    private void givenValid() {
        givenVerified();
        given(webhookEventService.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    private WebhookEvent savedEvent() {
        verify(webhookEventService).save(eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private PortOneWebhookFacade newFacade(String storeId) {
        PortOneProperties properties = new PortOneProperties(
            "test-secret", "https://api.portone.io", Duration.ofSeconds(1), Duration.ofSeconds(1), storeId, "channel");
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        return new PortOneWebhookFacade(
            portOneWebhookVerifier, new PortOneWebhookParser(), properties, webhookEventService, clock);
    }

    // Spring이 Hibernate 예외를 감싼 모양 그대로 만든다. MySQL은 제약 이름 앞에 테이블명을 붙인다
    private static DataIntegrityViolationException duplicate(String constraintName) {
        return new DataIntegrityViolationException("duplicate",
            new ConstraintViolationException("duplicate", new SQLException("Duplicate entry"), constraintName));
    }

    private static String body(String type, String storeId, String paymentIdJson) {
        return "{\"type\":\"" + type + "\",\"timestamp\":\"2026-10-25T12:00:00Z\","
            + "\"data\":{\"storeId\":\"" + storeId + "\",\"paymentId\":" + paymentIdJson
            + ",\"transactionId\":\"tx_1\"}}";
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
