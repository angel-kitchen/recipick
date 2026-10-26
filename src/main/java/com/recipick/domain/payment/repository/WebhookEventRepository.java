package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.WebhookEvent;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    // 수신 Facade의 중복 판정 보조. webhook_id는 UNIQUE라 인덱스로 바로 찾는다
    boolean existsByWebhookId(String webhookId);

    // Worker의 claim 전용. 같은 이벤트를 두 Worker가 동시에 잡지 않게 행을 잠근 뒤 상태를 다시 본다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM WebhookEvent e WHERE e.id = :id")
    Optional<WebhookEvent> findByIdForUpdate(@Param("id") Long id);

    // Fallback Sweep용. 큐에서 사라졌을 수 있는 미완료 이벤트의 ID만 읽는다. 상태마다 정상이라면 이미 실행됐어야 할 시각을
    // 넘긴 행만 고르고, id 기준으로 이어 읽어(afterId) 처리 중 행이 바뀌어도 건너뛰지 않는다
    @Query("""
        SELECT e.id
            FROM WebhookEvent e
            WHERE e.id > :afterId
              AND ((e.status = com.recipick.domain.payment.entity.enums.WebhookEventStatus.RECEIVED
                      AND e.updatedAt <= :receivedBefore)
                OR (e.status = com.recipick.domain.payment.entity.enums.WebhookEventStatus.RETRY_PENDING
                      AND e.updatedAt <= :retryBefore)
                OR (e.status = com.recipick.domain.payment.entity.enums.WebhookEventStatus.PROCESSING
                      AND e.updatedAt <= :processingBefore))
            ORDER BY e.id
            """)
    List<Long> findRecoverableIds(
        @Param("afterId") Long afterId,
        @Param("receivedBefore") LocalDateTime receivedBefore,
        @Param("retryBefore") LocalDateTime retryBefore,
        @Param("processingBefore") LocalDateTime processingBefore,
        Pageable pageable);
}
