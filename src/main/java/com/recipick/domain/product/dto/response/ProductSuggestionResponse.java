package com.recipick.domain.product.dto.response;

import com.recipick.domain.product.entity.Product;

public record ProductSuggestionResponse(
    Long productId,
    String name,
    String imageUrl
) {

    public static ProductSuggestionResponse from(Product product) {
        return new ProductSuggestionResponse(product.getId(), product.getName(), product.getImageUrl());
    }
}
