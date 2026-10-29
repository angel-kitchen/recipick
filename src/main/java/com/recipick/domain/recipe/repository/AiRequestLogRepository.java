package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AutoCartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {

    @Modifying
    @Query("UPDATE AiRequestLog l SET l.autoCartRequest = null WHERE l.autoCartRequest.id IN "
        + "(SELECT r.id FROM AutoCartRequest r WHERE r.status = :status AND r.createdAt < :before)")
    int clearAutoCartRequest(@Param("status")AutoCartStatus status, @Param("before") LocalDateTime before);
}
