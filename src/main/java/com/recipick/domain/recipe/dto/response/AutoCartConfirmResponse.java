package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.AutoCartStatus;
import com.recipick.domain.recipe.entity.UnavailableReason;
import com.recipick.domain.recipe.service.AutoCartLine;

import java.util.List;

public record AutoCartConfirmResponse(
    Long autoCartRequestId,
    AutoCartStatus status,
    List<AddedItem> addedItems,
    List<ExcludedItem> excludedItems,
    long totalAddedPrice,
    boolean priceChanged
) {

    public record AddedItem(Long productId, String name, int addedQuantity, int cartQuantity, long linePrice) {
    }

    public record ExcludedItem(Long ingredientId, String ingredientName, UnavailableReason reason) {

        public static ExcludedItem from(AutoCartLine line) {
            return new ExcludedItem(line.ingredient().ingredientId(), line.ingredient().name(),
                line.unavailableReason());
        }
    }
}
