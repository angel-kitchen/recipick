package com.recipick.domain.recipe.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "auto_cart_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auto_cart_item_auto_cart_request_id_ingredient_id",
                columnNames = {"auto_cart_request_id", "ingredient_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auto_cart_request_id")
    private AutoCartRequest autoCartRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    private Long productId;

    @Column(length = 100)
    private String productName;

    @Column(precision = 10, scale = 2)
    private BigDecimal saleUnitAmount;

    private Long unitPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal requiredAmount;

    private int purchaseQuantity;

    private Long linePrice;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private UnavailableReason unavailableReason;

    @Builder
    private AutoCartItem(
            AutoCartRequest autoCartRequest,
            Ingredient ingredient,
            Long productId,
            String productName,
            BigDecimal saleUnitAmount,
            Long unitPrice,
            BigDecimal requiredAmount,
            int purchaseQuantity,
            Long linePrice,
            UnavailableReason unavailableReason
    ) {
        this.autoCartRequest = autoCartRequest;
        this.ingredient = ingredient;
        this.productId = productId;
        this.productName = productName;
        this.saleUnitAmount = saleUnitAmount;
        this.unitPrice = unitPrice;
        this.requiredAmount = requiredAmount;
        this.purchaseQuantity = purchaseQuantity;
        this.linePrice = linePrice;
        this.unavailableReason = unavailableReason;
    }

    public boolean isPurchasable() {
        return unavailableReason == null;
    }

    public BigDecimal calculateExpectedRemainingAmount() {
        if (!isPurchasable()) {
            return null;
        }
        return saleUnitAmount.multiply(BigDecimal.valueOf(purchaseQuantity)).subtract(requiredAmount);
    }
}
