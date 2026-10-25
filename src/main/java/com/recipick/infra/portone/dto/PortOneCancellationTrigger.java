package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

// 취소 요청 경로. 레시픽 서버가 요청한 취소는 API다
public enum PortOneCancellationTrigger {
    CONSOLE,
    API,
    PORTONE_ADMIN,
    CHARGERBACK,
    @JsonEnumDefaultValue
    UNKNOWN
}
