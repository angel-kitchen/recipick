package com.recipick.domain.recipe.entity;

import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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

    public void validateConfirmable() {
        if (status != AutoCartStatus.PREVIEWED) {
            throw new BusinessException(RecipeErrorCode.AUTO_CART_INVALID_STATUS);
        }
    }

    // 확정 뒤 기록 화면은 확정 시점 합계를 보여줘야 하므로 미리보기 합계를 확정 합계로 바꾼다
    public void confirm(long totalPrice, LocalDateTime confirmedAt) {
        validateConfirmable();
        this.status = AutoCartStatus.CONFIRMED;
        this.totalPrice = totalPrice;
        this.confirmedAt = confirmedAt;
    }
}
