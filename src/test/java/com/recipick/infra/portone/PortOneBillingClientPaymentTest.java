package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.recipick.infra.portone.dto.PortOneBillingPaymentOutcome;
import com.recipick.infra.portone.dto.PortOneBillingPaymentRequest;
import com.recipick.infra.portone.dto.PortOneBillingPaymentResult;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

// F-1. 빌링키 청구 호출의 분류(2단계 4-3)와 정보 보호를 본다. 결제 조회·취소 테스트와 같이 운영 설정을 거친 빌더에 Mock 서버를 붙인다.
// 어떤 응답에도 FAILED가 없고, detail은 허용 목록의 코드만이며, 로그·결과에 빌링키·고객 정보·pgMessage 원문이 없어야 한다
class PortOneBillingClientPaymentTest {

    private static final PortOneProperties PROPERTIES = new PortOneProperties(
        "test-secret",
        "https://api.portone.io",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        "test-store-id",
        "test-channel-key"
    );
    private static final String PAYMENT_ID = "mpay_0123456789abcdef0123456789abcdef";
    private static final String PAY_URL = "https://api.portone.io/payments/" + PAYMENT_ID + "/billing-key";
    private static final String BILLING_KEY = "billing-key-test-secret-0001";
    private static final String EMAIL = "member@example.test";
    private static final String PHONE = "01012345678";
    private static final String NAME = "홍길동";
    // PG 오류 본문의 자유 문장. 고객 정보 모양의 문자열이 들어 있어도 로그·결과에 그대로 옮겨지면 안 된다
    private static final String PG_MESSAGE = "card declined for " + EMAIL + " / " + PHONE;
    private static final Logger CLIENT_LOGGER = (Logger) LoggerFactory.getLogger(PortOneBillingClient.class);

