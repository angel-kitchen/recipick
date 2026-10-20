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
import jakarta.persistence.Index;
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
@Table(
    name = "product",
    indexes = @Index(
        name = "idx_product_mart_id_ingredient_id",
        columnList = "mart_id, ingredient_id"
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mart_id")
    private Mart mart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private Long ingredientId;

    @Column(length = 50)
    private String name;

    @Column(length = 50)
    private String brand;

    @Column(columnDefinition = "text")
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal saleUnitAmount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(10)")
    private SaleUnit saleUnit;

    private String imageUrl;

    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private ProductStatus status;

    private int totalQuantity;

    private boolean deleted;

    @Builder
    private Product(

            Mart mart,
            Category category,
            Long ingredientId,
            String name,
            String brand,
            String description,
            BigDecimal saleUnitAmount,
            SaleUnit saleUnit,
            String imageUrl,
            Long price,
            ProductStatus status,
            int totalQuantity,
            boolean deleted
    ) {
        this.mart = mart;
        this.category = category;
        this.ingredientId = ingredientId;
        this.name = name;
        this.brand = brand;
        this.description = description;
        this.saleUnitAmount = saleUnitAmount;
        this.saleUnit = saleUnit;
        this.imageUrl = imageUrl;
        this.price = price;
        this.status = ProductStatus.SELLING;
        this.totalQuantity = totalQuantity;
        this.deleted = deleted;
    }
}
