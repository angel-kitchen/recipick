package com.recipick.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "auto_cart_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoCartRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private Long martId;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "json")
    private String menus;

    private Long totalPrice;

    private LocalDateTime confirmedAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AutoCartRequest(Long memberId, Long martId, String status, String menus, Long totalPrice) {
        this.memberId = memberId;
        this.martId = martId;
        this.status = status;
        this.menus = menus;
        this.totalPrice = totalPrice;
    }
}
