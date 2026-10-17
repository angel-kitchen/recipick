package com.recipick.domain.product.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT-001", "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_SELLING(HttpStatus.CONFLICT, "PRODUCT-003", "판매 중인 상품이 아닙니다."),
    UNIT_MISMATCH(HttpStatus.BAD_REQUEST, "PRODUCT-004", "재료 단위와 판매 단위가 맞지 않습니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "PRODUCT-005", "가격은 0 이상이어야 합니다."),
    INVALID_SALE_UNIT(HttpStatus.BAD_REQUEST, "PRODUCT-006", "판매 단위가 올바르지 않습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY-001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "CATEGORY-002", "이미 있는 카테고리입니다."),
    CATEGORY_HAS_PRODUCTS(HttpStatus.CONFLICT, "CATEGORY-003", "상품이 있는 카테고리는 삭제할 수 없습니다."),
    CATEGORY_ALREADY_DELETED(HttpStatus.CONFLICT, "CATEGORY-004", "이미 삭제된 카테고리입니다."),
    MART_NOT_FOUND(HttpStatus.NOT_FOUND, "MART-001", "마트를 찾을 수 없습니다."),
    MART_NOT_OPEN(HttpStatus.CONFLICT, "MART-002", "운영 중인 마트가 아닙니다."),
    MART_REGION_DUPLICATED(HttpStatus.CONFLICT, "MART-003", "이 지역에는 이미 마트가 있습니다."),
    INVALID_MART_AREA(HttpStatus.BAD_REQUEST, "MART-004", "마트 좌표 또는 서비스 반경이 올바르지 않습니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND,"REGION-001", "지역을 찾을 수 없습니다." ),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "STOCK-001", "재고가 부족합니다."),
    STOCK_ADJUST_EXCEEDS_AVAILABLE(HttpStatus.BAD_REQUEST, "STOCK-002", "감소 수량이 가용 재고를 넘을 수 없습니다."),
    INVALID_ADJUST_QUANTITY(HttpStatus.BAD_REQUEST, "STOCK-003", "조정 수량은 1 이상이어야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

}
