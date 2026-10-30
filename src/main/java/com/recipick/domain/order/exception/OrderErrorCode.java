package com.recipick.domain.order.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    // 장바구니
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART-001", "장바구니 상품을 찾을 수 없습니다."),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "CART-002", "장바구니가 비어 있습니다."),
    INVALID_CART_QUANTITY(HttpStatus.BAD_REQUEST, "CART-003", "상품 수량이 올바르지 않습니다."),
    CART_ITEM_NOT_PURCHASABLE(HttpStatus.CONFLICT, "CART-004", "현재 장바구니에 담거나 수량을 변경할 수 없는 상품입니다."),
    CART_INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "CART-005", "요청한 수량보다 상품 재고가 부족합니다."),
    CART_HAS_PREVIOUS_MART_ITEMS(HttpStatus.CONFLICT, "CART-006", "장바구니에 이전 마트 상품이 있습니다. 삭제한 뒤 다시 담아 주세요."),
    CART_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "CART-007", "다른 요청과 동시에 처리되어 장바구니에 담지 못했습니다. 장바구니를 확인해 주세요."),

    // 주문 생성
    ORDER_ITEMS_REQUIRED(HttpStatus.BAD_REQUEST, "ORDER-001", "주문할 상품을 선택해 주세요."),
    DUPLICATE_ORDER_ITEM(HttpStatus.BAD_REQUEST, "ORDER-002", "주문할 상품이 중복되었습니다."),
    PENDING_ORDER_EXISTS(HttpStatus.CONFLICT, "ORDER-003", "결제 대기 중인 주문이 있습니다. 기존 주문을 확인해 주세요."),
    MART_NOT_SELECTED(HttpStatus.CONFLICT, "ORDER-004", "이용 마트가 지정되지 않았습니다."),
    MART_NOT_OPEN(HttpStatus.CONFLICT, "ORDER-005", "현재 이용 마트에서 주문할 수 없습니다."),
    INVALID_DELIVERY_INFORMATION(HttpStatus.BAD_REQUEST, "ORDER-006", "배송지 정보를 확인해 주세요."),
    DELIVERY_REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-007", "배송지 지역을 찾을 수 없습니다."),
    OUTSIDE_DELIVERY_AREA(HttpStatus.BAD_REQUEST, "ORDER-008", "배송 가능한 지역이 아닙니다."),
    NO_PURCHASABLE_ORDER_ITEMS(HttpStatus.CONFLICT, "ORDER-009", "주문 가능한 상품이 없습니다."),
    ORDER_INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "ORDER-010", "상품 재고가 부족하여 주문을 생성할 수 없습니다."),
    ORDER_AMOUNT_CHANGED(HttpStatus.CONFLICT, "ORDER-011", "주문 금액이 변경되었습니다. 다시 확인해 주세요."),

    // 주문 조회·상태 변경
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-012", "주문을 찾을 수 없습니다."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "ORDER-013", "현재 취소할 수 없는 주문입니다."),
    ORDER_NOT_ACCEPTABLE(HttpStatus.CONFLICT, "ORDER-014", "현재 수락할 수 없는 주문입니다."),
    ORDER_NOT_REJECTABLE(HttpStatus.CONFLICT, "ORDER-015", "현재 거절할 수 없는 주문입니다."),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.CONFLICT, "ORDER-016", "허용되지 않는 주문 상태 변경입니다."),
    INVALID_MART_STATUS_TRANSITION(HttpStatus.CONFLICT, "ORDER-017", "허용되지 않는 주문의 마트 처리 상태 변경입니다."),
    ORDER_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "ORDER-018", "동시 처리 충돌로 요청을 완료하지 못했습니다. 주문 상태를 확인한 뒤 다시 시도해 주세요."),

    // 주문 금액
    USED_POINT_EXCEEDS_PRODUCT_AMOUNT(HttpStatus.BAD_REQUEST, "ORDER-019", "사용 포인트는 상품 금액을 초과할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
