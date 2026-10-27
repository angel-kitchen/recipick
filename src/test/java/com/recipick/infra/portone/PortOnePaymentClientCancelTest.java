package com.recipick.infra.portone;

import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 결제 조회(PortOnePaymentClientTest)와 같은 방식으로 운영 설정을 거친 빌더에 Mock 서버를 붙인다
class PortOnePaymentClientCancelTest {

    private static final PortOneProperties PROPERTIES = new PortOneProperties(
        "test-secret",
        "https://api.portone.io",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        "test-store-id",
        "test-channel-key"
    );
    private static final String CANCEL_URL = "https://api.portone.io/payments/pay_1/cancel";
    private static final String SERVER_KEY = "recipick-refund-12";

    private MockRestServiceServer server;
    private PortOnePaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = PortOneClientConfig.configure(RestClient.builder(), PROPERTIES);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOnePaymentClient(builder.build());
    }

    @Test
    @DisplayName("SUCCEEDED면 취소 내역 ID와 KST 취소 시각을 돌려주고, 인증·쌍따옴표 멱등 키·전액 취소 본문을 보낸다")
    void succeeded() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne test-secret"))
            .andExpect(header("Idempotency-Key", "\"" + SERVER_KEY + "\""))
            .andExpect(content().json("""
                {"reason": "단순 변심", "currentCancellableAmount": 23800, "requester": "CUSTOMER"}
                """, JsonCompareMode.STRICT))
            .andRespond(withSuccess(
                cancellation("SUCCEEDED", "\"2026-10-23T10:08:40Z\""), MediaType.APPLICATION_JSON));

        // when
        PortOneCancelResult result = cancel();

        // then — amount를 보내지 않아 전액 취소다 (STRICT 비교라 본문에 다른 필드가 있으면 실패한다)
        server.verify();
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.SUCCEEDED);
        assertThat(result.cancellationId()).isEqualTo("cancel_1");
        assertThat(result.canceledAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 19, 8, 40));
    }


    @Test
    @DisplayName("SYSTEM 환불의 사후 반환은 같은 경로·멱등 키 규칙으로 requester=ADMIN 본문을 보낸다")
    void succeededByAdmin() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Idempotency-Key", "\"" + SERVER_KEY + "\""))
            .andExpect(content().json("""
                {"reason": "결제 대기 5분 만료", "currentCancellableAmount": 23800, "requester": "ADMIN"}
                """, JsonCompareMode.STRICT))
            .andRespond(withSuccess(
                cancellation("SUCCEEDED", "\"2026-10-23T10:08:40Z\""), MediaType.APPLICATION_JSON));

        // when
        PortOneCancelResult result = client.cancelPaymentByAdmin("pay_1", SERVER_KEY, "결제 대기 5분 만료", 23800L);

        // then — 회원 취소와는 본문의 requester만 다르다 (STRICT 비교라 다른 필드가 섞이면 실패한다)
        server.verify();
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.SUCCEEDED);
        assertThat(result.cancellationId()).isEqualTo("cancel_1");
    }

    @Test
    @DisplayName("SUCCEEDED인데 취소 시각이 없으면 시각을 지어내지 않고 null로 돌려준다")
    void succeededWithoutCanceledAt() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andRespond(withSuccess(cancellation("SUCCEEDED", "null"), MediaType.APPLICATION_JSON));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.SUCCEEDED);
        assertThat(result.cancellationId()).isEqualTo("cancel_1");
        assertThat(result.canceledAt()).isNull();
    }

    @Test
    @DisplayName("REQUESTED여도 취소 내역 ID는 돌려준다 — 웹훅·재조회가 이 ID로 환불을 찾는다")
    void requested() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andRespond(withSuccess(cancellation("REQUESTED", "null"), MediaType.APPLICATION_JSON));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.REQUESTED);
        assertThat(result.cancellationId()).isEqualTo("cancel_1");
    }

    @Test
    @DisplayName("FAILED면 취소 내역 ID와 함께 FAILED로 돌려준다")
    void failed() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andRespond(withSuccess(cancellation("FAILED", "null"), MediaType.APPLICATION_JSON));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.FAILED);
        assertThat(result.cancellationId()).isEqualTo("cancel_1");
    }

    @Test
    @DisplayName("취소 상태가 있어도 cancellation id가 없으면 믿지 않고 UNCONFIRMED다")
    void missingCancellationId() {
        // given — FAILED를 믿고 PAID로 되돌리면 나중에 웹훅이 와도 어느 환불인지 찾을 수 없다
        server.expect(requestTo(CANCEL_URL))
            .andRespond(withSuccess("{\"cancellation\": {\"status\": \"FAILED\"}}", MediaType.APPLICATION_JSON));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        assertThat(result.cancellationId()).isNull();
    }

    @Test
    @DisplayName("알 수 없는 취소 상태는 UNCONFIRMED다")
    void unknownCancellationStatus() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andRespond(withSuccess(cancellation("PARTIALLY_DONE", "null"), MediaType.APPLICATION_JSON));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        assertThat(result.cancellationId()).isEqualTo("cancel_1");
    }

    @ParameterizedTest
    @CsvSource({
        "BAD_REQUEST, INVALID_REQUEST",
        "UNAUTHORIZED, UNAUTHORIZED",
        "FORBIDDEN, FORBIDDEN"
    })
    @DisplayName("400·401·403은 PortOne이 처리 전에 거절한 것이라 REJECTED다")
    void rejected(HttpStatus status, String type) {
        // given
        server.expect(requestTo(CANCEL_URL)).andRespond(error(status, type));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.REJECTED);
        assertThat(result.cancellationId()).isNull();
    }

    @Test
    @DisplayName("404는 우리는 PAID인데 PortOne에 결제가 없다는 뜻이라 MISMATCH다")
    void notFound() {
        // given
        server.expect(requestTo(CANCEL_URL)).andRespond(error(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "PAYMENT_ALREADY_CANCELLED",
        "PAYMENT_NOT_PAID",
        "CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN",
        "CANCEL_AMOUNT_EXCEEDS_CANCELLABLE_AMOUNT",
        "SUM_OF_PARTS_EXCEEDS_CANCEL_AMOUNT",
        "CANCEL_TAX_FREE_AMOUNT_EXCEEDS_CANCELLABLE_TAX_FREE_AMOUNT",
        "CANCEL_TAX_AMOUNT_EXCEEDS_CANCELLABLE_TAX_AMOUNT",
        "MAX_CANCEL_COUNT_REACHED"
    })
    @DisplayName("상태 불일치 409는 PAID로 되돌리면 안 되는 경우라 MISMATCH다")
    void mismatchConflicts(String type) {
        // given
        server.expect(requestTo(CANCEL_URL)).andRespond(error(HttpStatus.CONFLICT, type));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.MISMATCH);
        assertThat(result.detail()).contains(type);
    }

    @Test
    @DisplayName("같은 키 요청이 아직 처리 중이라는 409는 앞선 호출이 성공할 수 있으므로 UNCONFIRMED다")
    void outstandingRequest() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andRespond(error(HttpStatus.CONFLICT, "IDEMPOTENCY_OUTSTANDING_REQUEST"));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
    }

    @Test
    @DisplayName("409인데 오류 본문을 읽을 수 없으면 종류를 모르므로 UNCONFIRMED다")
    void conflictWithUnreadableBody() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\":"));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
    }

    @Test
    @DisplayName("500·502(PG_PROVIDER)는 PG 내부에서 취소됐는지 알 수 없어 UNCONFIRMED다")
    void serverErrors() {
        for (HttpStatus status : new HttpStatus[] {HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY}) {
            // given
            server.reset();
            server.expect(requestTo(CANCEL_URL)).andRespond(error(status, "PG_PROVIDER"));

            // when
            PortOneCancelResult result = cancel();

            // then
            assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        }
    }

    @Test
    @DisplayName("성공 응답 본문을 읽을 수 없으면 UNCONFIRMED다")
    void unreadableSuccessBody() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andRespond(withSuccess("{\"cancellation\": ", MediaType.APPLICATION_JSON));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
    }

    @Test
    @DisplayName("2xx 응답에 본문이 없으면 UNCONFIRMED다")
    void emptySuccessBody() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // when
        PortOneCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
    }

    @Test
    @DisplayName("timeout이면 예외 대신 UNCONFIRMED를 돌려준다")
    void timeout() {
        // given
        PortOnePaymentClient timeoutClient =
            clientWithFailingRequestFactory(new SocketTimeoutException("Read timed out"));

        // when
        PortOneCancelResult result = timeoutClient.cancelPayment("pay_1", SERVER_KEY, "단순 변심", 23800L);

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        assertThat(result.detail()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("연결 실패도 PortOne에 닿았는지 모르므로 UNCONFIRMED다")
    void connectionRefused() {
        // given
        PortOnePaymentClient failingClient =
            clientWithFailingRequestFactory(new ConnectException("Connection refused"));

        // when
        PortOneCancelResult result = failingClient.cancelPayment("pay_1", SERVER_KEY, "단순 변심", 23800L);

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        assertThat(result.detail()).isEqualTo("연결 실패");
    }

    private PortOneCancelResult cancel() {
        return client.cancelPayment("pay_1", SERVER_KEY, "단순 변심", 23800L);
    }

    // PortOne 스키마의 필수 필드를 채운 취소 응답. cancelledAt(PortOne 철자)은 JSON 값 그대로 받는다("null" 또는 "\"...Z\"")
    private static String cancellation(String status, String canceledAtJson) {
        return """
            {
              "cancellation": {
                "status": "%s",
                "id": "cancel_1",
                "pgCancellationId": "pg_cancel_1",
                "totalAmount": 23800,
                "taxFreeAmount": 0,
                "vatAmount": 2164,
                "reason": "단순 변심",
                "requestedAt": "2026-10-23T10:08:30Z",
                "cancelledAt": %s
              }
            }
            """.formatted(status, canceledAtJson);
    }

    private static ResponseCreator error(HttpStatus status, String type) {
        return withStatus(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"type\": \"" + type + "\", \"message\": \"test\"}");
    }

    private static PortOnePaymentClient clientWithFailingRequestFactory(IOException failure) {
        ClientHttpRequestFactory failingFactory = (uri, method) -> {
            throw failure;
        };
        RestClient restClient = PortOneClientConfig.configure(RestClient.builder(), PROPERTIES)
            .requestFactory(failingFactory)
            .build();
        return new PortOnePaymentClient(restClient);
    }
}
