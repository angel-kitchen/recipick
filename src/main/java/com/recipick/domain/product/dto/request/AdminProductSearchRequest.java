package com.recipick.domain.product.dto.request;

import com.recipick.domain.product.entity.ProductStatus;

// 관리자 상품 목록의 쿼리 파라미터. 조회 마트는 Service가 관리자 권한으로 확정한 뒤 검색 조건으로 바꾼다
public record AdminProductSearchRequest(
    Long martId,
    String keyword,
    Long categoryId,
    Long ingredientId,
    ProductStatus status
) {
}
