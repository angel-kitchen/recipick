package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 서비스의 트랜잭션 경계를 보는 테스트라 클래스에 @Transactional을 걸지 않는다 — claim이 자기 TX에서 커밋돼야
// 호출자가 잠금 없이 PortOne을 기다릴 수 있다. 만든 행은 테스트가 끝날 때 직접 지운다
class WebhookEventServiceTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    @Autowired
    private WebhookEventService webhookEventService;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private Clock clock;

    private final List<Long> savedIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        webhookEventRepository.deleteAllById(savedIds);
    }

    @Test
    @DisplayName("RECEIVED 이벤트를 잡으면 PROCESSING·시도 1회로 커밋되고 잡은 이벤트를 돌려준다")
    void claimReceived() {
        // given
        WebhookEvent saved = save(received("wh_svc_claim_1"));

        // when
        Optional<WebhookEvent> claimed = webhookEventService.claim(saved.getId(), LocalDateTime.now(clock), TIMEOUT);

        // then — 트랜잭션 밖에서 다시 읽어도 PROCESSING이다. claim의 TX가 커밋됐고 잠금도 풀렸다는 뜻
        assertThat(claimed).isPresent();
        assertThat(claimed.get().getId()).isEqualTo(saved.getId());
        WebhookEvent reloaded = reload(saved.getId());
        assertThat(reloaded.getStatus()).isEqualTo(WebhookEventStatus.PROCESSING);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("방금 잡은 PROCESSING은 다시 잡히지 않는다 — 큐에 같은 작업이 두 번 있어도 반영은 한 번")
    void claimSkipsFreshProcessing() {
        // given
        WebhookEvent saved = save(received("wh_svc_claim_2"));
        webhookEventService.claim(saved.getId(), LocalDateTime.now(clock), TIMEOUT);

        // when — Auditing이 적은 updated_at 직후 시각으로 다시 잡아 본다. JVM 시간대에 기대지 않도록 행의 시각을 기준으로 한다
        LocalDateTime justAfter = reload(saved.getId()).getUpdatedAt().plusSeconds(1);
        Optional<WebhookEvent> again = webhookEventService.claim(saved.getId(), justAfter, TIMEOUT);

        // then
        assertThat(again).isEmpty();
        assertThat(reload(saved.getId()).getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("updated_at이 timeout만큼 지난 PROCESSING은 죽은 Worker의 것으로 보고 다시 잡는다")
    void claimReclaimsStaleProcessing() {
        // given
        WebhookEvent saved = save(received("wh_svc_claim_3"));
        webhookEventService.claim(saved.getId(), LocalDateTime.now(clock), TIMEOUT);

        // when
        LocalDateTime timedOut = reload(saved.getId()).getUpdatedAt().plus(TIMEOUT);
        Optional<WebhookEvent> reclaimed = webhookEventService.claim(saved.getId(), timedOut, TIMEOUT);

        // then
        assertThat(reclaimed).isPresent();
        WebhookEvent reloaded = reload(saved.getId());
        assertThat(reloaded.getStatus()).isEqualTo(WebhookEventStatus.PROCESSING);
        assertThat(reloaded.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("끝난 이벤트(IGNORED 등)는 잡지 않고 그대로 둔다")
    void claimSkipsFinished() {
        // given
        WebhookEvent saved = save(WebhookEvent.ignored(
            "wh_svc_claim_4", "BillingKey.Issued", "pay_svc_1", "{}", "처리하지 않는 type", LocalDateTime.now(clock)));

        // when
        Optional<WebhookEvent> claimed = webhookEventService.claim(saved.getId(), LocalDateTime.now(clock), TIMEOUT);

        // then
        assertThat(claimed).isEmpty();
        assertThat(reload(saved.getId()).getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
    }

    @Test
    @DisplayName("없는 이벤트는 빈 값이다 — 큐에 남은 옛 작업이 예외가 되지 않는다")
    void claimMissing() {
        // when
        Optional<WebhookEvent> claimed = webhookEventService.claim(Long.MAX_VALUE, LocalDateTime.now(clock), TIMEOUT);

        // then
        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("처리 성공은 PROCESSED·finished_at을 적고 사유를 비운다")
    void markProcessed() {
        // given
        WebhookEvent saved = save(received("wh_svc_mark_1"));
        webhookEventService.claim(saved.getId(), LocalDateTime.now(clock), TIMEOUT);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 26, 13, 0);

        // when
        boolean recorded = webhookEventService.markProcessed(saved.getId(), 1, finishedAt);

        // then
        assertThat(recorded).isTrue();
        WebhookEvent reloaded = reload(saved.getId());
        assertThat(reloaded.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(reloaded.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(reloaded.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("재시도 대기는 사유만 적고 finished_at은 비워 둔다 — 아직 끝난 게 아니다")
    void markRetryPending() {
        // given
        WebhookEvent saved = save(received("wh_svc_mark_2"));
        webhookEventService.claim(saved.getId(), LocalDateTime.now(clock), TIMEOUT);

        // when
        boolean recorded = webhookEventService.markRetryPending(saved.getId(), 1, "PortOne 조회 실패: COMMON-009");

        // then
        assertThat(recorded).isTrue();
        WebhookEvent reloaded = reload(saved.getId());
        assertThat(reloaded.getStatus()).isEqualTo(WebhookEventStatus.RETRY_PENDING);
        assertThat(reloaded.getFailureReason()).isEqualTo("PortOne 조회 실패: COMMON-009");
        assertThat(reloaded.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("수동 확인은 MANUAL_REVIEW·사유·finished_at을 적는다")
    void markManualReview() {
        // given
        WebhookEvent saved = save(received("wh_svc_mark_3"));
        webhookEventService.claim(saved.getId(), LocalDateTime.now(clock), TIMEOUT);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 26, 13, 5);

        // when
        boolean recorded = webhookEventService.markManualReview(saved.getId(), 1, "부분 취소는 자동 반영하지 않음", finishedAt);

        // then
        assertThat(recorded).isTrue();
        WebhookEvent reloaded = reload(saved.getId());
        assertThat(reloaded.getStatus()).isEqualTo(WebhookEventStatus.MANUAL_REVIEW);
        assertThat(reloaded.getFailureReason()).isEqualTo("부분 취소는 자동 반영하지 않음");
        assertThat(reloaded.getFinishedAt()).isEqualTo(finishedAt);
    }

    @Test
    @DisplayName("처리권이 다른 Worker로 넘어간 뒤 옛 Worker의 결과는 거절된다 — 새 Worker의 PROCESSING을 덮어쓰지 않는다")
    void rejectResultFromStaleWorker() {
        // given — Worker A가 잡고(1번) 멈춘 사이 timeout이 지나 Worker B가 다시 잡았다(2번)
        WebhookEvent saved = save(received("wh_svc_fence_1"));
        webhookEventService.claim(saved.getId(), LocalDateTime.now(clock), TIMEOUT);
        LocalDateTime timedOut = reload(saved.getId()).getUpdatedAt().plus(TIMEOUT);
        webhookEventService.claim(saved.getId(), timedOut, TIMEOUT);

        // when — 뒤늦게 돌아온 A가 1번 처리권으로 기록을 시도한다
        boolean recordedByA = webhookEventService.markProcessed(saved.getId(), 1, timedOut);

        // then — 거절되고 B의 처리 상태가 그대로다
        assertThat(recordedByA).isFalse();
        WebhookEvent afterA = reload(saved.getId());
        assertThat(afterA.getStatus()).isEqualTo(WebhookEventStatus.PROCESSING);
        assertThat(afterA.getAttemptCount()).isEqualTo(2);

        // when — B는 2번 처리권으로 정상 기록한다
        boolean recordedByB = webhookEventService.markRetryPending(saved.getId(), 2, "PortOne 조회 실패: COMMON-008");

        // then
        assertThat(recordedByB).isTrue();
        assertThat(reload(saved.getId()).getStatus()).isEqualTo(WebhookEventStatus.RETRY_PENDING);
    }

    @Test
    @DisplayName("잡지 않은 이벤트(RECEIVED)에는 결과가 기록되지 않는다")
    void rejectMarkWithoutClaim() {
        // given
        WebhookEvent saved = save(received("wh_svc_mark_4"));

        // when
        boolean recorded = webhookEventService.markProcessed(saved.getId(), 0, LocalDateTime.now(clock));

        // then
        assertThat(recorded).isFalse();
        assertThat(reload(saved.getId()).getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
    }

    private static WebhookEvent received(String webhookId) {
        return WebhookEvent.received(webhookId, "Transaction.Paid", "pay_svc_1", "{\"type\":\"Transaction.Paid\"}");
    }

    private WebhookEvent save(WebhookEvent event) {
        WebhookEvent saved = webhookEventRepository.save(event);
        savedIds.add(saved.getId());
        return saved;
    }

    private WebhookEvent reload(Long id) {
        return webhookEventRepository.findById(id).orElseThrow();
    }
}
