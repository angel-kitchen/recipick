package com.recipick.domain.recipe.service;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.recipe.entity.UnavailableReason;

import java.math.BigDecimal;
import java.util.List;

public record AutoCartLine(
        RequiredIngredient ingredient,
        Option selected,
        List<Option> candidates
) {

    public boolean purchasable() {
        return selected != null && selected.unavailableReason() == null;
    }

    public UnavailableReason unavailableReason() {
        return selected == null ? UnavailableReason.NOT_SOLD_IN_MART : selected.unavailableReason();
    }

    public int purchaseQuantity() {
        return purchasable() ? selected.purchaseQuantity() : 0;
    }

    public long linePrice() {
        return purchasable() ? selected.linePrice() : 0L;
    }

    public record Option(
            ProductSummary product,
            int purchaseQuantity,
            long linePrice,
            BigDecimal expectedRemainingAmount,
            UnavailableReason unavailableReason
    ){

    }
}
