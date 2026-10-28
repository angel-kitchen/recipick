package com.recipick.domain.member.repository;

import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditTarget;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    @Query("""
            SELECT l FROM AdminAuditLog l
            WHERE (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt < :to)
              AND (:adminId IS NULL OR l.admin.id = :adminId)
              AND (:action IS NULL OR l.action = :action)
              AND (:targetType IS NULL OR l.targetType = :targetType)
            """)
    Page<AdminAuditLog> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("adminId") Long adminId, @Param("action") AdminAuditAction action,
            @Param("targetType") AdminAuditTarget targetType, Pageable pageable);

}
