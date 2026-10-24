package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

// PortOne 취소 내역 상태. PortOne이 값을 추가해도 응답 해석이 깨지지 않게 UNKNOWN으로 받는다.
public enum PortOneCancellationStatus {
    SUCCEEDED,
    REQUESTED,
    FAILED,
    @JsonEnumDefaultValue
    UNKNOWN
}