    private MockRestServiceServer server;
    private PortOneBillingClient client;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = PortOneClientConfig.configure(RestClient.builder(), PROPERTIES);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneBillingClient(builder.build());
        logs = new ListAppender<>();
        logs.start();
        CLIENT_LOGGER.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        CLIENT_LOGGER.detachAppender(logs);
    }

    @Test
    @DisplayName("200에 pgTxId·paidAt이 있으면 PAID 후보다 — 인증 헤더·KG이니시스 필수 필드를 보내고 멱등 키 헤더는 쓰지 않는다")
    void paid() {
        // given
        server.expect(requestTo(PAY_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne test-secret"))
            .andExpect(headerDoesNotExist("Idempotency-Key"))
            .andExpect(content().json("""
                {
                  "billingKey": "%s",
                  "orderName": "레시픽 멤버십 월 이용료",
                  "customer": {"id": "77", "name": {"full": "%s"}, "email": "%s", "phoneNumber": "%s"},
                  "amount": {"total": 3900},
                  "currency": "KRW",
                  "productCount": 1
                }
                """.formatted(BILLING_KEY, NAME, EMAIL, PHONE), JsonCompareMode.STRICT))
            .andRespond(withSuccess("""
                {"payment": {"pgTxId": "pgtx_1", "paidAt": "2026-11-06T01:03:11Z", "extra": "ignored"}}
                """, MediaType.APPLICATION_JSON));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        server.verify();
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.PAID);
        assertThat(result.pgTxId()).isEqualTo("pgtx_1");
        assertThat(result.paidAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 10, 3, 11));
        assertNoSensitiveText(result);
    }

    @Test
    @DisplayName("200인데 pgTxId나 paidAt이 없으면 결제됐는지 모르므로 UNKNOWN(MISSING_FIELD)이다")
    void successWithoutRequiredFields() {
        // given
        server.expect(requestTo(PAY_URL))
            .andRespond(withSuccess("{\"payment\": {\"pgTxId\": \"pgtx_1\"}}", MediaType.APPLICATION_JSON));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.UNKNOWN);
        assertThat(result.detail()).isEqualTo(PortOneBillingPaymentResult.DETAIL_MISSING_FIELD);
    }

    @Test
    @DisplayName("409 ALREADY_PAID는 같은 결제 ID가 이미 결제된 것이라 ALREADY_PAID다")
    void alreadyPaid() {
        // given
        server.expect(requestTo(PAY_URL)).andRespond(error(HttpStatus.CONFLICT, "ALREADY_PAID"));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.ALREADY_PAID);
        assertThat(result.detail()).isEqualTo("ALREADY_PAID");
    }

    @ParameterizedTest
    @CsvSource({
        "NOT_FOUND, BILLING_KEY_NOT_FOUND",
        "CONFLICT, BILLING_KEY_ALREADY_DELETED"
    })
    @DisplayName("빌링키가 없거나 삭제됐으면 BILLING_KEY_UNUSABLE이다")
    void billingKeyUnusable(HttpStatus status, String type) {
        // given
        server.expect(requestTo(PAY_URL)).andRespond(error(status, type));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.BILLING_KEY_UNUSABLE);
        assertThat(result.detail()).isEqualTo(type);
    }

    @ParameterizedTest
    @CsvSource({
        "BAD_REQUEST, INVALID_REQUEST, INVALID_REQUEST",
        "UNAUTHORIZED, UNAUTHORIZED, UNAUTHORIZED",
        "FORBIDDEN, FORBIDDEN, FORBIDDEN",
        "NOT_FOUND, CHANNEL_NOT_FOUND, CHANNEL_NOT_FOUND",
        "CONFLICT, SOME_NEW_CONFLICT, OTHER"
    })
    @DisplayName("400·401·403·404 CHANNEL_NOT_FOUND·그 밖의 409는 PortOne이 처리 전에 거절한 것이라 REJECTED다 — 미지 type은 OTHER")
    void rejected(HttpStatus status, String type, String expectedDetail) {
        // given
        server.expect(requestTo(PAY_URL)).andRespond(error(status, type));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.REJECTED);
        assertThat(result.detail()).isEqualTo(expectedDetail);
        assertThat(logs.list).anyMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR);
        assertNoSensitiveText(result);
    }

    @Test
    @DisplayName("502 PG_PROVIDER의 pgMessage에 고객 정보 모양 문장이 있어도 UNKNOWN(PG_PROVIDER)이고 원문은 어디에도 없다")
    void pgProviderErrorNeverLeaksMessage() {
        // given
        server.expect(requestTo(PAY_URL))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\": \"PG_PROVIDER\", \"message\": \"pg error\", \"pgCode\": \"E01\", \"pgMessage\": \""
                    + PG_MESSAGE + "\"}"));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.UNKNOWN);
        assertThat(result.detail()).isEqualTo("PG_PROVIDER");
        assertNoSensitiveText(result);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    @DisplayName("그 밖의 5xx는 결제됐는지 알 수 없어 UNKNOWN(HTTP_5XX)이다")
    void serverErrors(int status) {
        // given
        server.expect(requestTo(PAY_URL)).andRespond(error(HttpStatus.valueOf(status), "INTERNAL"));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.UNKNOWN);
        assertThat(result.detail()).isEqualTo(PortOneBillingPaymentResult.DETAIL_HTTP_5XX);
    }

    @Test
    @DisplayName("404인데 오류 본문을 읽을 수 없으면 종류를 모르므로 UNKNOWN이다")
    void notFoundWithUnreadableBody() {
        // given
        server.expect(requestTo(PAY_URL))
            .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{\"type\":"));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.UNKNOWN);
        assertThat(result.detail()).isEqualTo(PortOneBillingPaymentResult.DETAIL_OTHER);
    }

    @Test
    @DisplayName("성공 응답 본문을 읽을 수 없으면 UNKNOWN이다")
    void unreadableSuccessBody() {
        // given
        server.expect(requestTo(PAY_URL)).andRespond(withSuccess("{\"payment\": ", MediaType.APPLICATION_JSON));

        // when
        PortOneBillingPaymentResult result = pay();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.UNKNOWN);
        assertThat(result.detail()).isIn(PortOneBillingPaymentResult.DETAIL_MISSING_FIELD,
            PortOneBillingPaymentResult.DETAIL_PARSE);
    }

    @Test
    @DisplayName("timeout이면 예외 대신 UNKNOWN(TIMEOUT)이다 — PG에 도착했을 수 있어 재조회가 정한다")
    void timeout() {
        // given
        PortOneBillingClient timeoutClient =
            clientWithFailingRequestFactory(new SocketTimeoutException("Read timed out"));

        // when
        PortOneBillingPaymentResult result = timeoutClient.payWithBillingKey(PAYMENT_ID, request());

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.UNKNOWN);
        assertThat(result.detail()).isEqualTo(PortOneBillingPaymentResult.DETAIL_TIMEOUT);
        assertNoSensitiveText(result);
    }

    @Test
    @DisplayName("연결 실패도 UNKNOWN(CONNECTION)이다")
    void connectionRefused() {
        // given
        PortOneBillingClient failingClient =
            clientWithFailingRequestFactory(new ConnectException("Connection refused"));

        // when
        PortOneBillingPaymentResult result = failingClient.payWithBillingKey(PAYMENT_ID, request());

        // then
        assertThat(result.outcome()).isEqualTo(PortOneBillingPaymentOutcome.UNKNOWN);
        assertThat(result.detail()).isEqualTo(PortOneBillingPaymentResult.DETAIL_CONNECTION);
    }

    @Test
    @DisplayName("요청 DTO의 toString은 빌링키·고객 정보를 가린다")
    void requestToStringIsRedacted() {
        String text = request().toString() + request().customer().toString() + request().customer().name();
        assertThat(text).doesNotContain(BILLING_KEY, EMAIL, PHONE, NAME);
    }

    private PortOneBillingPaymentResult pay() {
        return client.payWithBillingKey(PAYMENT_ID, request());
    }

    private static PortOneBillingPaymentRequest request() {
        return PortOneBillingPaymentRequest.of(BILLING_KEY, 3_900L, "77", NAME, EMAIL, PHONE);
    }

    // detail은 허용 목록의 코드만, 로그에는 결제 ID·상태·코드만
    private void assertNoSensitiveText(PortOneBillingPaymentResult result) {
        if (result.detail() != null) {
            assertThat(PortOneBillingPaymentResult.SAFE_DETAILS).contains(result.detail());
        }
        assertThat(result.toString()).doesNotContain(BILLING_KEY, EMAIL, PHONE, NAME, PG_MESSAGE);
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(BILLING_KEY, EMAIL, PHONE, NAME, PG_MESSAGE);
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    private static ResponseCreator error(HttpStatus status, String type) {
        return withStatus(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"type\": \"" + type + "\", \"message\": \"" + PG_MESSAGE + "\"}");
    }

    private static PortOneBillingClient clientWithFailingRequestFactory(IOException failure) {
        ClientHttpRequestFactory failingFactory = (uri, method) -> {
            throw failure;
        };
        RestClient restClient = PortOneClientConfig.configure(RestClient.builder(), PROPERTIES)
            .requestFactory(failingFactory)
            .build();
        return new PortOneBillingClient(restClient);
    }
}
