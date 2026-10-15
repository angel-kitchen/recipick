package com.recipick.domain.product.entity;

import com.recipick.global.common.SaleUnit;
import com.recipick.global.entity.BaseTimeEntity;
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
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private Long ingredientId;

    @Column(length = 50)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal saleUnitAmount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(10)")
    private SaleUnit saleUnit;

    private String imageUrl;

    private boolean deleted;

    @Builder
    private Product(
            Category category,
            Long ingredientId,
            String name,
            String description,
            BigDecimal saleUnitAmount,
            SaleUnit saleUnit,
            String imageUrl
    ) {
        this.category = category;
        this.ingredientId = ingredientId;
        this.name = name;
        this.description = description;
        this.saleUnitAmount = saleUnitAmount;
        this.saleUnit = saleUnit;
        this.imageUrl = imageUrl;
    }
}
