package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;

    // flush까지 해서 webhook_id UNIQUE 충돌이 이 메서드 안에서 터지게 한다. 커밋 시점에 터지면 Facade가 어느 저장이 실패했는지 모른다.
    // 충돌 판정은 트랜잭션 밖(Facade)에서 한다 — 안에서 잡으면 rollback-only로 남아 커밋이 실패한다
    @Transactional
    public WebhookEvent save(WebhookEvent event) {
        return webhookEventRepository.saveAndFlush(event);
    }

    // TX-A. 행을 잠근 뒤 처리 가능한 상태인지 다시 보고 PROCESSING으로 바꾼다. 잡지 못하면 빈 값(끝났거나 처리 중이거나 없음).
    // 커밋과 함께 잠금이 풀리므로 호출자는 이 뒤에 PortOne을 기다려도 다른 요청을 막지 않는다.
    // 돌려준 이벤트의 attemptCount가 이 Worker의 처리권 번호다 — 결과를 기록할 때 그대로 넘긴다
    @Transactional
    public Optional<WebhookEvent> claim(Long webhookEventId, LocalDateTime now, Duration processingTimeout) {
        return webhookEventRepository.findByIdForUpdate(webhookEventId)
            .filter(event -> event.claim(now, processingTimeout));
    }

    // TX-C. 아래 넷은 처리권이 아직 내 것일 때만 기록하고 true를 돌려준다. false면 내가 멈춘 사이 stale로 판정돼
    // 다른 Worker가 다시 잡은 것이다 — 그쪽 결과를 덮어쓰지 않는다
    @Transactional
    public boolean markProcessed(Long webhookEventId, int expectedAttemptCount, LocalDateTime now) {
        Optional<WebhookEvent> owned = findOwnedForUpdate(webhookEventId, expectedAttemptCount);
        owned.ifPresent(event -> event.markProcessed(now));
        return owned.isPresent();
    }

    @Transactional
    public boolean markIgnored(Long webhookEventId, int expectedAttemptCount, String reason, LocalDateTime now) {
        Optional<WebhookEvent> owned = findOwnedForUpdate(webhookEventId, expectedAttemptCount);
        owned.ifPresent(event -> event.markIgnored(reason, now));
        return owned.isPresent();
    }

    @Transactional
    public boolean markRetryPending(Long webhookEventId, int expectedAttemptCount, String reason) {
        Optional<WebhookEvent> owned = findOwnedForUpdate(webhookEventId, expectedAttemptCount);
        owned.ifPresent(event -> event.markRetryPending(reason));
        return owned.isPresent();
    }

    @Transactional
    public boolean markManualReview(Long webhookEventId, int expectedAttemptCount, String reason, LocalDateTime now) {
        Optional<WebhookEvent> owned = findOwnedForUpdate(webhookEventId, expectedAttemptCount);
        owned.ifPresent(event -> event.markManualReview(reason, now));
        return owned.isPresent();
    }

    // 잠금 아래에서 처리권을 확인한다. claim할 때마다 attemptCount가 1씩 오르므로 번호가 같으면 그 뒤 아무도 다시 잡지 않았다.
    // 행이 없으면 claim으로 잡은 행이 사라진 것이라 호출 순서 버그다
    private Optional<WebhookEvent> findOwnedForUpdate(Long webhookEventId, int expectedAttemptCount) {
        WebhookEvent event = webhookEventRepository.findByIdForUpdate(webhookEventId)
            .orElseThrow(() -> new IllegalStateException("웹훅 이벤트가 없습니다. webhookEventId=" + webhookEventId));
        if (event.getStatus() != WebhookEventStatus.PROCESSING || event.getAttemptCount() != expectedAttemptCount) {
            return Optional.empty();
        }
        return Optional.of(event);
    }
}
