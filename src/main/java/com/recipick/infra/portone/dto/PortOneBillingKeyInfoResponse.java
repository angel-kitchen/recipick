package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

// GET /billing-keys/{billingKey} 응답(BillingKeyInfo) 중 등록 검증에 필요한 필드만 담은 와이어 DTO.
// 필드 이름은 PortOne V2 공식 OpenAPI 스키마 그대로다. methods[].card 아래의 카드 번호·소유자 정보는 읽지 않는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneBillingKeyInfoResponse(
    PortOneBillingKeyStatus status,
    String billingKey,
    String storeId,
    List<Channel> channels,
    Customer customer,
    List<Method> methods,
    String issueId,
    OffsetDateTime issuedAt,
    OffsetDateTime deletedAt
) {
    // SelectedChannel. type은 PortOne enum(LIVE·TEST)이지만 모르는 값도 받기 위해 문자열로 둔다 — 검증기가 거절한다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Channel(
        String type,
        String key
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(
        String id
    ) {
    }

    // BillingKeyPaymentMethod. 카드는 type = "BillingKeyPaymentMethodCard"
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Method(
        String type
    ) {
    }
}
