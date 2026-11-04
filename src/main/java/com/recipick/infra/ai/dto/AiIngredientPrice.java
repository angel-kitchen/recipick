package com.recipick.infra.ai.dto;

// 가격·재고 조회 Tool 결과. unavailableReason이 null이면 바로 살 수 있고, 상품이 없으면 상품 필드가 비어 있다
public record AiIngredientPrice(
    Long ingredientId,
    String productName,
    Long price,
    String saleUnit,
    String unavailableReason
) {
}
