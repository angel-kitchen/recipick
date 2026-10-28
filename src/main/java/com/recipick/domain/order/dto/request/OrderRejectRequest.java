package com.recipick.domain.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 거절 사유는 ORDER.cancel_reason_detail(500자)과 환불 사유에 그대로 저장된다
public record OrderRejectRequest(@NotBlank @Size(max = 500) String reason) {
}
