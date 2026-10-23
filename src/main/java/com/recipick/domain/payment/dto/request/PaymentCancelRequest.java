package com.recipick.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 취소 사유는 ORDER.cancel_reason_detail(500자)에 그대로 저장된다
public record PaymentCancelRequest(
    @NotBlank @Size(max = 500) String reason
) {
}
