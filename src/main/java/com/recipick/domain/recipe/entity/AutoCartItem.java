package com.recipick.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

    @Column(precision = 10, scale = 2)
    private BigDecimal requiredAmount;

    private int purchaseQuantity;

    private Long price;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String unavailableReason;

    @Builder
    private AutoCartItem(
            AutoCartRequest autoCartRequest,
            Ingredient ingredient,
            Long productId,
            BigDecimal requiredAmount,
            int purchaseQuantity,
            Long price,
            String unavailableReason
    ) {
        this.autoCartRequest = autoCartRequest;
        this.ingredient = ingredient;
        this.productId = productId;
        this.requiredAmount = requiredAmount;
        this.purchaseQuantity = purchaseQuantity;
        this.price = price;
        this.unavailableReason = unavailableReason;
    }
}
