package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneMembershipCancelRequest;
import com.recipick.infra.portone.dto.PortOneMembershipCancelResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 멤버십 금액 지정 취소 클라이언트. 운영 설정을 거친 전용 빌더(읽기 한도 = membershipRefundReadTimeout)에 Mock 서버를 붙인다.
// timeout은 실제 지연 응답을 주는 로컬 HTTP 서버와 짧은 테스트 전용 읽기 한도로 재현한다 — 운영 기본값 60초를 기다리지 않는다
class PortOneMembershipRefundClientTest {

    private static final PortOneProperties PROPERTIES = new PortOneProperties(
        "test-secret",
        "https://api.portone.io",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        "test-store-id",
        "test-channel-key",
        Duration.ofSeconds(2)
    );
    private static final String CANCEL_URL = "https://api.portone.io/payments/mpay_1/cancel";
    private static final String PG_KEY = "mrf_v1_0f9d8c7b-0000-4000-8000-000000000001";
    private static final String REASON = "RECIPICK_MEMBERSHIP_REFUND_V1_10_" + PG_KEY;

    private MockRestServiceServer server;
    private PortOneMembershipRefundClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = PortOneClientConfig.configureForMembershipRefund(RestClient.builder(), PROPERTIES);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneMembershipRefundClient(builder.build());
    }

    @Test
    @DisplayName("성공 응답은 취소 ID·상태·금액·reason·KST 시각을 보존하고, 인증·쌍따옴표 멱등 키·금액 지정 본문을 보낸다")
    void succeeded() {
        // given
        server.expect(requestTo(CANCEL_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne test-secret"))
            .andExpect(header("Idempotency-Key", "\"" + PG_KEY + "\""))
            .andExpect(content().json("""
                {"storeId": "test-store-id", "amount": 1862, "currentCancellableAmount": 3900,
                 "requester": "CUSTOMER", "reason": "%s"}
                """.formatted(REASON), JsonCompareMode.STRICT))
            .andRespond(withSuccess("""
                {"cancellation": {"status": "SUCCEEDED", "id": "cxl_1", "totalAmount": 1862, "reason": "%s",
                 "requestedAt": "2027-03-10T06:00:00Z", "cancelledAt": "2027-03-10T06:00:03Z", "trigger": "API"}}
                """.formatted(REASON), MediaType.APPLICATION_JSON));

        // when
        PortOneMembershipCancelResult result = cancel();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.SUCCEEDED);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.cancellationId()).isEqualTo("cxl_1");
        assertThat(result.cancellationStatus()).isEqualTo(PortOneCancellationStatus.SUCCEEDED);
        assertThat(result.totalAmount()).isEqualTo(1_862L);
        assertThat(result.reason()).isEqualTo(REASON);
        assertThat(result.requestedAt()).isEqualTo(LocalDateTime.of(2027, 3, 10, 15, 0, 0));
        assertThat(result.canceledAt()).isEqualTo(LocalDateTime.of(2027, 3, 10, 15, 0, 3));
        server.verify();
    }

    @Test
    @DisplayName("REQUESTED는 처리 중, FAILED는 실패, 취소 ID 없는 200은 결과 불명이다")
    void statusesWithoutId() {
        // given
        server.expect(requestTo(CANCEL_URL)).andRespond(withSuccess(
            "{\"cancellation\":{\"status\":\"REQUESTED\",\"id\":\"cxl_2\",\"totalAmount\":1862}}",
            MediaType.APPLICATION_JSON));
        server.expect(requestTo(CANCEL_URL)).andRespond(withSuccess(
            "{\"cancellation\":{\"status\":\"FAILED\",\"id\":\"cxl_3\",\"totalAmount\":1862}}",
            MediaType.APPLICATION_JSON));
        server.expect(requestTo(CANCEL_URL)).andRespond(withSuccess(
            "{\"cancellation\":{\"status\":\"SUCCEEDED\"}}", MediaType.APPLICATION_JSON));

        // when
        PortOneMembershipCancelResult requested = cancel();
        PortOneMembershipCancelResult failed = cancel();
        PortOneMembershipCancelResult noId = cancel();

        // then
        assertThat(requested.outcome()).isEqualTo(PortOneCancelOutcome.REQUESTED);
        assertThat(requested.cancellationId()).isEqualTo("cxl_2");
        assertThat(failed.outcome()).isEqualTo(PortOneCancelOutcome.FAILED);
        assertThat(noId.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        assertThat(noId.hasCancellationId()).isFalse();
    }

    @Test
    @DisplayName("400은 요청 거절, 401·403은 인증 오류로 구분되고 둘 다 REJECTED다")
    void rejectedDistinguishesRequestAndAuth() {
        // given
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"INVALID_REQUEST\"}"));
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"FORBIDDEN\"}"));

        // when
        PortOneMembershipCancelResult bad = cancel();
        PortOneMembershipCancelResult forbidden = cancel();

        // then
        assertThat(bad.outcome()).isEqualTo(PortOneCancelOutcome.REJECTED);
        assertThat(bad.isRequestRejected()).isTrue();
        assertThat(bad.isAuthError()).isFalse();
        assertThat(bad.errorType()).isEqualTo("INVALID_REQUEST");
        assertThat(forbidden.outcome()).isEqualTo(PortOneCancelOutcome.REJECTED);
        assertThat(forbidden.isAuthError()).isTrue();
    }

    @Test
    @DisplayName("409 IDEMPOTENCY_OUTSTANDING_REQUEST는 결과 불명(같은 키 재확인), 잔액·상태 409와 404는 MISMATCH, 5xx·알 수 없는 409는 결과 불명")
    void conflictClassification() {
        // given
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"IDEMPOTENCY_OUTSTANDING_REQUEST\"}"));
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"CANCEL_AMOUNT_EXCEEDS_CANCELLABLE_AMOUNT\"}"));
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"PAYMENT_NOT_FOUND\"}"));
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"INTERNAL\"}"));
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"SOMETHING_NEW\"}"));

        // when
        PortOneMembershipCancelResult outstanding = cancel();
        PortOneMembershipCancelResult exceeds = cancel();
        PortOneMembershipCancelResult notFound = cancel();
        PortOneMembershipCancelResult serverError = cancel();
        PortOneMembershipCancelResult unknownConflict = cancel();

        // then
        assertThat(outstanding.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        assertThat(outstanding.errorType()).isEqualTo(PortOneMembershipCancelResult.TYPE_IDEMPOTENCY_OUTSTANDING);
        assertThat(exceeds.outcome()).isEqualTo(PortOneCancelOutcome.MISMATCH);
        assertThat(notFound.outcome()).isEqualTo(PortOneCancelOutcome.MISMATCH);
        assertThat(serverError.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        assertThat(unknownConflict.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
    }

    @Test
    @DisplayName("읽기 한도를 넘기면 예외 없이 결과 불명(timeout)으로 돌려준다 — 실제 지연 응답 서버와 테스트 전용 한도로 재현")
    void readTimeoutIsUnconfirmed() throws IOException {
        // given
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"cancellation\":{\"status\":\"SUCCEEDED\",\"id\":\"late\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        slow.start();
        try {
            PortOneProperties shortTimeout = new PortOneProperties("test-secret",
                "http://127.0.0.1:" + slow.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(1),
                "test-store-id", "test-channel-key", Duration.ofMillis(300));
            PortOneMembershipRefundClient slowClient = new PortOneMembershipRefundClient(
                PortOneClientConfig.configureForMembershipRefund(RestClient.builder(), shortTimeout).build());

            // when
            PortOneMembershipCancelResult result = slowClient.cancel("mpay_1", PG_KEY, request());

            // then
            assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
            assertThat(result.detail()).isEqualTo("timeout");
            assertThat(result.hasCancellationId()).isFalse();
        } finally {
            slow.stop(0);
        }
    }

    private PortOneMembershipCancelResult cancel() {
        return client.cancel("mpay_1", PG_KEY, request());
    }

    private static PortOneMembershipCancelRequest request() {
        return new PortOneMembershipCancelRequest("test-store-id", 1_862L, 3_900L,
            PortOneMembershipCancelRequest.CUSTOMER, REASON);
    }
}
