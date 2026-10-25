package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.infra.portone.dto.PortOneWebhook;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortOneWebhookDeserializationTest {

    private final ObjectMapper mapper = PortOneClientConfig.newPortOneObjectMapper();

    @Test
    @DisplayName("Transaction.Paid 본문의 type·timestamp·data 식별자를 읽는다")
    void deserializePaid() throws Exception {
        // given
        String json = """
                {
                  "type": "Transaction.Paid",
                  "timestamp": "2026-10-25T03:00:00.123Z",
                  "data": { "storeId": "store-1", "paymentId": "pay_1", "transactionId": "tx_1" }
                }
                """;

        // when
        PortOneWebhook webhook = mapper.readValue(json, PortOneWebhook.class);

        // then
        assertThat(webhook.type()).isEqualTo("Transaction.Paid");
        assertThat(webhook.timestamp()).isEqualTo(OffsetDateTime.parse("2026-10-25T03:00:00.123Z"));
        assertThat(webhook.data().storeId()).isEqualTo("store-1");
        assertThat(webhook.data().paymentId()).isEqualTo("pay_1");
        assertThat(webhook.data().transactionId()).isEqualTo("tx_1");
        assertThat(webhook.data().cancellationId()).isNull();
    }

    @Test
    @DisplayName("취소 계열 본문은 cancellationId를 함께 읽는다")
    void deserializeCancelled() throws Exception {
        // given
        String json = """
                {
                  "type": "Transaction.Cancelled",
                  "timestamp": "2026-10-25T03:00:00Z",
                  "data": { "storeId": "store-1", "paymentId": "pay_1", "transactionId": "tx_1",
                            "cancellationId": "cancel_1" }
                }
                """;

        // when
        PortOneWebhook webhook = mapper.readValue(json, PortOneWebhook.class);

        // then
        assertThat(webhook.type()).isEqualTo("Transaction.Cancelled");
        assertThat(webhook.data().cancellationId()).isEqualTo("cancel_1");
    }

    @Test
    @DisplayName("모르는 type과 모르는 필드가 와도 파싱이 깨지지 않는다 — PortOne이 예고 없이 추가할 수 있다")
    void ignoreUnknownTypeAndFields() throws Exception {
        // given
        String json = """
                {
                  "type": "BillingKey.Issued",
                  "timestamp": "2026-10-25T03:00:00Z",
                  "data": { "storeId": "store-1", "billingKey": "billing-key-1", "somethingNew": { "a": 1 } },
                  "newTopLevel": [1, 2]
                }
                """;

        // when
        PortOneWebhook webhook = mapper.readValue(json, PortOneWebhook.class);

        // then
        assertThat(webhook.type()).isEqualTo("BillingKey.Issued");
        assertThat(webhook.data().storeId()).isEqualTo("store-1");
        assertThat(webhook.data().paymentId()).isNull();
    }
}
