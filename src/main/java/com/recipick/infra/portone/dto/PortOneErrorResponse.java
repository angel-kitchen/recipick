package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// PortOne 오류 응답 본문. type(예: PAYMENT_ALREADY_CANCELLED)으로 오류 종류를 구분한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneErrorResponse(
    String type,
    String message
) {
}
