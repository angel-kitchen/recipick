package com.recipick.domain.payment.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BillingKeyDeletionErrorCode implements ErrorCode {
    CLEANUP_PENDING(HttpStatus.CONFLICT, "BILLING-030",
        "자동결제 수단을 정리하고 있습니다. 정리가 끝난 뒤 탈퇴를 다시 요청해 주세요."),
    CLEANUP_REVIEW_REQUIRED(HttpStatus.CONFLICT, "BILLING-031",
        "자동결제 수단 정리에 운영 확인이 필요합니다. 고객 지원에 문의해 주세요."),
    CLEANUP_CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "BILLING-032",
        "자동결제 수단 정리 설정을 확인해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
