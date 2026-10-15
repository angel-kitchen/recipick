package com.recipick.domain.order.entity;

import com.recipick.global.common.SaleUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    private Long productId;

    @Column(length = 100)
    private String productName;

    private Long unitPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal saleUnitAmount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(10)")
    private SaleUnit saleUnit;

    private Long lineAmount;

    private int quantity;

    @Builder
    private OrderItem(
            Order order,
            Long productId,
            String productName,
            Long unitPrice,
            BigDecimal saleUnitAmount,
            SaleUnit saleUnit,
            Long lineAmount,
            int quantity
    ) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.saleUnitAmount = saleUnitAmount;
        this.saleUnit = saleUnit;
        this.lineAmount = lineAmount;
        this.quantity = quantity;
    }
}
