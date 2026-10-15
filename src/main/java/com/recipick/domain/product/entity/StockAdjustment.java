package com.recipick.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "stock_adjustment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mart_id")
    private Mart mart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String type;

    private int quantity;

    private int beforeQuantity;

    @Column(length = 500)
    private String reason;

    private Long adminId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private StockAdjustment(
            Mart mart,
            Product product,
            String type,
            int quantity,
            int beforeQuantity,
            String reason,
            Long adminId
    ) {
        this.mart = mart;
        this.product = product;
        this.type = type;
        this.quantity = quantity;
        this.beforeQuantity = beforeQuantity;
        this.reason = reason;
        this.adminId = adminId;
    }
}
