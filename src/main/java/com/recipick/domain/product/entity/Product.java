package com.recipick.domain.product.entity;

import com.recipick.domain.product.dto.ProductStatusResponse;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
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
    indexes = {
        @Index(
            name = "idx_product_mart_id_ingredient_id",
            columnList = "mart_id, ingredient_id"
        ),
        @Index(
            name = "idx_product_mart_category_price",
            columnList = "mart_id, category_id, deleted, price, id"
        ),
        @Index(
            name = "idx_product_mart_created",
            columnList = "mart_id, deleted, created_at, id"
        )
    }
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

    public void decreaseStock(int quantity) {
        validatePositive(quantity);
        if (totalQuantity < quantity) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK);
        }
        this.totalQuantity -= quantity;
    }

    public void changeStatus(ProductStatus status) {
        if (status == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.status = status;
    }

    public void updateInfo(
        Category category,
        Long ingredientId,
        String name,
        String brand,
        String description,
        BigDecimal saleUnitAmount,
        SaleUnit saleUnit,
        String imageUrl
    ){
            this.category = category;
            this.ingredientId = ingredientId;
            this.name = name;
            this.brand = brand;
            this.description = description;
            this.saleUnitAmount = saleUnitAmount;
            this.saleUnit = saleUnit;
            this.imageUrl = imageUrl;

    }

    public void changePrice(Long price){
        if(price == null){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if(price < 0){
            throw new BusinessException(ProductErrorCode.INVALID_PRICE);
        }
        this.price = price;
    }

    public void delete(){
        this.deleted = true;
    }

    public void increaseStock(int quantity) {
        validatePositive(quantity);
            if(quantity > Integer.MAX_VALUE - totalQuantity){
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        this.totalQuantity += quantity;
    }

    private void validatePositive(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INVALID_ADJUST_QUANTITY);
        }
    }
}
