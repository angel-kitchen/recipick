package com.recipick.domain.recipe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "auto_cart_request",
        indexes = {
                @Index(name = "idx_auto_cart_request_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_auto_cart_request_member_id_created_at", columnList = "member_id, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoCartRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private Long martId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private AutoCartStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<AutoCartMenu> menus;

    private Long totalPrice;

    private LocalDateTime confirmedAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AutoCartRequest(Long memberId, Long martId, List<AutoCartMenu> menus, Long totalPrice) {
        this.memberId = memberId;
        this.martId = martId;
        this.status = AutoCartStatus.PREVIEWED;
        this.menus = menus;
        this.totalPrice = totalPrice;
    }

    public boolean isConfirmed() {
        return status == AutoCartStatus.CONFIRMED;
    }
}
