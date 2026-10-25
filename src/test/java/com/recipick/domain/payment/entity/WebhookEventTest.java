package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class WebhookEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 15, 0);
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    @Test
    @DisplayName("수신한 웹훅은 RECEIVED이고 시도 횟수 0, 종료 시각 없음이다")
    void receivedIsNotStarted() {
        // when
        WebhookEvent event = WebhookEvent.received("wh_1", "Transaction.Paid", "pay_1", "{}");

        // then
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getFinishedAt()).isNull();
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getPayload()).isEqualTo("{}");
    }

    @Test
    @DisplayName("수신 시점에 무시한 웹훅은 사유와 종료 시각을 갖고 다시 잡히지 않는다")
    void ignoredAtReceiptIsFinished() {
        // when
        WebhookEvent event = WebhookEvent.ignored("wh_1", "BillingKey.Issued", null, "{}", "처리 대상 아님", NOW);

        // then
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(event.getFailureReason()).isEqualTo("처리 대상 아님");
        assertThat(event.getFinishedAt()).isEqualTo(NOW);
        assertThat(event.claim(NOW, TIMEOUT)).isFalse();
    }

    @Test
    @DisplayName("RECEIVED를 잡으면 PROCESSING이 되고 시도 횟수가 1 오른다")
    void claimReceived() {
        // given
        WebhookEvent event = received();

        // when
        boolean claimed = event.claim(NOW, TIMEOUT);

        // then
        assertThat(claimed).isTrue();
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.PROCESSING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("RETRY_PENDING도 다시 잡히고 시도 횟수가 누적된다")
    void claimRetryPending() {
        // given
        WebhookEvent event = received();
        event.claim(NOW, TIMEOUT);
        event.markRetryPending("PortOne 503");

        // when
        boolean claimed = event.claim(NOW, TIMEOUT);

        // then
        assertThat(claimed).isTrue();
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.PROCESSING);
        assertThat(event.getAttemptCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = WebhookEventStatus.class, names = {"PROCESSED", "IGNORED", "MANUAL_REVIEW"})
    @DisplayName("끝난 이벤트는 큐에 다시 와도 잡히지 않고 아무것도 바뀌지 않는다")
    void finishedIsNotClaimed(WebhookEventStatus finished) {
        // given
        WebhookEvent event = eventIn(finished);
        int attempts = event.getAttemptCount();

        // when
        boolean claimed = event.claim(NOW, TIMEOUT);

        // then
        assertThat(claimed).isFalse();
        assertThat(event.getStatus()).isEqualTo(finished);
        assertThat(event.getAttemptCount()).isEqualTo(attempts);
    }

    @Test
    @DisplayName("다른 Worker가 처리 중(updated_at이 timeout 안)이면 잡지 않는다")
    void skipProcessingWithinTimeout() {
        // given
        WebhookEvent event = received();
        event.claim(NOW, TIMEOUT);
        touch(event, NOW.minusMinutes(5));

        // when
        boolean claimed = event.claim(NOW, TIMEOUT);

        // then
        assertThat(claimed).isFalse();
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PROCESSING인데 updated_at이 timeout 이상 지났으면 멈춘 것으로 보고 다시 잡는다")
    void reclaimStaleProcessing() {
        // given
        WebhookEvent event = received();
        event.claim(NOW, TIMEOUT);
        touch(event, NOW.minusMinutes(10));

        // when
        boolean claimed = event.claim(NOW, TIMEOUT);

        // then
        assertThat(claimed).isTrue();
        assertThat(event.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("처리 성공은 PROCESSED로 끝내고 이전 재시도 사유를 비운다")
    void markProcessedClearsReason() {
        // given
        WebhookEvent event = received();
        event.claim(NOW, TIMEOUT);
        event.markRetryPending("PortOne 503");
        event.claim(NOW, TIMEOUT);

        // when
        event.markProcessed(NOW);

        // then
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getFinishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("재시도 대기는 사유만 남기고 종료 시각은 두지 않는다 — 다음 실행 시각은 큐가 갖는다")
    void markRetryPendingIsNotFinished() {
        // given
        WebhookEvent event = received();
        event.claim(NOW, TIMEOUT);

        // when
        event.markRetryPending("PortOne 503");

        // then
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.RETRY_PENDING);
        assertThat(event.getFailureReason()).isEqualTo("PortOne 503");
        assertThat(event.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("처리 중 무시와 수동 확인은 사유와 종료 시각을 함께 남긴다")
    void markIgnoredAndManualReviewAreFinished() {
        // given
        WebhookEvent ignored = received();
        ignored.claim(NOW, TIMEOUT);
        WebhookEvent manual = received();
        manual.claim(NOW, TIMEOUT);

        // when
        ignored.markIgnored("다른 storeId", NOW);
        manual.markManualReview("부분 취소", NOW);

        // then
        assertThat(ignored.getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(ignored.getFailureReason()).isEqualTo("다른 storeId");
        assertThat(ignored.getFinishedAt()).isEqualTo(NOW);
        assertThat(manual.getStatus()).isEqualTo(WebhookEventStatus.MANUAL_REVIEW);
        assertThat(manual.getFailureReason()).isEqualTo("부분 취소");
        assertThat(manual.getFinishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("잡지 않은 이벤트에 결과를 쓰면 내부 오류다 — 다른 Worker의 처리와 섞인다")
    void resultBeforeClaimFails() {
        // given
        WebhookEvent event = received();

        // when, then
        assertThatThrownBy(() -> event.markProcessed(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> event.markIgnored("r", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> event.markRetryPending("r")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> event.markManualReview("r", NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
    }

    @Test
    @DisplayName("사유가 500자를 넘으면 컬럼 길이에 맞춰 자른다")
    void truncateReason() {
        // given
        WebhookEvent event = received();
        event.claim(NOW, TIMEOUT);

        // when
        event.markRetryPending("x".repeat(600));

        // then
        assertThat(event.getFailureReason()).hasSize(500);
    }

    private static WebhookEvent received() {
        return WebhookEvent.received("wh_1", "Transaction.Paid", "pay_1", "{}");
    }

    private static WebhookEvent eventIn(WebhookEventStatus finished) {
        if (finished == WebhookEventStatus.IGNORED) {
            return WebhookEvent.ignored("wh_1", "BillingKey.Issued", null, "{}", "처리 대상 아님", NOW);
        }
        WebhookEvent event = received();
        event.claim(NOW, TIMEOUT);
        if (finished == WebhookEventStatus.PROCESSED) {
            event.markProcessed(NOW);
        } else {
            event.markManualReview("부분 취소", NOW);
        }
        return event;
    }

    // updated_at은 JPA Auditing이 채운다. 단위 테스트에는 Auditing이 없어 직접 넣는다
    private static void touch(WebhookEvent event, LocalDateTime updatedAt) {
        ReflectionTestUtils.setField(event, "updatedAt", updatedAt);
    }
}
