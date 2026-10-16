package com.recipick.domain.payment.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_NOT_PAID(HttpStatus.BAD_REQUEST, "PAYMENT-002", "결제가 아직 완료되지 않았습니다."),
    PAYMENT_FAILED(HttpStatus.CONFLICT, "PAYMENT-003", "결제가 실패하여 종료되었습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT-004", "결제 금액이 일치하지 않습니다."),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT, "PAYMENT-005", "결제 가능 시간이 만료되었습니다."),
    PAYMENT_ALREADY_CLOSED(HttpStatus.CONFLICT, "PAYMENT-006", "이미 종료된 결제입니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "PAYMENT-007", "현재 결제 상태에서는 요청을 처리할 수 없습니다."),
    ORDER_ALREADY_PREPARING(HttpStatus.CONFLICT, "PAYMENT-008", "배송 준비가 시작되어 결제를 취소할 수 없습니다."),
    PAYMENT_CANCEL_REJECTED(HttpStatus.CONFLICT, "PAYMENT-009", "결제 취소가 거절되었습니다."),
    PORTONE_CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT-010", "결제 공개 설정을 불러올 수 없습니다."),
    INVALID_BILLING_KEY(HttpStatus.BAD_REQUEST, "BILLING-001", "유효하지 않은 자동결제 수단입니다."),
    BILLING_KEY_ALREADY_USED(HttpStatus.CONFLICT, "BILLING-002", "이미 사용 중인 빌링키입니다."),
    MEMBERSHIP_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "BILLING-003", "멤버십 결제 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
