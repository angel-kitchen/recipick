package com.recipick.domain.product.entity;

import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "stock_adjustment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private StockAdjustmentType type;

    private int quantity;

    private int beforeQuantity;

    @Column(length = 500)
    private String reason;

    private Long adminId;

    @Builder
    private StockAdjustment(
            Product product,
            int quantity,
            int beforeQuantity,
            String reason,
            Long adminId
    ) {
        this.product = product;
        this.quantity = quantity;
        this.beforeQuantity = beforeQuantity;
        this.reason = reason;
        this.adminId = adminId;
    }
}
