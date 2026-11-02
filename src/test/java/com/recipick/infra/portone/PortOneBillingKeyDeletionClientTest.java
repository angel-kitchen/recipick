package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.integration.BillingKeyDeletionStubServer;
import com.recipick.domain.payment.integration.BillingKeyDeletionStubServer.Entry;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionCode;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation.Kind;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class PortOneBillingKeyDeletionClientTest {
    private final BillingKeyDeletionStubServer server = new BillingKeyDeletionStubServer();
    private final JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
    private final PortOneBillingKeyDeletionClient client = new PortOneBillingKeyDeletionClient(
        RestClient.builder().requestFactory(factory).build(), server.baseUrl());

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    @DisplayName("키를 경로 한 조각으로 인코딩하고 상점·요청 주체·사유를 쿼리로 보낸다")
    void queriesExactStoreAndEncodesOpaqueKeyAsOnePathSegment() {
        // given
        String key = "test/key +?&";
        server.register(key);

        // when, then
        assertThat(client.lookup(key, BillingKeyDeletionStubServer.STORE).kind()).isEqualTo(Kind.ISSUED);
        assertThat(server.lastRawPath()).endsWith("test%2Fkey%20%2B%3F%26");
        assertThat(server.lastRawQuery()).contains("storeId=" + BillingKeyDeletionStubServer.STORE);
        assertThat(client.delete(key, BillingKeyDeletionStubServer.STORE, "RECIPICK_WITHDRAWAL", "CUSTOMER"))
            .isEqualTo(PortOneBillingKeyDeletionCode.DELETE_ACCEPTED);
        assertThat(server.lastRawQuery()).contains("requester=CUSTOMER").contains("reason=RECIPICK_WITHDRAWAL");
        assertThat(client.lookup(key, BillingKeyDeletionStubServer.STORE).kind()).isEqualTo(Kind.ABSENT);
    }

    @Test
    @DisplayName("실연동 채널이나 다른 키·상점 응답은 운영 확인으로 분류한다")
    void rejectsLiveOrMismatchedKeysBeforeCallerCanDelete() {
        // given
        Entry entry = server.register("key");

        // when, then
        entry.channelType = "LIVE";
        assertThat(client.lookup("key", BillingKeyDeletionStubServer.STORE).code())
            .isEqualTo(PortOneBillingKeyDeletionCode.CHANNEL_NOT_TEST);
        entry.channelType = "TEST";
        entry.responseKey = "another-key";
        assertThat(client.lookup("key", BillingKeyDeletionStubServer.STORE).code())
            .isEqualTo(PortOneBillingKeyDeletionCode.IDENTITY_MISMATCH);
        assertThat(server.deleteCount()).isZero();
    }

    @Test
    @DisplayName("정확한 404·인증 실패·해석 불가 본문을 구분한다")
    void distinguishesExactNotFoundAuthenticationFailureAndMalformedBody() {
        // given
        Entry entry = server.register("key");

        // when, then
        entry.getHttp = 404;
        entry.errorType = "NOT_FOUND";
        assertThat(client.lookup("key", BillingKeyDeletionStubServer.STORE).kind()).isEqualTo(Kind.UNKNOWN);
        entry.errorType = "BILLING_KEY_NOT_FOUND";
        assertThat(client.lookup("key", BillingKeyDeletionStubServer.STORE).kind()).isEqualTo(Kind.ABSENT);
        entry.getHttp = 403;
        assertThat(client.lookup("key", BillingKeyDeletionStubServer.STORE).code())
            .isEqualTo(PortOneBillingKeyDeletionCode.AUTH_ERROR);
        entry.getHttp = 200;
        entry.malformedGet = true;
        assertThat(client.lookup("key", BillingKeyDeletionStubServer.STORE).kind()).isEqualTo(Kind.UNKNOWN);
    }

    @Test
    @DisplayName("DELETE 응답 시간 초과는 성공도 실패도 아니며 뒤이은 조회로 확인한다")
    void timeoutIsNotDeletionFailureOrSuccess() {
        // given
        factory.setReadTimeout(Duration.ofMillis(100));
        server.register("key");
        server.holdDeleteReply();

        // when, then
        assertThat(client.delete("key", BillingKeyDeletionStubServer.STORE, "RECIPICK_REPLACED_KEY", "ADMIN"))
            .isEqualTo(PortOneBillingKeyDeletionCode.TIMEOUT);
        server.releaseDeleteReply();
        assertThat(client.lookup("key", BillingKeyDeletionStubServer.STORE).kind()).isEqualTo(Kind.ABSENT);
    }
}
