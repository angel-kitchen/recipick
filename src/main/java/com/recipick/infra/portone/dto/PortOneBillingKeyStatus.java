package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

// PortOne V2 빌링키 상태.
public enum PortOneBillingKeyStatus {
    ISSUED,
    DELETED,
    @JsonEnumDefaultValue
    UNKNOWN
}
