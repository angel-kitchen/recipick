package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
