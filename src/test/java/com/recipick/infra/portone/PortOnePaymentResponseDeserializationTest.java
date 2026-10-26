package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePaymentResponse;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortOnePaymentResponseDeserializationTest {

    private final ObjectMapper mapper = PortOneClientConfig.newPortOneObjectMapper();

    @Test
    @DisplayName("PAID 응답의 결제 확정용 필드를 읽는다")
    void deserializePaidResponse() throws Exception {
        // given
        String json = """
                {
                  "status": "PAID",
                  "id": "pay_1",
                  "transactionId": "tx_1",
                  "amount": { "total": 23800, "taxFree": 0, "vat": 2164, "supply": 21636,
                              "discount": 0, "paid": 23800, "cancelled": 0, "cancelledTaxFree": 0 },
                  "currency": "KRW",
                  "paidAt": "2026-10-20T10:03:11Z",
                  "customData": "{\\"orderId\\":10}",
                  "method": { "type": "PaymentMethodCard" },
                  "channel": { "type": "LIVE", "id": "channel-1" },
                  "webhooks": [ { "id": "wh_1" } ]
                }
                """;

        // when
        PortOnePaymentResponse response = mapper.readValue(json, PortOnePaymentResponse.class);

        // then
        assertThat(response.status()).isEqualTo(PortOnePaymentStatus.PAID);
        assertThat(response.id()).isEqualTo("pay_1");
        assertThat(response.transactionId()).isEqualTo("tx_1");
        assertThat(response.amount().total()).isEqualTo(23800L);
        assertThat(response.amount().paid()).isEqualTo(23800L);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.paidAt()).isEqualTo(OffsetDateTime.parse("2026-10-20T10:03:11Z"));
        assertThat(response.customData()).isEqualTo("{\"orderId\":10}");
        assertThat(response.failure()).isNull();
    }

    @Test
    @DisplayName("PortOne 철자 CANCELLED·PARTIAL_CANCELLED를 팀 철자 enum으로 읽는다")
    void deserializeCancelledSpelling() throws Exception {
        // given
        String cancelled = "{\"status\":\"CANCELLED\",\"id\":\"pay_1\"}";
        String partial = "{\"status\":\"PARTIAL_CANCELLED\",\"id\":\"pay_1\"}";

        // when
        PortOnePaymentResponse cancelledResponse = mapper.readValue(cancelled, PortOnePaymentResponse.class);
        PortOnePaymentResponse partialResponse = mapper.readValue(partial, PortOnePaymentResponse.class);

        // then
        assertThat(cancelledResponse.status()).isEqualTo(PortOnePaymentStatus.CANCELED);
        assertThat(partialResponse.status()).isEqualTo(PortOnePaymentStatus.PARTIAL_CANCELED);
    }

    @Test
    @DisplayName("알 수 없는 status 값은 UNKNOWN으로 읽어 조회가 깨지지 않는다")
    void deserializeUnknownStatus() throws Exception {
        // given
        String json = "{\"status\":\"PAY_PENDING\",\"id\":\"pay_1\"}";

        // when
        PortOnePaymentResponse response = mapper.readValue(json, PortOnePaymentResponse.class);

        // then
        assertThat(response.status()).isEqualTo(PortOnePaymentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("paidAt은 Z·+09:00·소수점 초 표기를 모두 OffsetDateTime으로 읽는다")
    void deserializePaidAtFormats() throws Exception {
        // given
        String utc = "{\"status\":\"PAID\",\"paidAt\":\"2026-10-20T10:03:11Z\"}";
        String kst = "{\"status\":\"PAID\",\"paidAt\":\"2026-10-20T19:03:11+09:00\"}";
        String fractional = "{\"status\":\"PAID\",\"paidAt\":\"2026-10-20T10:03:11.123456Z\"}";

        // when
        OffsetDateTime utcValue = mapper.readValue(utc, PortOnePaymentResponse.class).paidAt();
        OffsetDateTime kstValue = mapper.readValue(kst, PortOnePaymentResponse.class).paidAt();
        OffsetDateTime fractionalValue = mapper.readValue(fractional, PortOnePaymentResponse.class).paidAt();

        // then
        assertThat(utcValue.toInstant()).isEqualTo(kstValue.toInstant());
        assertThat(fractionalValue.getNano()).isEqualTo(123_456_000);
    }

    @Test
    @DisplayName("FAILED 응답의 failure 객체를 읽는다")
    void deserializeFailedResponse() throws Exception {
        // given
        String json = """
                {
                  "status": "FAILED",
                  "id": "pay_1",
                  "transactionId": "tx_1",
                  "failedAt": "2026-10-20T10:03:11Z",
                  "failure": { "reason": "card declined", "pgCode": "E001", "pgMessage": "한도 초과" }
                }
                """;

        // when
        PortOnePaymentResponse response = mapper.readValue(json, PortOnePaymentResponse.class);

        // then
        assertThat(response.status()).isEqualTo(PortOnePaymentStatus.FAILED);
        assertThat(response.failure().reason()).isEqualTo("card declined");
        assertThat(response.failure().pgCode()).isEqualTo("E001");
        assertThat(response.failure().pgMessage()).isEqualTo("한도 초과");
        assertThat(response.paidAt()).isNull();
        assertThat(response.amount()).isNull();
    }

    @Test
    @DisplayName("amount 안의 알 수 없는 필드와 알 수 없는 중첩 객체를 무시한다")
    void ignoreUnknownNestedFields() throws Exception {
        // given
        String json = """
                {
                  "status": "PAID",
                  "amount": { "total": 100, "somethingNew": { "a": 1 } },
                  "newTopLevel": [1, 2, 3]
                }
                """;

        // when
        PortOnePaymentResponse response = mapper.readValue(json, PortOnePaymentResponse.class);

        // then
        assertThat(response.amount().total()).isEqualTo(100L);
    }

    @Test
    @DisplayName("cancellations의 상태·금액·시각·경로를 읽는다 — PortOne 키 cancelledAt, 모르는 값은 UNKNOWN")
    void deserializeCancellations() throws Exception {
        // given — PAID 결제에도 아직 끝나지 않은 취소 내역이 온다
        String json = """
                {
                  "status": "PAID",
                  "id": "pay_1",
                  "cancellations": [
                    { "status": "REQUESTED", "id": "cancel_1", "totalAmount": 23800,
                      "requestedAt": "2026-10-25T03:00:00Z", "trigger": "API", "reason": "단순 변심" },
                    { "status": "SUCCEEDED", "id": "cancel_2", "totalAmount": 23800,
                      "requestedAt": "2026-10-25T03:00:00Z", "cancelledAt": "2026-10-25T03:00:10Z",
                      "trigger": "CONSOLE", "pgCancellationId": "pg_1" },
                    { "status": "SOMETHING_NEW", "id": "cancel_3", "trigger": "SOMETHING_NEW" }
                  ]
                }
                """;

        // when
        PortOnePaymentResponse response = mapper.readValue(json, PortOnePaymentResponse.class);

        // then
        assertThat(response.cancellations()).hasSize(3);
        PortOnePaymentResponse.Cancellation requested = response.cancellations().get(0);
        assertThat(requested.status()).isEqualTo(PortOneCancellationStatus.REQUESTED);
        assertThat(requested.id()).isEqualTo("cancel_1");
        assertThat(requested.totalAmount()).isEqualTo(23800L);
        assertThat(requested.requestedAt()).isEqualTo(OffsetDateTime.parse("2026-10-25T03:00:00Z"));
        assertThat(requested.canceledAt()).isNull();
        assertThat(requested.trigger()).isEqualTo(PortOneCancellationTrigger.API);
        PortOnePaymentResponse.Cancellation succeeded = response.cancellations().get(1);
        assertThat(succeeded.canceledAt()).isEqualTo(OffsetDateTime.parse("2026-10-25T03:00:10Z"));
        assertThat(succeeded.trigger()).isEqualTo(PortOneCancellationTrigger.CONSOLE);
        PortOnePaymentResponse.Cancellation unknown = response.cancellations().get(2);
        assertThat(unknown.status()).isEqualTo(PortOneCancellationStatus.UNKNOWN);
        assertThat(unknown.trigger()).isEqualTo(PortOneCancellationTrigger.UNKNOWN);
    }

    @Test
    @DisplayName("cancellations가 없는 응답은 null로 읽는다")
    void deserializeWithoutCancellations() throws Exception {
        // when
        PortOnePaymentResponse response =
            mapper.readValue("{\"status\":\"PAID\",\"id\":\"pay_1\"}", PortOnePaymentResponse.class);

        // then
        assertThat(response.cancellations()).isNull();
    }
}
