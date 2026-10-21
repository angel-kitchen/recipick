package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PortOne V2 결제 상태. 프로젝트 PaymentStatus와 섞지 않는다.
 * PortOne은 CANCELLED(L 둘) 철자를 쓰므로 JSON 값만 매핑하고 상수명은 팀 철자(CANCELED)를 따른다.
 */
public enum PortOnePaymentStatus {
    READY,
    PENDING,
    VIRTUAL_ACCOUNT_ISSUED,
    PAID,
    FAILED,
    @JsonProperty("PARTIAL_CANCELLED")
    PARTIAL_CANCELED,
    @JsonProperty("CANCELLED")
    CANCELED,
    @JsonEnumDefaultValue
    UNKNOWN
}
