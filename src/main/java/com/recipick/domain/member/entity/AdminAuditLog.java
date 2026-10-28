package com.recipick.domain.member.entity;

import com.recipick.domain.product.entity.Mart;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Immutable
@EntityListeners(AuditingEntityListener.class)
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_admin_audit_log_created_at", columnList = "created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false, updatable = false)
    private Admin admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(64)")
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(32)")
    private AdminAuditTarget targetType;

    @Column(updatable = false)
    private Long targetId;

    @Column(name = "mart_id", updatable = false)
    private Long martId;

    // 도메인 조회는 ID 계약을 유지하고, 이 읽기 전용 연관관계는 마트 FK 생성에만 사용한다.
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mart_id", insertable = false, updatable = false)
    private Mart martReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", updatable = false)
    private Map<String, Map<String, Object>> details;

    @Column(length = 45, updatable = false)
    private String ipAddress;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AdminAuditLog create(Admin admin, AdminAuditCommand command, String ipAddress) {
        AdminAuditLog log = new AdminAuditLog();
        log.admin = admin;
        log.action = command.action();
        log.targetType = command.action().target();
        log.targetId = command.targetId();
        log.martId = command.martId();
        log.details = command.details();
        log.ipAddress = ipAddress;
        return log;
    }

    public Map<String, Map<String, Object>> getDetails() {
        if (details == null) {
            return null;
        }
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        details.forEach((key, values) -> copy.put(key,
                Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public String toString() {
        return "AdminAuditLog[id=" + id + "]";
    }
}
