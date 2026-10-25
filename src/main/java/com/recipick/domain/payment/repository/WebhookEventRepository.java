package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.WebhookEvent;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    // Worker의 claim 전용. 같은 이벤트를 두 Worker가 동시에 잡지 않게 행을 잠근 뒤 상태를 다시 본다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM WebhookEvent e WHERE e.id = :id")
    Optional<WebhookEvent> findByIdForUpdate(@Param("id") Long id);
}
