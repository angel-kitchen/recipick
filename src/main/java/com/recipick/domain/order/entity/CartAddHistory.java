package com.recipick.domain.order.entity;

import com.recipick.domain.order.entity.enums.CartAddSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "cart_add_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartAddHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private CartAddSource source;

    private Long autoCartRequestId;

    private int quantity;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private CartAddHistory(Long memberId, Long productId, CartAddSource source, Long autoCartRequestId, int quantity) {
        this.memberId = memberId;
        this.productId = productId;
        this.source = source;
        this.autoCartRequestId = autoCartRequestId;
        this.quantity = quantity;
    }
}
