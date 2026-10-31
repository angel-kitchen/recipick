package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfo;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfoResponse;
import com.recipick.infra.portone.dto.PortOneBillingKeyStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// PortOne 빌링키 조회 응답을 운영과 같은 매퍼로 읽는다. 외부 호출 없이 역직렬화와 도메인 변환만 본다.
// JSON 모양은 PortOne V2 공식 OpenAPI의 IssuedBillingKeyInfo·DeletedBillingKeyInfo를 따른다
class PortOneBillingKeyInfoResponseDeserializationTest {

    private final ObjectMapper mapper = PortOneClientConfig.newPortOneObjectMapper();

    @Test
    @DisplayName("ISSUED 응답에서 등록 검증에 쓰는 필드를 읽고, 채널은 key·type 쌍으로, 시각은 KST로 바꾼다")
    void deserializeIssued() throws Exception {
        // given
        String json = """
                {
                  "status": "ISSUED",
                  "billingKey": "billing-key-test-0001",
                  "merchantId": "merchant-1",
                  "storeId": "test-store-id",
                  "channels": [
                    { "type": "LIVE", "id": "ch-live", "key": "live-channel-key", "name": "실채널",
                      "pgProvider": "INICIS_V2", "pgMerchantId": "mid-live" },
                    { "type": "TEST", "id": "ch-test", "key": "test-channel-key", "name": "테스트",
                      "pgProvider": "INICIS_V2", "pgMerchantId": "mid-test" }
                  ],
                  "customer": { "id": "7", "name": { "full": "홍길동" }, "email": "a@b.c", "phoneNumber": "01012345678" },
                  "methods": [
                    { "type": "BillingKeyPaymentMethodCard",
                      "card": { "publisher": "KOOKMIN", "issuer": "KOOKMIN", "brand": "LOCAL", "type": "CREDIT",
                                "ownerType": "PERSONAL", "bin": "123456", "name": "국민", "number": "1234****5678" } }
                  ],
                  "issueId": "rcp-bk-0f8a1c2e-6d4b-4a1e-9c3f-1234567890ab",
                  "issueName": "레시픽 멤버십 자동결제 카드 등록",
                  "issuedAt": "2026-10-31T05:00:00Z",
                  "requestedAt": "2026-10-31T04:59:30Z",
                  "pgBillingKeyIssueResponses": [ { "type": "PgBillingKeyIssueResponse" } ]
                }
                """;

        // when
        PortOneBillingKeyInfoResponse response = mapper.readValue(json, PortOneBillingKeyInfoResponse.class);
        PortOneBillingKeyInfo info = PortOneBillingKeyInfo.from(response);

        // then
        assertThat(response.status()).isEqualTo(PortOneBillingKeyStatus.ISSUED);
        assertThat(response.issuedAt()).isEqualTo(OffsetDateTime.parse("2026-10-31T05:00:00Z"));
        assertThat(info.billingKey()).isEqualTo("billing-key-test-0001");
        assertThat(info.storeId()).isEqualTo("test-store-id");
        assertThat(info.channels()).containsExactly(
            new PortOneBillingKeyInfo.Channel("live-channel-key", "LIVE"),
            new PortOneBillingKeyInfo.Channel("test-channel-key", "TEST"));
        assertThat(info.channelOf("test-channel-key")).contains(
            new PortOneBillingKeyInfo.Channel("test-channel-key", "TEST"));
        assertThat(info.channelOf("no-such-key")).isEmpty();
        assertThat(info.customerId()).isEqualTo("7");
        assertThat(info.issueId()).isEqualTo("rcp-bk-0f8a1c2e-6d4b-4a1e-9c3f-1234567890ab");
        assertThat(info.cardMethod()).isTrue();
        assertThat(info.issuedAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 14, 0));
        assertThat(info.deletedAt()).isNull();
    }

    @Test
    @DisplayName("DELETED 응답은 삭제 시각을 KST로 읽는다")
    void deserializeDeleted() throws Exception {
        // given
        String json = """
                {
                  "status": "DELETED",
                  "billingKey": "billing-key-test-0002",
                  "merchantId": "merchant-1",
                  "storeId": "test-store-id",
                  "channels": [ { "type": "TEST", "id": "ch-test", "key": "test-channel-key" } ],
                  "customer": { },
                  "issuedAt": "2026-10-31T05:00:00Z",
                  "deletedAt": "2026-10-31T06:30:00Z"
                }
                """;

        // when
        PortOneBillingKeyInfo info = PortOneBillingKeyInfo.from(
            mapper.readValue(json, PortOneBillingKeyInfoResponse.class));

        // then
        assertThat(info.status()).isEqualTo(PortOneBillingKeyStatus.DELETED);
        assertThat(info.deletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 15, 30));
        assertThat(info.customerId()).isNull();
        assertThat(info.issueId()).isNull();
        assertThat(info.cardMethod()).isFalse();
    }

    @Test
    @DisplayName("모르는 상태 값은 UNKNOWN으로 읽힌다 — 클라이언트가 COMMON-008로 바꾼다")
    void unknownStatus() throws Exception {
        // given
        String json = """
                { "status": "SOMETHING_NEW", "billingKey": "billing-key-test-0003", "storeId": "test-store-id" }
                """;

        // when
        PortOneBillingKeyInfoResponse response = mapper.readValue(json, PortOneBillingKeyInfoResponse.class);

        // then
        assertThat(response.status()).isEqualTo(PortOneBillingKeyStatus.UNKNOWN);
    }

    @Test
    @DisplayName("customer·issueId·methods·channels가 없는 응답도 읽히고 도메인 값은 null·빈 목록·false다")
    void missingOptionalFields() throws Exception {
        // given
        String json = """
                { "status": "ISSUED", "billingKey": "billing-key-test-0004", "storeId": "test-store-id",
                  "issuedAt": "2026-10-31T05:00:00Z" }
                """;

        // when
        PortOneBillingKeyInfo info = PortOneBillingKeyInfo.from(
            mapper.readValue(json, PortOneBillingKeyInfoResponse.class));

        // then
        assertThat(info.channels()).isEmpty();
        assertThat(info.channelOf("test-channel-key")).isEmpty();
        assertThat(info.customerId()).isNull();
        assertThat(info.issueId()).isNull();
        assertThat(info.cardMethod()).isFalse();
    }

    @Test
    @DisplayName("채널 type이 비어 있어도 읽힌다 — 검증기가 TEST가 아니라고 거절할 몫이다")
    void channelWithoutType() throws Exception {
        // given
        String json = """
                { "status": "ISSUED", "billingKey": "billing-key-test-0005", "storeId": "test-store-id",
                  "channels": [ { "id": "ch-test", "key": "test-channel-key" } ],
                  "issuedAt": "2026-10-31T05:00:00Z" }
                """;

        // when
        PortOneBillingKeyInfo info = PortOneBillingKeyInfo.from(
            mapper.readValue(json, PortOneBillingKeyInfoResponse.class));

        // then
        assertThat(info.channelOf("test-channel-key")).contains(
            new PortOneBillingKeyInfo.Channel("test-channel-key", null));
    }
}
