package com.recipick.domain.member.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AddressErrorCode implements ErrorCode {

    ADDRESS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "ADDRESS-002", "배송지는 최대 5개까지 저장할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
