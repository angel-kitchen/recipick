package com.recipick.domain.recipe.dto;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.recipe.entity.UnavailableReason;

// 냉장고 파먹기에서 부족한 재료를 이용 마트에서 살 수 있는지. 판매 상품이 없으면 product가 null이다
public record IngredientPrice(
    Long ingredientId,
    ProductSummary product,
    UnavailableReason unavailableReason
) {
}
