package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// cancelled = 누적 취소액(PortOne V2 amount.cancelled). 응답에 없으면 null — 0으로 바꾸지 않는다(비교 불가는 비교 불가로 남긴다)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentAmount(
    Long total,
    Long paid,
    Long cancelled
) {

    // 누적 취소액이 필요 없는 결제 확정 경로용(기존 생성자 호환)
    public PortOnePaymentAmount(Long total, Long paid) {
        this(total, paid, null);
    }
}
