package com.recipick.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.infra.portone.PortOneWebhookProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 실제 서명 → 보안 필터 → Controller → Facade → MySQL까지 한 줄로 확인한다. 트랜잭션을 걸지 않는다 —
// UNIQUE 충돌 뒤에도 같은 세션을 계속 쓰면 결과가 흐려져서 각 테스트가 자기 행을 지운다
class PortOneWebhookIntegrationTest extends IntegrationTestSupport {

    private static final String WEBHOOK_ID_PREFIX = "wh_it_";
    private static final String STORE_ID = "test-store-id";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private PortOneWebhookProperties webhookProperties;

    @Autowired
    private StringRedisTemplate redis;

    // 이 테스트가 만든 행과 그 행의 큐 작업만 지운다. 큐 키 전체를 지우면 다른 테스트의 작업까지 사라진다
    @AfterEach
    void tearDown() {
        List<WebhookEvent> rows = webhookEventRepository.findAll().stream()
            .filter(event -> event.getWebhookId().startsWith(WEBHOOK_ID_PREFIX))
            .toList();
        rows.forEach(event -> redis.opsForZSet()
            .remove(PaymentDelayQueue.QUEUE_KEY, PaymentDelayTask.webhookProcess(event.getId()).member()));
        webhookEventRepository.deleteAll(rows);
    }

    @Test
    @DisplayName("서명이 맞는 Transaction.Paid는 200이고 원문 그대로 RECEIVED 한 행이 남으며, 큐에 지금 실행할 작업이 등록된다")
    void receiveAndStore() throws Exception {
        // given
        String webhookId = WEBHOOK_ID_PREFIX + "paid";
        String body = body("Transaction.Paid", STORE_ID, "pay_it_1");

        // when
        ResultActions result = send(webhookId, body, body);

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        List<WebhookEvent> rows = rowsOf(webhookId);
        assertThat(rows).hasSize(1);
        WebhookEvent saved = rows.get(0);
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(saved.getType()).isEqualTo("Transaction.Paid");
        assertThat(saved.getPortonePaymentId()).isEqualTo("pay_it_1");
        assertThat(saved.getPayload()).isEqualTo(body);
        assertThat(saved.getAttemptCount()).isZero();
        assertThat(queueScoreOf(saved)).isNotNull();
    }

    @Test
    @DisplayName("같은 webhook_id가 다시 와도 200이고 행은 하나이며, 미뤄 둔 재시도 시각을 앞당기지 않는다")
    void duplicateIsAcceptedOnce() throws Exception {
        // given — 첫 수신 뒤 Worker가 실패해 10분 뒤로 미뤄 둔 상황
        String webhookId = WEBHOOK_ID_PREFIX + "dup";
        String body = body("Transaction.Paid", STORE_ID, "pay_it_2");
        send(webhookId, body, body).andExpect(status().isOk());
        WebhookEvent saved = rowsOf(webhookId).get(0);
        long retryAt = Instant.now().plusSeconds(600).toEpochMilli();
        redis.opsForZSet().add(
            PaymentDelayQueue.QUEUE_KEY, PaymentDelayTask.webhookProcess(saved.getId()).member(), retryAt);

        // when
        ResultActions result = send(webhookId, body, body);

        // then — 재전송은 재처리 신호가 아니다. 백오프는 우리 재시도 정책이 정한다
        result.andExpect(status().isOk());
        assertThat(rowsOf(webhookId)).hasSize(1);
        assertThat(queueScoreOf(saved)).isEqualTo((double) retryAt);
    }

    @Test
    @DisplayName("서명은 다른 본문으로 만들어졌으면 401 AUTH-010이고 행이 생기지 않는다")
    void rejectTamperedBody() throws Exception {
        // given
        String webhookId = WEBHOOK_ID_PREFIX + "tampered";
        String signedBody = body("Transaction.Paid", STORE_ID, "pay_it_3");
        String sentBody = body("Transaction.Paid", STORE_ID, "pay_it_999");

        // when
        ResultActions result = send(webhookId, signedBody, sentBody);

        // then
        result.andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-010"));
        assertThat(rowsOf(webhookId)).isEmpty();
    }

    @Test
    @DisplayName("처리하지 않는 type은 200이고 IGNORED로 끝난 행이 남는다")
    void ignoreUnknownType() throws Exception {
        // given
        String webhookId = WEBHOOK_ID_PREFIX + "billing";
        String body = "{\"type\":\"BillingKey.Issued\",\"timestamp\":\"2026-10-25T12:00:00Z\","
            + "\"data\":{\"storeId\":\"" + STORE_ID + "\",\"billingKey\":\"billing-key-1\"}}";

        // when
        ResultActions result = send(webhookId, body, body);

        // then
        result.andExpect(status().isOk());
        WebhookEvent saved = rowsOf(webhookId).get(0);
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(saved.getFinishedAt()).isNotNull();
        assertThat(saved.getFailureReason()).contains("BillingKey.Issued");
        assertThat(queueScoreOf(saved)).isNull();
    }

    @Test
    @DisplayName("다른 storeId의 웹훅은 200이고 IGNORED로 남는다")
    void ignoreOtherStore() throws Exception {
        // given
        String webhookId = WEBHOOK_ID_PREFIX + "store";
        String body = body("Transaction.Paid", "store-other", "pay_it_4");

        // when
        ResultActions result = send(webhookId, body, body);

        // then
        result.andExpect(status().isOk());
        assertThat(rowsOf(webhookId).get(0).getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
    }

    // 서명은 signedBody로 만들고 전송은 sentBody로 한다. 둘이 같으면 정상 요청, 다르면 변조 요청이다
    private ResultActions send(String webhookId, String signedBody, String sentBody) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        return mockMvc.perform(post("/api/webhooks/portone")
            .header("webhook-id", webhookId)
            .header("webhook-timestamp", timestamp)
            .header("webhook-signature", sign(webhookId, timestamp, signedBody))
            .contentType(MediaType.APPLICATION_JSON)
            .content(sentBody));
    }

    // 검증기와 같은 규칙(Standard Webhooks)으로 테스트 쪽에서 서명한다. 키는 application-test.yml의 공개 예제 키다
    private String sign(String webhookId, String timestamp, String body) throws Exception {
        String secret = webhookProperties.secret();
        String encodedKey = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(encodedKey), HMAC_ALGORITHM));
        byte[] signature = mac.doFinal((webhookId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(signature);
    }

    // 큐에 있으면 실행 시각(score), 없으면 null
    private Double queueScoreOf(WebhookEvent event) {
        return redis.opsForZSet()
            .score(PaymentDelayQueue.QUEUE_KEY, PaymentDelayTask.webhookProcess(event.getId()).member());
    }

    private List<WebhookEvent> rowsOf(String webhookId) {
        return webhookEventRepository.findAll().stream()
            .filter(event -> webhookId.equals(event.getWebhookId()))
            .toList();
    }

    private static String body(String type, String storeId, String paymentId) {
        return "{\"type\":\"" + type + "\",\"timestamp\":\"2026-10-25T12:00:00Z\","
            + "\"data\":{\"storeId\":\"" + storeId + "\",\"paymentId\":\"" + paymentId + "\","
            + "\"transactionId\":\"tx_it\"}}";
    }
}
