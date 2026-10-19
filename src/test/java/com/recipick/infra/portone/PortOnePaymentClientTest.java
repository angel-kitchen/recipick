package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PortOnePaymentClientTest {

    private static final PortOneProperties PROPERTIES = new PortOneProperties(
            "test-secret", "https://api.portone.io", Duration.ofSeconds(1), Duration.ofSeconds(1));

    private static final String PAID_BODY = """
            {
              "status": "PAID",
              "id": "pay_1",
              "transactionId": "tx_1",
              "amount": { "total": 23800, "paid": 23800 },
              "currency": "KRW",
              "paidAt": "2026-10-20T10:03:11Z",
              "customData": "{\\"orderId\\":10}"
            }
            """;

    private MockRestServiceServer server;
    private PortOnePaymentClient client;

    @BeforeEach
    void setUp() {
        // 운영 빈과 같은 configure()를 거친 빌더에 Mock 서버를 붙여 헤더·컨버터 설정을 그대로 검증한다
        RestClient.Builder builder = PortOneClientConfig.configure(RestClient.builder(), PROPERTIES);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOnePaymentClient(builder.build());
    }

    @Test
    @DisplayName("결제 조회 성공 시 Authorization 헤더를 붙이고 응답을 도메인용 DTO로 바꾼다")
    void getPayment() {
        // given
        server.expect(requestTo("https://api.portone.io/payments/pay_1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne test-secret"))
                .andRespond(withSuccess(PAID_BODY, MediaType.APPLICATION_JSON));

        // when
        PortOnePayment payment = client.getPayment("pay_1");

        // then
        server.verify();
        assertThat(payment.status()).isEqualTo(PortOnePaymentStatus.PAID);
        assertThat(payment.transactionId()).isEqualTo("tx_1");
        assertThat(payment.totalAmount()).isEqualTo(23800L);
        assertThat(payment.paidAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 19, 3, 11));
        assertThat(payment.customDataOrderId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("결제 ID의 특수문자를 URL 인코딩한다")
    void encodePaymentId() {
        // given
        server.expect(requestTo("https://api.portone.io/payments/pay_a%20b"))
                .andRespond(withSuccess(PAID_BODY, MediaType.APPLICATION_JSON));

        // when
        client.getPayment("pay_a b");

        // then
        server.verify();
    }

    @Test
    @DisplayName("알 수 없는 status가 와도 UNKNOWN으로 읽는다 (PortOne 전용 매퍼 사용 확인)")
    void unknownStatusViaClient() {
        // given
        server.expect(requestTo("https://api.portone.io/payments/pay_1"))
                .andRespond(withSuccess("{\"status\":\"PAY_PENDING\",\"id\":\"pay_1\"}",
                        MediaType.APPLICATION_JSON));

        // when
        PortOnePayment payment = client.getPayment("pay_1");

        // then
        assertThat(payment.status()).isEqualTo(PortOnePaymentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("404면 PAYMENT_NOT_FOUND")
    void notFound() {
        // given
        server.expect(requestTo("https://api.portone.io/payments/pay_missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"PAYMENT_NOT_FOUND\",\"message\":\"not found\"}"));

        // when & then
        assertErrorCode(() -> client.getPayment("pay_missing"), PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("401·403·500 오류 응답이면 EXTERNAL_SERVICE_ERROR")
    void errorResponses() {
        for (HttpStatus status : new HttpStatus[] {
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.INTERNAL_SERVER_ERROR}) {
            // given
            server.reset();
            server.expect(requestTo("https://api.portone.io/payments/pay_1"))
                    .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body("{}"));

            // when & then
            assertErrorCode(() -> client.getPayment("pay_1"), CommonErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    @Test
    @DisplayName("응답 본문을 읽을 수 없으면 EXTERNAL_SERVICE_ERROR")
    void unreadableBody() {
        // given
        server.expect(requestTo("https://api.portone.io/payments/pay_1"))
                .andRespond(withSuccess("{\"status\": PAID", MediaType.APPLICATION_JSON));

        // when & then
        assertErrorCode(() -> client.getPayment("pay_1"), CommonErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    @DisplayName("읽기 timeout이면 EXTERNAL_SERVICE_TIMEOUT")
    void readTimeout() {
        // given
        PortOnePaymentClient timeoutClient = clientWithFailingRequestFactory(
                new SocketTimeoutException("Read timed out"));

        // when & then
        assertErrorCode(() -> timeoutClient.getPayment("pay_1"), CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT);
    }

    @Test
    @DisplayName("JDK HttpClient의 timeout 예외도 EXTERNAL_SERVICE_TIMEOUT")
    void httpTimeout() {
        // given
        PortOnePaymentClient timeoutClient = clientWithFailingRequestFactory(
                new HttpTimeoutException("request timed out"));

        // when & then
        assertErrorCode(() -> timeoutClient.getPayment("pay_1"), CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT);
    }

    @Test
    @DisplayName("연결 실패는 timeout이 아니므로 EXTERNAL_SERVICE_ERROR")
    void connectionRefused() {
        // given
        PortOnePaymentClient failingClient = clientWithFailingRequestFactory(
                new ConnectException("Connection refused"));

        // when & then
        assertErrorCode(() -> failingClient.getPayment("pay_1"), CommonErrorCode.EXTERNAL_SERVICE_ERROR);
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

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
