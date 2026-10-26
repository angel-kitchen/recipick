package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WebhookEventRepositoryTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    @Autowired
    WebhookEventRepository webhookEventRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("수신한 웹훅을 저장하고 다시 읽으면 값과 생성 시각이 채워져 있다")
    void saveAndFind() {
        // given
        WebhookEvent saved = webhookEventRepository.save(
            WebhookEvent.received("wh_1", "Transaction.Paid", "pay_1", "{\"type\":\"Transaction.Paid\"}"));
        em.flush();
        em.clear();

        // when
        WebhookEvent found = webhookEventRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getWebhookId()).isEqualTo("wh_1");
        assertThat(found.getType()).isEqualTo("Transaction.Paid");
        assertThat(found.getPortonePaymentId()).isEqualTo("pay_1");
        assertThat(found.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(found.getPayload()).isEqualTo("{\"type\":\"Transaction.Paid\"}");
        assertThat(found.getAttemptCount()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 webhook_id를 두 번 저장하면 이름 붙인 UNIQUE 제약에 걸린다 — 수신 Facade가 이 이름으로 재전송을 판정한다")
    void webhookIdIsUnique() {
        // given
        webhookEventRepository.saveAndFlush(WebhookEvent.received("wh_dup", "Transaction.Paid", "pay_1", "{}"));

        // when
        Throwable thrown = catchThrowable(() ->
            webhookEventRepository.saveAndFlush(WebhookEvent.received("wh_dup", "Transaction.Paid", "pay_1", "{}")));

        // then — 메시지 문자열이 아니라 Hibernate가 뽑아 둔 제약 이름으로 판정한다. MySQL은 앞에 테이블명이 붙는다
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        ConstraintViolationException violation = findConstraintViolation(thrown);
        assertThat(violation).isNotNull();
        assertThat(violation.getConstraintName()).endsWith(WebhookEvent.WEBHOOK_ID_UNIQUE_CONSTRAINT);
    }

    @Test
    @DisplayName("status는 이름 문자열로 저장되고 컬럼은 varchar(20)이다")
    void statusStoredAsVarchar() {
        // given
        WebhookEvent saved = webhookEventRepository.save(
            WebhookEvent.ignored("wh_2", "BillingKey.Issued", null, "{}", "처리 대상 아님", LocalDateTime.now()));
        em.flush();

        // when
        Object status = em.createNativeQuery("SELECT status FROM webhook_event WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
        Object columnType = em.createNativeQuery(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'webhook_event' AND COLUMN_NAME = 'status'")
            .getSingleResult();

        // then
        assertThat(status).isEqualTo("IGNORED");
        assertThat(columnType).isEqualTo("varchar(20)");
    }

    @Test
    @DisplayName("payload 컬럼은 text다 — 웹훅 원문이 varchar(255)를 넘는다")
    void payloadColumnIsText() {
        // when
        Object columnType = em.createNativeQuery(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'webhook_event' AND COLUMN_NAME = 'payload'")
            .getSingleResult();

        // then
        assertThat(columnType).isEqualTo("text");
    }

    @Test
    @DisplayName("잠금 조회도 같은 이벤트를 돌려준다")
    void findByIdForUpdate() {
        // given
        WebhookEvent saved = webhookEventRepository.save(
            WebhookEvent.received("wh_3", "Transaction.Paid", "pay_3", "{}"));
        em.flush();
        em.clear();

        // when
        WebhookEvent found = webhookEventRepository.findByIdForUpdate(saved.getId()).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getWebhookId()).isEqualTo("wh_3");
    }

    @Test
    @DisplayName("방금 잡은 PROCESSING은 다시 잡히지 않고, updated_at이 timeout 이상 지나면 다시 잡힌다")
    void reclaimOnlyWhenStale() {
        // given — claim을 flush하면 Auditing이 updated_at을 지금으로 바꾼다
        WebhookEvent saved = webhookEventRepository.save(
            WebhookEvent.received("wh_4", "Transaction.Paid", "pay_4", "{}"));
        saved.claim(LocalDateTime.now(), TIMEOUT);
        em.flush();
        em.clear();

        // when — 아직 처리 중
        WebhookEvent fresh = webhookEventRepository.findById(saved.getId()).orElseThrow();
        boolean claimedFresh = fresh.claim(LocalDateTime.now(), TIMEOUT);

        // then
        assertThat(claimedFresh).isFalse();
        assertThat(fresh.getAttemptCount()).isEqualTo(1);

        // when — Worker가 죽어 timeout이 지난 상황을 updated_at으로 만든다
        em.clear();
        em.createNativeQuery("UPDATE webhook_event SET updated_at = :updatedAt WHERE id = :id")
            .setParameter("updatedAt", LocalDateTime.now().minus(TIMEOUT).minusMinutes(1))
            .setParameter("id", saved.getId())
            .executeUpdate();
        WebhookEvent stale = webhookEventRepository.findById(saved.getId()).orElseThrow();
        boolean claimedStale = stale.claim(LocalDateTime.now(), TIMEOUT);

        // then
        assertThat(claimedStale).isTrue();
        assertThat(stale.getStatus()).isEqualTo(WebhookEventStatus.PROCESSING);
        assertThat(stale.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Fallback용 조회는 상태별 기준 시각보다 오래된 RECEIVED·RETRY_PENDING·PROCESSING ID만 id 순으로 돌려준다")
    void findRecoverableIds() {
        // given — 기준: RECEIVED 11:59, RETRY_PENDING 11:50, PROCESSING 11:50
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 12, 0);
        LocalDateTime receivedBefore = now.minusMinutes(1);
        LocalDateTime retryBefore = now.minusMinutes(10);
        LocalDateTime processingBefore = now.minusMinutes(10);
        WebhookEvent receivedOld = saveWithUpdatedAt("wh_fb_1", WebhookEventStatus.RECEIVED, now.minusMinutes(2));
        saveWithUpdatedAt("wh_fb_2", WebhookEventStatus.RECEIVED, now.minusSeconds(30));
        WebhookEvent retryOld = saveWithUpdatedAt("wh_fb_3", WebhookEventStatus.RETRY_PENDING, now.minusMinutes(11));
        saveWithUpdatedAt("wh_fb_4", WebhookEventStatus.RETRY_PENDING, now.minusMinutes(5));
        WebhookEvent processingStale =
            saveWithUpdatedAt("wh_fb_5", WebhookEventStatus.PROCESSING, processingBefore);
        saveWithUpdatedAt("wh_fb_6", WebhookEventStatus.PROCESSING, now.minusMinutes(9));
        saveWithUpdatedAt("wh_fb_7", WebhookEventStatus.PROCESSED, now.minusHours(1));
        saveWithUpdatedAt("wh_fb_8", WebhookEventStatus.MANUAL_REVIEW, now.minusHours(1));
        saveWithUpdatedAt("wh_fb_9", WebhookEventStatus.IGNORED, now.minusHours(1));
        em.clear();
        // 앞선 테스트 클래스가 남긴 행과 섞이지 않게 이번에 만든 행부터 읽는다
        long afterId = receivedOld.getId() - 1;

        // when
        List<Long> all = webhookEventRepository.findRecoverableIds(
            afterId, receivedBefore, retryBefore, processingBefore, PageRequest.ofSize(100));
        List<Long> nextPage = webhookEventRepository.findRecoverableIds(
            receivedOld.getId(), receivedBefore, retryBefore, processingBefore, PageRequest.ofSize(1));

        // then — 끝난 상태(PROCESSED·MANUAL_REVIEW·IGNORED)와 아직 이른 행은 빠진다. 기준 시각과 같으면 포함한다
        assertThat(all).containsExactly(receivedOld.getId(), retryOld.getId(), processingStale.getId());
        assertThat(nextPage).containsExactly(retryOld.getId());
    }

    // 상태 전이를 실제 메서드로 거친 뒤 updated_at만 원하는 시각으로 바꾼다. 네이티브 UPDATE는 Auditing을 타지 않는다
    private WebhookEvent saveWithUpdatedAt(String webhookId, WebhookEventStatus status, LocalDateTime updatedAt) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 11, 0);
        WebhookEvent event = status == WebhookEventStatus.IGNORED
            ? WebhookEvent.ignored(webhookId, "BillingKey.Issued", "pay_fb", "{}", "처리하지 않는 type", now)
            : WebhookEvent.received(webhookId, "Transaction.Paid", "pay_fb", "{}");
        if (status != WebhookEventStatus.RECEIVED && status != WebhookEventStatus.IGNORED) {
            event.claim(now, TIMEOUT);
        }
        switch (status) {
            case RETRY_PENDING -> event.markRetryPending("PortOne 조회 실패: COMMON-008");
            case PROCESSED -> event.markProcessed(now);
            case MANUAL_REVIEW -> event.markManualReview("부분 취소는 자동 반영하지 않음", now);
            default -> { }
        }
        WebhookEvent saved = webhookEventRepository.saveAndFlush(event);
        em.createNativeQuery("UPDATE webhook_event SET updated_at = :updatedAt WHERE id = :id")
            .setParameter("updatedAt", updatedAt)
            .setParameter("id", saved.getId())
            .executeUpdate();
        return saved;
    }

    // Spring 예외 → Hibernate 예외 → JDBC 예외 순으로 감싸여 있다. 3단계 Facade가 같은 방식으로 찾는다
    private static ConstraintViolationException findConstraintViolation(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation;
            }
        }
        return null;
    }
}
